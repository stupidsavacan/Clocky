"""Independent OS processes, shared temporary registry; never executes ADB."""
import argparse
import contextlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock
import uuid

from clockydev.broker import Broker, FileLock, atomic_json, command_access
from clockydev.cli import Ctx, main
from clockydev.errors import CdevError
from clockydev import managed_process
from clockydev.verification import Transaction, validate_bundle
from tests.helpers import FIX

DEVICE = Path(__file__).resolve().parents[1]

WORKER = r'''
import json, os, sys, time
sys.path.insert(0, sys.argv[1])
from clockydev.broker import Broker
b = Broker(sys.argv[2])
agent, session, token = sys.argv[3:6]
while not os.path.exists(sys.argv[6]): time.sleep(.01)
b.request("PHONE", agent, session, token, os.getcwd())
print(json.dumps({"event":"queued", "agent":agent}), flush=True)
lease = None
while not lease:
    lease = b.claim("PHONE", session, token)
    if not lease and b.status("PHONE")["state"] == "DIRTY":
        print(json.dumps({"event":"dirty"}), flush=True)
        sys.exit(6)
    time.sleep(.01)
print(json.dumps({"event":"acquired", "agent":agent}), flush=True)
sys.stdin.readline()
lease.begin_release()
lease.complete({"clean":True, "session":session, "test_exit_code":0})
print(json.dumps({"event":"released", "agent":agent}), flush=True)
'''


class BrokerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.b = Broker(self.root / "registry")
        self.b.register("PHONE", ["USB", "WIFI"])
        self.processes = []
        self.addCleanup(self.stop_workers)

    def stop_workers(self):
        for p in self.processes:
            if p.poll() is None:
                p.kill()
            p.wait(timeout=10)
            for f in (p.stdin, p.stdout, p.stderr):
                if f:
                    f.close()

    def bootstrap(self):
        lease = self.b.claim("PHONE", "bootstrap", "bootstrap-token", recovery=True,
                             agent="operator", worktree=self.root)
        self.assertIsNotNone(lease)
        lease.begin_release()
        lease.complete({"clean": True, "session": "bootstrap", "test_exit_code": 0})

    def acquire(self, name="A"):
        self.b.request("PHONE", name, name, "token-" + name, self.root)
        lease = self.b.claim("PHONE", name, "token-" + name)
        self.assertIsNotNone(lease)
        self.addCleanup(lease.life.close)
        return lease

    def spawn(self, name, gate):
        worktree = self.root / ("worktree-" + name)
        worktree.mkdir()
        p = subprocess.Popen([sys.executable, "-u", "-c", WORKER, str(DEVICE), str(self.b.root),
                              name, name, "token-" + name, str(gate)],
                             cwd=worktree, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, text=True)
        self.processes.append(p)
        return p

    def wait_state(self, predicate):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            state = self.b.status("PHONE")
            if predicate(state):
                return state
            time.sleep(.01)
        self.fail("Timed out; state=%s" % self.b.status("PHONE"))

    def release_worker(self, p):
        p.stdin.write("release\n")
        p.stdin.flush()
        p.wait(timeout=10)
        self.assertEqual(p.returncode, 0, p.stderr.read())

    def test_simultaneous_ab_single_owner_and_handoff(self):
        self.bootstrap()
        gate = self.root / "go"
        a, b = self.spawn("A", gate), self.spawn("B", gate)
        gate.touch()
        state = self.wait_state(lambda s: s["owner"] and len(s["queue"]) == 1)
        owner = state["owner"]["agent"]
        follower = state["queue"][0]["agent"]
        self.assertNotEqual(owner, follower)
        time.sleep(.15)
        self.assertEqual(self.b.status("PHONE")["owner"]["agent"], owner)
        self.release_worker({"A": a, "B": b}[owner])
        self.wait_state(lambda s: s["owner"] and s["owner"]["agent"] == follower)
        self.release_worker({"A": a, "B": b}[follower])
        self.assertEqual(self.b.status("PHONE")["state"], "FREE")

    def test_fifo_three_waiters_across_worktrees(self):
        self.bootstrap()
        holder = self.acquire()
        ps = []
        for name in ("B", "C", "D"):
            gate = self.root / name
            p = self.spawn(name, gate)
            gate.touch()
            ps.append(p)
            self.wait_state(lambda s: any(x["agent"] == name for x in s["queue"]))
        self.assertEqual([x["agent"] for x in self.b.status("PHONE")["queue"]], ["B", "C", "D"])
        holder.begin_release()
        holder.complete({"clean": True})
        for name, p in zip(("B", "C", "D"), ps):
            self.wait_state(lambda s: s["owner"] and s["owner"]["agent"] == name)
            self.release_worker(p)
        self.assertEqual(self.b.status("PHONE")["state"], "FREE")

    def test_duplicate_request_and_authenticated_cancel(self):
        self.bootstrap()
        for _ in range(2):
            self.b.request("PHONE", "A", "session", "token", self.root)
        self.assertEqual(len(self.b.status("PHONE")["queue"]), 1)
        with self.assertRaises(CdevError):
            self.b.request("PHONE", "B", "session", "token", self.root)
        with self.assertRaises(CdevError):
            self.b.cancel("PHONE", "session", "wrong")
        self.b.cancel("PHONE", "session", "token")
        self.assertEqual(self.b.status("PHONE")["queue"], [])

    def test_bad_token_session_and_double_release(self):
        self.bootstrap()
        lease = self.acquire()
        for session, token in (("A", "wrong"), ("B", "token-A")):
            with self.assertRaises(CdevError):
                with self.b.operation("PHONE", session, token):
                    self.fail("Unauthorized operation")
        self.b.request_release("PHONE", "A", "token-A")
        with self.assertRaises(CdevError):
            self.b.request_release("PHONE", "A", "token-A")
        lease.begin_release()
        with self.assertRaises(CdevError):
            with self.b.operation("PHONE", "A", "token-A"):
                pass
        lease.complete({"clean": True})
        with self.assertRaises(CdevError):
            lease.complete({"clean": True})

    def test_owner_process_crash_persists_dirty_and_recovery_reuses_baseline(self):
        self.bootstrap()
        gate = self.root / "go"
        a = self.spawn("A", gate)
        gate.touch()
        self.wait_state(lambda s: s["owner"] is not None)
        self.b.request("PHONE", "B", "B", "token-B", self.root)
        original = {"path": "original", "build_dir": str(self.root / "old-session")}
        self.b.update("PHONE", "A", "token-A", baseline=original)
        a.kill()
        a.wait(timeout=10)
        self.assertEqual(self.b.status("PHONE")["state"], "DIRTY")
        self.assertTrue(self.b.status("PHONE")["recovery_required"])
        self.assertIsNone(self.b.claim("PHONE", "B", "token-B"))
        recovery = self.b.claim("PHONE", "repair", "repair-token", recovery=True,
                                agent="operator", worktree=self.root)
        self.assertEqual(str(recovery.build_dir), original["build_dir"])
        recovery.begin_release()
        recovery.complete({"clean": True})
        b = self.b.claim("PHONE", "B", "token-B")
        self.assertIsNotNone(b)
        b.begin_release()
        b.complete({"clean": True})

    def test_live_owner_never_stolen_even_for_recovery(self):
        self.bootstrap()
        lease = self.acquire()
        self.assertIsNone(self.b.claim("PHONE", "repair", "repair-token", recovery=True,
                                      agent="operator", worktree=self.root))
        self.assertEqual(self.b.status("PHONE")["owner"]["session"], lease.session)

    def test_restore_failure_blocks_next_acquisition(self):
        self.bootstrap()
        a = self.acquire()
        self.b.request("PHONE", "B", "B", "token-B", self.root)
        a.begin_release()
        a.complete({"clean": False, "errors": ["restore failed"]})
        self.assertIsNone(self.b.claim("PHONE", "B", "token-B"))
        self.assertEqual(self.b.status("PHONE")["state"], "DIRTY")

    def test_corrupt_missing_registry_and_path_sessions_fail_closed(self):
        self.b.path.write_text("{broken", encoding="utf-8")
        with self.assertRaises(CdevError):
            self.b.status("PHONE")
        self.b.path.unlink()
        with self.assertRaises(CdevError):
            self.b.status("PHONE")

    def test_initial_registration_requires_recovery(self):
        self.b.request("PHONE", "A", "A", "token-A", self.root)
        self.assertIsNone(self.b.claim("PHONE", "A", "token-A"))
        with self.assertRaises(CdevError):
            self.b.request("PHONE", "A", "../other", "token", self.root)

    def test_retired_credentials_never_reactivate(self):
        self.bootstrap()
        lease = self.acquire()
        lease.begin_release()
        lease.complete({"clean": True})
        with self.assertRaises(CdevError):
            self.b.request("PHONE", "A", "A", "token-A", self.root)

    def test_interactive_test_token_refused_until_baseline_ready(self):
        self.bootstrap()
        self.b.request("PHONE", "A", "A", "token-A", self.root)
        lease = self.b.claim("PHONE", "A", "token-A", preparing=True)
        self.addCleanup(lease.life.close)
        with self.assertRaises(CdevError):
            with self.b.operation("PHONE", "A", "token-A"):
                pass
        with self.b.operation("PHONE", "A", lease.setup_token):
            pass
        lease.ready()
        with self.b.operation("PHONE", "A", "token-A"):
            pass

    def test_cli_request_cancel_status_and_wait_timeout_do_not_touch_adb(self):
        self.bootstrap()
        holder = self.acquire()
        common = ["--root", str(self.b.root), "--identity", "PHONE", "--json"]
        file = str(self.root / "credentials.json")
        env = {"CLOCKY_BROKER_ROOT": str(self.b.root)}
        commands = [(["lease", "request", *common, "--agent", "B", "--serial", "USB",
                      "--credential-file", file], 0),
                    (["lease", "status", *common], 0),
                    (["lease", "run", *common, "--agent", "B", "--serial", "USB",
                      "--request-file", file, "--wait-timeout", "0", "--", sys.executable, "-c", "pass"], 6)]
        with mock.patch("clockydev.cli.Ctx.raw_adb", side_effect=AssertionError("No ADB while queued")):
            for args, expected in commands:
                with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(main(args, env=env), expected)
        self.assertEqual(self.b.status("PHONE")["owner"]["session"], holder.session)
        self.assertEqual(self.b.status("PHONE")["queue"], [])

    def test_operation_lock_serializes_release_in_other_process(self):
        self.bootstrap()
        gate = self.root / "go"
        a = self.spawn("A", gate)
        gate.touch()
        self.wait_state(lambda s: s["owner"] is not None)
        with self.b.operation("PHONE", "A", "token-A"):
            a.stdin.write("release\n")
            a.stdin.flush()
            time.sleep(.2)
            self.assertEqual(self.b.status("PHONE")["state"], "ACTIVE")
            self.assertIsNone(a.poll())
        a.wait(timeout=10)
        self.assertEqual(self.b.status("PHONE")["state"], "FREE")

    def env(self, token="token-A", identity="PHONE", serial="USB"):
        return dict(os.environ, CLOCKY_BROKER_ROOT=str(self.b.root), CLOCKY_LEASE_TOKEN=token,
                    CLOCKY_LEASE_SESSION="A", CLOCKY_DEVICE_IDENTITY=identity, CLOCKY_SERIAL=serial)

    def test_cdev_refuses_all_device_commands_before_any_adb(self):
        self.bootstrap()
        self.acquire()
        commands = [["status"], ["devices"], ["prepare"], ["finish"], ["restore"], ["cleanup"],
                    ["inspect"], ["widget"], ["logs"], ["install"], ["launch", "app"],
                    ["tap", "x"], ["key", "home"], ["collect", "x"], ["project"],
                    ["inventory"], ["rotate", "landscape"], ["proc"], ["mark", "x"],
                    ["long-press", "x"], ["drag", "x", "--to-xy", "1", "2"],
                    ["swipe", "1", "2", "3", "4"], ["scroll", "up"], ["wait", "x"],
                    ["adb", "--", "shell", "getprop"]]
        with mock.patch("clockydev.cli.Ctx.raw_adb", side_effect=AssertionError("ADB must not be called")):
            for args in commands:
                with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(main(args, env=self.env(token="wrong")), 6, args)

    def test_cdev_identity_mismatch_and_cross_worktree_shared_evidence(self):
        self.bootstrap()
        lease = self.acquire()
        for identity, serial in (("OTHER", "USB"), ("PHONE", "UNREGISTERED")):
            ctx = Ctx(argparse.Namespace(command="status"), env=self.env(identity=identity, serial=serial))
            with self.assertRaises(CdevError):
                with command_access(ctx):
                    pass
        for tree in ("treeA", "treeB"):
            ctx = Ctx(argparse.Namespace(command="status"), env=self.env())
            ctx.root = str(self.root / tree)
            with command_access(ctx):
                self.assertEqual(ctx.build_dir, str(lease.build_dir))
                from clockydev import session
                raw = mock.Mock()
                raw.bind.return_value.shell.return_value.text = "OTHER"
                ctx.raw_adb = lambda: raw
                with self.assertRaises(CdevError) as cm:
                    session.select_for_ctx(ctx)
                self.assertEqual(cm.exception.code, "BROKER_IDENTITY_MISMATCH")
                self.assertEqual(raw.run.call_count, 0)  # no devices discovery

    def test_unconfigured_legacy_cdev_path(self):
        env = {"CLOCKY_BROKER_ROOT": str(self.root / "unconfigured")}
        ctx = Ctx(argparse.Namespace(command="status"), env=env)
        old = ctx.build_dir
        with command_access(ctx):
            self.assertEqual(ctx.build_dir, old)
            self.assertFalse(hasattr(ctx, "broker"))


