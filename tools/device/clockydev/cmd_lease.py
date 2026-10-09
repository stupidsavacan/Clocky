"""Broker CLI; finite supervisors own device lifetime locks."""
import argparse
import json
import math
import os
from pathlib import Path
import secrets
import shutil
import sys
import time
import uuid

from .broker import Broker, atomic_json, fail, root_from_env
from .cli import Out, command
from .errors import adb_err
from . import managed_process


def setup(p):
    sub = p.add_subparsers(dest="action", required=True)
    for action in ("register", "status", "request", "cancel", "release", "run", "hold", "recover"):
        s = sub.add_parser(action)
        s.add_argument("--root", help="shared local registry (all worktrees must use the same path)")
        s.add_argument("--json", action="store_true", default=argparse.SUPPRESS)
        s.add_argument("--identity", required=True, help="physical ro.serialno; never a worktree name")
        if action == "register":
            s.add_argument("--transport", action="append", required=True, help="known adb serial alias; repeat for USB/Wi-Fi")
        if action in ("request", "run", "hold", "recover"):
            s.add_argument("--agent", required=True)
            s.add_argument("--serial", required=True)
            s.add_argument("--credential-file", help="local bearer credentials; do not publish or commit")
        if action == "request":
            s.add_argument("--session", default=None, help="stable ID for idempotent request (default random UUID)")
        if action in ("cancel", "release"):
            s.add_argument("--credential-file", required=True)
        if action in ("run", "hold", "recover"):
            s.add_argument("--request-file", help="resume a previously queued request")
            s.add_argument("--wait-timeout", type=float, default=900)
            s.add_argument("--duration", type=float, default=900, help="finite test/interactive unit in seconds")
            s.add_argument("--restore-script", help="Python restoration script; inherits cdev credentials and CLOCKY_BASELINE_DIR")
        if action == "recover":
            s.add_argument("--acknowledge-quiescent", action="store_true", required=True,
                           help="operator has stopped old/legacy external clients; no live owner is stolen")
            s.add_argument("--reason", required=True, help="local recovery audit explanation")
            s.add_argument("--baseline", help="operator-reviewed original evidence bundle, ONLY when original baseline is missing")


def read_credentials(path, broker, identity):
    with open(path, encoding="utf-8") as f:
        c = json.load(f)
    if c["identity"] != identity or Path(c["root"]).resolve() != broker.root:
        fail("Credentials belong to a different identity or registry")
    return c


def credentials(ctx, args, broker):
    if getattr(args, "request_file", None):
        c = read_credentials(args.request_file, broker, args.identity)
        if c["agent"] != args.agent or c["serial"] != args.serial:
            fail("Request agent/serial mismatch")
        return c
    path = getattr(args, "credential_file", None)
    if path and Path(path).exists():
        c = read_credentials(path, broker, args.identity)
        if c["agent"] != args.agent or c["serial"] != args.serial:
            fail("Existing credential file belongs to another agent/serial")
        if getattr(args, "session", None) and args.session != c["session"]:
            fail("Existing credential file has a different session")
        return c
    c = {"root": str(broker.root), "identity": args.identity, "serial": args.serial,
         "agent": args.agent, "session": getattr(args, "session", None) or uuid.uuid4().hex,
         "token": secrets.token_urlsafe(32), "worktree": ctx.root}
    if path:
        atomic_json(Path(path).resolve(), c)
    return c


