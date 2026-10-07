import argparse
import os
import shutil
import tempfile
import unittest
from unittest import mock

from clockydev import constants as C
from clockydev import devstate as D
from clockydev import cmd_misc as M
from clockydev.errors import CdevError
from clockydev.session import load_state
from tests.helpers import FakeAdb, fixture


class TestParsers(unittest.TestCase):
    def test_pidof(self):
        self.assertEqual(D.parse_pidof("13853\n", 0), [13853])
        self.assertEqual(D.parse_pidof("1 22 \n", 0), [1, 22])
        self.assertEqual(D.parse_pidof("", 1), [])
        self.assertIsNone(D.parse_pidof("pidof: not found", 127))

    def test_ps_api34(self):
        self.assertEqual(D.parse_ps(fixture("moto_g13_ps_A.txt"), C.PACKAGE), [8474])

    def test_ps_api25_exact_name_only(self):
        self.assertEqual(D.parse_ps(fixture("emu25_ps.txt"), C.PACKAGE), [5499])

    def test_ps_garbage(self):
        self.assertEqual(D.parse_ps("", C.PACKAGE), [])
        self.assertEqual(D.parse_ps("nothing useful", C.PACKAGE), [])


class ScriptedAdb:
    """shell(): first matching prefix wins; list values are consumed one per call (last one repeats)."""

    class R:
        def __init__(self, text, rc=0):
            self.text, self.rc, self.err_text = text, rc, ""

    def __init__(self, script):
        self.script, self.calls = script, []

    def shell(self, cmd, timeout=30, check=False):
        self.calls.append(cmd)
        for k, v in self.script.items():
            if cmd.startswith(k):
                if callable(v):
                    v = v(self.calls)
                elif isinstance(v, list):
                    v = v.pop(0) if len(v) > 1 else v[0]
                return v if isinstance(v, ScriptedAdb.R) else ScriptedAdb.R(v)
        return ScriptedAdb.R("")


class Dev:
    def __init__(self, adb):
        self.adb, self.warnings, self.serial = adb, [], "SER"


class Ctx:
    def __init__(self, adb, build_dir):
        self._dev, self.build_dir = Dev(adb), build_dir

    def device(self):
        return self._dev


FOCUS_LAUNCHER = "mCurrentFocus=Window{abc u0 com.motorola.launcher3/com.android.launcher3.Launcher}"
FOCUS_CLOCKY = "mCurrentFocus=Window{abc u0 com.stupidsavacan.clocky/com.stupidsavacan.clocky.widget.studio.StudioActivity}"
PKG_DEBUG = "versionCode=2 minSdk=25\n    flags=[ DEBUGGABLE HAS_CODE ]\n userId=10232"
PKG_REL = "versionCode=2 minSdk=25\n    flags=[ HAS_CODE ]\n userId=10232"
NR = ScriptedAdb.R


def alive_until_run_as(calls):
    return "" if any(c.startswith("run-as") for c in calls) else "5499\n"


def run(adb, action, hard=False, timeout=1.0, build_dir=None):
    args = argparse.Namespace(action=action, hard=hard, timeout=timeout)
    return M.cmd_proc(Ctx(adb, build_dir), args)