class FakeRunner:
    """Exercise real transaction decisions with fixture bundles, not a phone."""
    def __init__(self, root, failures=(), drift=False):
        self.root, self.failures, self.drift = Path(root), set(failures), drift
        self.calls = []
        self.baseline = self.bundle("base")
        self.restored = self.bundle("restored")
        if drift:
            widget = json.loads((self.restored / "widget.json").read_text())
            widget["hosts_on_screen"][0]["bounds"][0] += 5
            atomic_json(self.restored / "widget.json", widget)

    def bundle(self, name):
        path = self.root / name
        shutil.copytree(Path(FIX) / "bundles/moto_baseline", path)
        meta = json.loads((path / "meta.json").read_text())
        meta["device"]["identity"] = "PHONE"
        meta["errors"] = []
        atomic_json(path / "meta.json", meta)
        widget = json.loads((path / "widget.json").read_text())
        widget.update(prefs_status="ok", hosts_on_screen=[{"bounds": [1, 2, 3, 4]}])
        atomic_json(path / "widget.json", widget)
        atomic_json(path / "status.json", {"launcher": "launcher/.Home", "focus": "launcher/.Home",
                                           "settings": {"user_rotation": "0"}, "rotation": 0})
        (path / "screen.png").write_bytes(b"\x89PNGfixture")
        (path / "ui.xml").write_text("<hierarchy><node package='com.stupidsavacan.clocky' resource-id='root' class='View' bounds='[0,0][720,1600]'/></hierarchy>")
        (path / "dumpsys_appwidget.txt").write_text("Providers:\nWidgets:\n")
        return path

    def __call__(self, argv, **kwargs):
        cmd = argv[2] if len(argv) > 2 and argv[1].endswith("cdev.py") else "hook"
        self.calls.append(cmd)
        if cmd in self.failures:
            return subprocess.CompletedProcess(argv, 1, b'{"ok":false,"error":"injected"}', b"")
        result = {}
        if cmd == "inventory":
            result = {"sha256": "a" * 64, "source_commit": None}
        if cmd == "collect":
            result = {"dir": str(self.baseline if argv[3] == "broker-baseline" else self.restored)}
        return subprocess.CompletedProcess(argv, 0, json.dumps({"ok": True, "result": result}).encode(), b"")