@command("lease", setup)
def cmd_lease(ctx, args):
    """Shared device registry, FIFO requests, transactional run/hold and explicit recovery."""
    broker = Broker(args.root or root_from_env(ctx.env))
    if args.action == "register":
        broker.register(args.identity, args.transport)
        return Out({"state": "DIRTY", "recovery_required": True}, ["Registered offline; clean bootstrap recovery required."])
    if args.action == "status":
        d = broker.status(args.identity)
        return Out(d, [json.dumps(d, ensure_ascii=True, indent=2)])
    if args.action in ("cancel", "release"):
        c = read_credentials(args.credential_file, broker, args.identity)
        fn = broker.cancel if args.action == "cancel" else broker.request_release
        fn(args.identity, c["session"], c["token"])
        return Out({"requested": args.action}, ["%s requested; release completes only after supervisor restoration." % args.action])
    if broker.resolve_serial(args.serial) != args.identity:
        fail("Device identity and transport alias do not match")
    if args.action in ("request", "hold") and not args.credential_file and not getattr(args, "request_file", None):
        fail("request/hold requires a credential file for cancellation/release")
    c = credentials(ctx, args, broker)
    if args.action == "request":
        broker.request(args.identity, args.agent, c["session"], c["token"], ctx.root)
        return Out({"session": c["session"], "credential_file": str(Path(args.credential_file).resolve())},
                   ["Queued %s; waiting supervisors poll local registry, no GitHub notification needed." % c["session"]])
    if not math.isfinite(args.duration) or not math.isfinite(args.wait_timeout) or args.duration <= 0 or args.wait_timeout < 0:
        fail("Duration must be positive and wait timeout nonnegative")
    if args.action in ("run", "recover") and not args.raw_tail:
        fail("lease run/recover requires a finite command after --")
    if args.action == "hold" and args.raw_tail:
        fail("hold does not accept a test command; use run")
    recovery = args.action == "recover"
    if recovery and args.request_file:
        fail("Recovery uses a fresh session, not --request-file")
    if not recovery:
        broker.request(args.identity, args.agent, c["session"], c["token"], ctx.root)
    lease = None
    deadline = time.monotonic() + args.wait_timeout
    try:
        while lease is None:
            lease = broker.claim(args.identity, c["session"], c["token"], recovery=recovery,
                                 agent=args.agent, worktree=ctx.root, preparing=True)
            if lease:
                break
            if recovery:
                fail("Recovery refused: another supervisor is alive")
            state = broker.status(args.identity)
            if state["state"] == "DIRTY":
                fail("RECOVERY_REQUIRED: " + (state.get("reason") or "dirty device"))
            if not any(x["session"] == c["session"] for x in state["queue"]):
                fail("Request was cancelled")
            if time.monotonic() >= deadline:
                fail("Wait timed out; current owner was not revoked")
            time.sleep(0.2)
    except BaseException:
        if not recovery:
            try:
                broker.cancel(args.identity, c["session"], c["token"])
            except Exception:
                pass
        raise
    from .verification import Transaction
    tx = Transaction(lease, args.serial, ctx.root, args.restore_script)
    if recovery:
        tx.recovery_reason = args.reason
    if recovery and args.baseline:
        try:
            from .verification import validate_bundle
            if tx.baseline:
                fail("Recovery must not replace a saved original baseline")
            path = str(Path(args.baseline).resolve())
            tx.baseline = {"path": path, "build_dir": str(lease.build_dir),
                           "metadata": validate_bundle(path, lease.identity), "operator_reviewed": args.reason}
            tx.env["CLOCKY_BASELINE_DIR"] = path
            broker.update(lease.identity, lease.session, lease.access_token, baseline=tx.baseline)
        except BaseException:
            lease.life.close()
            raise
    test_rc = 1
    try:
        tx.start()
        if args.action == "hold":
            # Avoid printing credentials. A second shell reads the local file.
            sys.stderr.write("Lease ACTIVE session=%s credentials=%s; release or duration expiry triggers restoration.\n" %
                             (lease.session, args.credential_file or args.request_file))
            sys.stderr.flush()
            end = time.monotonic() + args.duration
            while time.monotonic() < end:
                state = broker.status(args.identity)
                if state["owner"]["release_requested"]:
                    break
                time.sleep(0.2)
            test_rc = 0
        else:
            r = managed_process.run(args.raw_tail, env=tx.env, cwd=ctx.root, timeout=args.duration)
            # Evidence remains local. Never embed raw command output in PR-safe reports.
            lease.build_dir.mkdir(parents=True, exist_ok=True)
            (lease.build_dir / "test.stdout").write_bytes(r.stdout)
            (lease.build_dir / "test.stderr").write_bytes(r.stderr)
            test_rc = r.returncode
    except KeyboardInterrupt:
        test_rc = 130
    except Exception as e:
        tx.errors.append({"step": "test/start", "error": str(e)})
    finally:
        try:
            evidence = tx.finish(test_rc)
            if recovery:
                evidence["recovery_reason"] = args.reason
                atomic_json(lease.build_dir / "recovery.json", evidence)
        finally:
            # If cleanup itself crashes, ACTIVE/RELEASING remains an orphan and
            # next status/access persists DIRTY. Never synthesize a clean release.
            lease.life.close()
    return Out({"session": lease.session, "clean": evidence["clean"], "test_exit_code": test_rc,
                "evidence": str(lease.build_dir / "handoff.json"), "installed": evidence["installed"]},
               ["lease %s: %s; test exit=%s" % (lease.session, "clean release" if evidence["clean"] else "DIRTY / RECOVERY_REQUIRED", test_rc)],
               exit_code=(0 if test_rc == 0 else 7) if evidence["clean"] else 6)


def project_setup(p):
    p.add_argument("--record", help="local MP4/MKV recording output")
    p.add_argument("--duration", type=float, default=60, help="maximum GUI lifetime (seconds)")


@command("project", project_setup)
def cmd_project(ctx, args):
    """Broker-managed finite scrcpy GUI/recording; process tree ends before return."""
    if not getattr(ctx, "broker", None):
        fail("Projection requires a broker lease")
    if not math.isfinite(args.duration) or args.duration <= 0:
        fail("Projection duration must be positive")
    dev = ctx.device()  # physical identity verified before launching GUI
    exe = shutil.which("scrcpy")
    if not exe:
        raise adb_err("SCRCPY_NOT_FOUND", "scrcpy not found on PATH")
    # Native time-limit permits scrcpy to finalize recordings before the hard job
    # deadline; unsupported installed versions return an explicit command error.
    argv = [exe, "--serial=" + dev.serial, "--no-audio", "--time-limit=" + str(args.duration)]
    if args.record:
        path = Path(args.record).resolve()
        if path.suffix.lower() not in (".mp4", ".mkv") or path.exists():
            fail("Recording needs a new local .mp4/.mkv output")
        path.parent.mkdir(parents=True, exist_ok=True)
        argv += ["--record=" + str(path)]
    try:
        # scrcpy supports ADB as the selected executable (upstream FAQ). Use
        # exactly the client whose protocol/physical identity cdev just checked.
        env = dict(os.environ, ADB=ctx.adb_exe())
        r = managed_process.run(argv, env=env, timeout=args.duration + 10)
    except Exception as e:
        raise adb_err("PROJECTION_ENDED", "Projection tree stopped: %s" % e)
    return Out({"exit_code": r.returncode}, ["Projection ended; managed process tree stopped."],
               exit_code=0 if r.returncode == 0 else 5)


from . import verification  # noqa: E402,F401 (register inventory)