class TestProc(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.t, True)
        p = mock.patch("time.sleep", lambda s: None)
        p.start()
        self.addCleanup(p.stop)
        t = mock.patch("time.monotonic", mock.Mock(side_effect=iter(x * 0.5 for x in range(1000))))
        t.start()
        self.addCleanup(t.stop)

    def base(self, **kw):
        s = {"dumpsys window": FOCUS_LAUNCHER, "date +%s": "1791367600.5\n", "dumpsys package": PKG_DEBUG}
        s.update(kw)
        return ScriptedAdb(s)

    def test_status(self):
        out = run(self.base(pidof=["13853\n"]), "status", build_dir=self.t)
        self.assertEqual(out.result, {"running": True, "pids": [13853], "source": "pidof", "foreground": False})
        self.assertEqual(out.lines, ["clocky process: running pid 13853"])

    def test_status_absent_rc1_is_ok(self):
        out = run(self.base(pidof=NR("", 1)), "status", build_dir=self.t)
        self.assertFalse(out.result["running"])
        self.assertEqual(out.lines, ["clocky process: absent"])

    def test_status_falls_back_to_ps(self):
        adb = self.base(pidof=NR("pidof: not found", 127), **{"ps -A": fixture("moto_g13_ps_A.txt")})
        self.assertEqual(run(adb, "status", build_dir=self.t).result["pids"], [8474])
        self.assertEqual(adb.calls.count("ps -A"), 1)

    def test_kill_success_second_poll_and_mark(self):
        adb = self.base(pidof=["15199\n", "15199\n", ""])
        out = run(adb, "kill", build_dir=self.t)
        self.assertTrue(out.result["killed"])
        self.assertEqual(out.result["method"], "am-kill")
        self.assertIn("am kill com.stupidsavacan.clocky", adb.calls)
        self.assertEqual(load_state(self.t)["marks"][C.PROC_KILL_MARK], 1791367600.5)

    def test_kill_when_absent(self):
        adb = self.base(pidof=NR("", 1))
        out = run(adb, "kill", build_dir=self.t)
        self.assertFalse(out.result["killed"])
        self.assertFalse([c for c in adb.calls if c.startswith("am ")])

    def test_kill_refused_in_foreground(self):
        adb = self.base(pidof=["1\n"], **{"dumpsys window": FOCUS_CLOCKY})
        with self.assertRaises(CdevError) as c:
            run(adb, "kill", build_dir=self.t)
        self.assertEqual((c.exception.exit_code, c.exception.code), (2, "CLOCKY_FOREGROUND"))
        self.assertFalse([x for x in adb.calls if x.startswith("am ")])

    def test_ineffective_without_hard_is_exit5(self):
        adb = self.base(pidof=["5499\n"])
        with self.assertRaises(CdevError) as c:
            run(adb, "kill", build_dir=self.t)
        self.assertEqual((c.exception.exit_code, c.exception.code), (5, "KILL_INEFFECTIVE"))
        self.assertFalse([x for x in adb.calls if "run-as" in x])
        self.assertNotIn(C.PROC_KILL_MARK, load_state(self.t).get("marks", {}))

    def test_hard_uses_exact_run_as_command(self):
        adb = self.base(pidof=alive_until_run_as)
        out = run(adb, "kill", hard=True, build_dir=self.t)
        self.assertEqual(out.result["method"], "run-as-kill-9")
        self.assertIn("run-as com.stupidsavacan.clocky kill -9 5499", adb.calls)

    def test_hard_refused_when_not_debuggable(self):
        adb = self.base(pidof=["5499\n"], **{"dumpsys package": PKG_REL})
        with self.assertRaises(CdevError) as c:
            run(adb, "kill", hard=True, build_dir=self.t)
        self.assertEqual((c.exception.exit_code, c.exception.code), (6, "HARD_KILL_REFUSED"))
        self.assertFalse([x for x in adb.calls if "run-as" in x])

    def test_never_force_stop(self):
        adb = self.base(pidof=alive_until_run_as)
        run(adb, "kill", hard=True, build_dir=self.t)
        self.assertFalse([x for x in adb.calls if "force-stop" in x])


if __name__ == "__main__":
    unittest.main()


class TestTickingLoop(unittest.TestCase):
    def run_loop(self, texts, pids, timeout=75.0, no_process=False):
        from clockydev import cmd_widget as CW
        it_t, it_p = iter(texts), iter(pids)
        last = {"t": None}
        now = {"v": 0.0}

        def read():
            last["t"] = next(it_t, last["t"])
            return "<xml/>", {"text": last["t"]}

        def sleep(s):
            now["v"] += s
        res, _a, _b = CW.run_ticking(read, lambda: next(it_p, []), timeout, no_process, sleep=sleep, clock=lambda: now["v"])
        return res

    def test_pass_when_text_changes(self):
        r = self.run_loop(["7:19", "7:19", "7:20"], [[], [], []], no_process=True)
        self.assertTrue(r["passed"])
        self.assertEqual((r["before"], r["after"], r["elapsed"]), ("7:19", "7:20", 10.0))
        self.assertTrue(r["process_absent_throughout"])

    def test_timeout_fails(self):
        r = self.run_loop(["7:19"] * 100, [[]] * 100, timeout=12.0)
        self.assertFalse(r["passed"])
        self.assertEqual(r["before"], r["after"])
        self.assertEqual(len(r["samples"]), 4)

    def test_no_process_violation_fails(self):
        r = self.run_loop(["7:19", "7:20"], [[], [4242]], no_process=True)
        self.assertFalse(r["passed"])
        self.assertFalse(r["process_absent_throughout"])
        self.assertEqual(r["samples"][1]["pids"], [4242])

    def test_process_allowed_without_flag(self):
        r = self.run_loop(["7:19", "7:20"], [[1], [1]])
        self.assertTrue(r["passed"])

    def test_pick_entry_rules(self):
        from clockydev import cmd_widget as CW
        with self.assertRaises(CdevError) as c:
            CW.pick_entry([], None)
        self.assertEqual(c.exception.code, "NO_HOST_ON_SCREEN")
        two = [{"host": mock.Mock(bounds=(0, 0, 1, 1)), "time": 1, "text": "1:00"}] * 2
        with self.assertRaises(CdevError) as c:
            CW.pick_entry(two, None)
        self.assertEqual((c.exception.exit_code, c.exception.code), (2, "MULTIPLE_HOSTS"))
        self.assertEqual(CW.pick_entry(two, 1)["text"], "1:00")
        with self.assertRaises(CdevError) as c:
            CW.pick_entry([{"host": mock.Mock(bounds=(0, 0, 1, 1)), "time": None, "text": None}], None)
        self.assertEqual((c.exception.exit_code, c.exception.code), (4, "NO_TIME_TEXT"))