class TransactionTest(unittest.TestCase):
    setUp = BrokerTest.setUp
    stop_workers = BrokerTest.stop_workers
    bootstrap = BrokerTest.bootstrap
    acquire = BrokerTest.acquire
    def transaction(self, failures=(), drift=False, hook=None):
        self.bootstrap()
        lease = self.acquire()
        runner = FakeRunner(self.root / "evidence", failures, drift)
        tx = Transaction(lease, "USB", DEVICE.parents[1], restore_script=hook, runner=runner)
        return tx, runner

    def test_clean_transaction_can_release_after_failed_test(self):
        tx, runner = self.transaction()
        tx.start()
        result = tx.finish(7)
        self.assertTrue(result["clean"])
        self.assertEqual(result["test_exit_code"], 7)
        self.assertEqual(runner.calls, ["inventory", "prepare", "collect", "restore", "collect", "finish", "inventory"])
        self.assertEqual(self.b.status("PHONE")["state"], "FREE")

    def test_finally_attempts_finish_even_when_restore_hook_fails(self):
        tx, runner = self.transaction(failures=["hook"], hook="repair.py")
        tx.start()
        result = tx.finish(1)
        self.assertFalse(result["clean"])
        self.assertIn("finish", runner.calls)
        self.assertEqual(self.b.status("PHONE")["state"], "DIRTY")

    def test_geometry_drift_blocks_release(self):
        tx, runner = self.transaction(drift=True)
        tx.start()
        self.assertFalse(tx.finish(0)["clean"])
        self.assertEqual(self.b.status("PHONE")["state"], "DIRTY")

    def test_finish_failure_and_pending_restore_block_release(self):
        tx, runner = self.transaction(failures=["finish"])
        tx.start()
        from clockydev.session import save_state
        save_state(str(tx.lease.build_dir), {"saved_settings": {"identity": "PHONE", "items": {"system/user_rotation": "0"}}})
        result = tx.finish(0)
        self.assertFalse(result["clean"])
        self.assertTrue(any(e["step"] == "pending-restore" for e in result["errors"]))

    def test_partial_unavailable_identity_evidence_never_certifies_clean(self):
        tx, runner = self.transaction()
        meta_path = runner.baseline / "meta.json"
        meta = json.loads(meta_path.read_text())
        for changed in ({"errors": [{"part": "widget", "error": "failed"}]},
                        {"device": {"identity": "OTHER"}}):
            atomic_json(meta_path, dict(meta, **changed))
            with self.assertRaises(CdevError):
                validate_bundle(runner.baseline, "PHONE")

    def test_recovery_must_not_bless_failure_before_baseline(self):
        self.bootstrap()
        old = self.acquire()
        old.life.close()  # crash before tx.start/complete baseline
        self.assertEqual(self.b.status("PHONE")["state"], "DIRTY")
        lease = self.b.claim("PHONE", "repair", "new-token", recovery=True,
                             agent="operator", worktree=self.root)
        self.addCleanup(lease.life.close)
        runner = FakeRunner(self.root / "evidence")
        tx = Transaction(lease, "USB", DEVICE.parents[1], runner=runner)
        with self.assertRaises(CdevError):
            tx.start()
        self.assertEqual(runner.calls, [])

    def test_cli_run_failed_test_clean_release_then_timeout_dirty_and_recovery(self):
        self.bootstrap()
        fake = FakeRunner(self.root / "evidence")
        real_tx = Transaction
        def factory(lease, serial, repo, restore_script):
            return real_tx(lease, serial, repo, restore_script, runner=fake)
        common = ["--root", str(self.b.root), "--identity", "PHONE", "--serial", "USB", "--agent", "agent", "--json"]
        cases = [(["lease", "run", *common, "--", sys.executable, "-c", "import sys; sys.exit(7)"], 7, "FREE"),
                 (["lease", "run", *common, "--duration", ".1", "--", sys.executable, "-c", "import time; time.sleep(3)"], 6, "DIRTY"),
                 (["lease", "recover", *common, "--acknowledge-quiescent", "--reason", "offline test repair",
                   "--", sys.executable, "-c", "pass"], 0, "FREE")]
        with mock.patch("clockydev.verification.Transaction", side_effect=factory):
            for args, expected, state in cases:
                output = io.StringIO()
                with contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(main(args), expected)
                self.assertEqual(self.b.status("PHONE")["state"], state)
                self.assertIn("clean", json.loads(output.getvalue())["result"])
        self.assertEqual(self.b.status("PHONE")["last_clean_handoff"]["recovery_reason"], "offline test repair")

    def test_prepare_persists_stay_awake_original_before_failed_write(self):
        from clockydev.cmd_session import cmd_prepare
        from clockydev.session import load_state
        from tests.helpers import FakeAdb
        dev = mock.Mock(identity="PHONE", serial="USB", transport="usb", warnings=[], adb=FakeAdb({"settings get": "0"}))
        ctx = mock.Mock(build_dir=str(self.root / "cdev-state"))
        ctx.device.return_value = dev
        with mock.patch("clockydev.devstate.snapshot", return_value={"wakefulness": "Awake", "keyguard": False}), mock.patch(
                "clockydev.cmd_session.put_setting", side_effect=CdevError(5, "WRITE_FAILED", "injected")):
            with self.assertRaises(CdevError):
                cmd_prepare(ctx, argparse.Namespace(stay_awake=True, home=False))
        self.assertEqual(load_state(ctx.build_dir)["saved_settings"]["items"]["global/stay_on_while_plugged_in"], "0")


class AdbServerTest(unittest.TestCase):
    def test_existing_server_protocol_matches_without_device_selection(self):
        from clockydev.adb import Adb
        chunks = [b"OK", b"AY", b"0004", b"0029"]
        sock = mock.MagicMock()
        sock.__enter__.return_value = sock
        sock.recv.side_effect = chunks
        result = subprocess.CompletedProcess([], 0, b"Android Debug Bridge version 1.0.41\n", b"")
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch("socket.create_connection", return_value=sock), mock.patch(
                "clockydev.managed_process.run", return_value=result) as run:
            Adb("adb.exe", managed=True).check_server()
            run.assert_called_once_with(["adb.exe", "version"], timeout=10)
        sock.sendall.assert_called_once_with(b"000chost:version")

    def test_protocol_mismatch_and_absent_server_fail_before_device_command(self):
        from clockydev.adb import Adb
        sock = mock.MagicMock()
        sock.__enter__.return_value = sock
        sock.recv.side_effect = [b"OKAY", b"0004", b"0028"]
        result = subprocess.CompletedProcess([], 0, b"Android Debug Bridge version 1.0.41\n", b"")
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch("socket.create_connection", return_value=sock), mock.patch(
                "clockydev.managed_process.run", return_value=result) as run:
            with self.assertRaises(CdevError) as cm:
                Adb("adb.exe", "USB", managed=True).run(["shell", "getprop"])
            self.assertEqual(cm.exception.code, "ADB_SERVER_VERSION_MISMATCH")
            self.assertEqual(run.call_count, 1)  # only offline `adb version`
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch("socket.create_connection", side_effect=OSError("absent")), mock.patch(
                "clockydev.managed_process.run", return_value=result):
            with self.assertRaises(CdevError) as cm:
                Adb("adb.exe", managed=True).check_server()
            self.assertEqual(cm.exception.code, "ADB_SERVER_NOT_RUNNING")


class ManagedProcessTest(unittest.TestCase):
    def test_stdout_stderr_exit_code_and_timeout(self):
        r = managed_process.run([sys.executable, "-c", "import sys; print('ok'); print('err', file=sys.stderr); sys.exit(7)"])
        self.assertEqual(r.returncode, 7)
        self.assertIn(b"ok", r.stdout)
        self.assertIn(b"err", r.stderr)
        with self.assertRaises(subprocess.TimeoutExpired):
            managed_process.run([sys.executable, "-c", "import time; time.sleep(10)"], timeout=.1)

    def test_detached_descendant_stops_before_parent_return(self):
        with tempfile.TemporaryDirectory() as root:
            marker = Path(root) / "should-not-exist"
            child = "import time; from pathlib import Path; time.sleep(1); Path(%r).touch()" % str(marker)
            parent = "import subprocess,sys; subprocess.Popen([sys.executable,'-c',%r]); print('spawned')" % child
            r = managed_process.run([sys.executable, "-c", parent], timeout=5)
            self.assertEqual(r.returncode, 0)
            time.sleep(1.2)
            self.assertFalse(marker.exists())
