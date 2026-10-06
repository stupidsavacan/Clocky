"""adb execution layer: executable resolution, subprocess helpers, safety guards.

User-supplied values are never interpolated into device shell strings; shell
strings passed to `adb shell` are fixed text plus numbers.
"""
import os
import re
import shlex
import shutil
import subprocess

from . import constants as C
from .errors import adb_err, refused, usage


def normalize_text(data):
    """bytes -> str (utf-8, replace), CRLF -> LF."""
    if isinstance(data, bytes):
        data = data.decode("utf-8", errors="replace")
    return data.replace("\r\n", "\n")


def _read_sdk_dir(local_properties):
    try:
        with open(local_properties, encoding="utf-8") as f:
            for line in f:
                if line.startswith("sdk.dir="):
                    return line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
    except OSError:
        pass
    return None


def resolve_adb(env=None, which=shutil.which, exists=os.path.exists, repo_root=None, exe="adb.exe"):
    """Order: CLOCKY_ADB, PATH, ANDROID_HOME/ANDROID_SDK_ROOT, local.properties sdk.dir, default SDK dir."""
    env = os.environ if env is None else env
    if env.get("CLOCKY_ADB"):
        return env["CLOCKY_ADB"]
    found = which("adb")
    if found:
        return found
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if env.get(var):
            p = os.path.join(env[var], "platform-tools", exe)
            if exists(p):
                return p
    if repo_root:
        sdk = _read_sdk_dir(os.path.join(repo_root, "local.properties"))
        if sdk:
            p = os.path.join(sdk, "platform-tools", exe)
            if exists(p):
                return p
    if env.get("LOCALAPPDATA"):
        p = os.path.join(env["LOCALAPPDATA"], "Android", "Sdk", "platform-tools", exe)
        if exists(p):
            return p
    return None


class Result:
    def __init__(self, rc, out, err):
        self.rc = rc
        self.out = out      # bytes
        self.err = err      # bytes

    @property
    def text(self):
        return normalize_text(self.out)

    @property
    def err_text(self):
        return normalize_text(self.err)


class Adb:
    """Thin adb wrapper bound to one serial (or none, for `devices`)."""

    def __init__(self, exe, serial=None, logger=None):
        self.exe = exe
        self.serial = serial
        self.logger = logger  # callable(args, result) or None

    def bind(self, serial):
        return Adb(self.exe, serial, self.logger)

    def _argv(self, args):
        argv = [self.exe]
        if self.serial:
            argv += ["-s", self.serial]
        return argv + list(args)

    def run(self, args, timeout=30, check=False):
        argv = self._argv(args)
        try:
            p = subprocess.run(argv, capture_output=True, timeout=timeout)
            res = Result(p.returncode, p.stdout, p.stderr)
        except subprocess.TimeoutExpired:
            raise adb_err("ADB_TIMEOUT", "adb timed out after %ss: %s" % (timeout, " ".join(args)),
                          "Check the device is responsive; ask the user before restarting the adb server.")
        except OSError as e:
            raise adb_err("ADB_NOT_FOUND", "cannot execute adb (%s): %s" % (self.exe, e),
                          "Set CLOCKY_ADB or install Android platform-tools.")
        if self.logger:
            self.logger(args, res)
        if check and res.rc != 0:
            raise adb_err("ADB_FAILED", "adb %s failed (rc=%d): %s"
                          % (" ".join(args[:3]), res.rc, res.err_text.strip()[:300]))
        return res

    def shell(self, cmd, timeout=30, check=False):
        """Run a fixed shell string on the device."""
        return self.run(["shell", cmd], timeout=timeout, check=check)

    def exec_out(self, cmd, timeout=30, check=False):
        """Binary-clean output (screencap, cat)."""
        return self.run(["exec-out", cmd], timeout=timeout, check=check)


# ---- raw passthrough guard (developer safety guard, not a security sandbox) ----

_PM_DESTRUCTIVE = {"clear", "uninstall", "disable", "disable-user", "hide", "suspend"}
_TOP_DENY = {"uninstall", "reboot", "root", "unroot", "remount", "disable-verity", "enable-verity",
             "sideload", "reboot-bootloader"}
_MSYS_RE = re.compile(r"[A-Za-z]:[/\\][^\"']*?[/\\]Git[/\\]\S*", re.I)


def _tokens(args):
    toks = []
    for a in args:
        try:
            toks.extend(shlex.split(a, posix=True))
        except ValueError:
            toks.extend(a.split())
    return toks


def raw_denied(args):
    """Return a reason string if the raw adb args hit the denylist, else None."""
    toks = _tokens(args)
    i = 0
    while i < len(toks) and toks[i] in ("-s", "-d", "-e", "-t", "-H", "-P"):
        i += 2 if toks[i] in ("-s", "-t", "-H", "-P") else 1
    toks = toks[i:]
    if not toks:
        return None
    head = toks[0]
    if head in _TOP_DENY:
        return "adb %s is not allowed (can wipe widgets/settings or alter the device)" % head
    if head == "emu" and "kill" in toks:
        return "adb emu kill is not allowed"
    if head == "install" and "-d" in toks[1:]:
        return "adb install -d (downgrade) is not allowed"
    if head == "logcat" and any(x in ("-c", "--clear", "-G") for x in toks[1:]):
        return "logcat -c/-G is not allowed (destroys evidence)"
    if head == "shell":
        body = toks[1:]
        low = [t.lower() for t in body]
        for idx, t in enumerate(low):
            rest = low[idx + 1:]
            if t in ("pm", "cmd") and rest:
                if t == "cmd" and rest[0] == "package":
                    rest = rest[1:]
                if rest and rest[0] in _PM_DESTRUCTIVE:
                    return "shell %s %s is not allowed (destroys app data/widgets)" % (t, rest[0])
            if t in ("reboot", "su", "wipe", "shutdown"):
                return "shell %s is not allowed" % t
            if t == "logcat" and any(x in ("-c", "--clear", "-G") for x in body[idx + 1:]):
                return "logcat -c/-G is not allowed (destroys evidence)"
            if t == "settings" and rest and rest[0] in ("put", "delete"):
                return "shell settings put/delete is not allowed via raw adb (use `rotate` / `restore`)"
            if t == "rm":
                targets = [x for x in body[idx + 1:] if not x.startswith("-")]
                prefix = C.DEVICE_TMP_DIR + "/" + C.DEVICE_TMP_PREFIX
                if not targets or any(not x.startswith(prefix) or ".." in x for x in targets):
                    return "shell rm is only allowed for %s*" % prefix
    return None


def msys_suspect(args):
    """Detect Git-Bash MSYS path conversion in raw args."""
    for a in args:
        m = _MSYS_RE.search(a)
        if m:
            return m.group(0)
    return None


def check_raw(args):
    if not args:
        raise usage("NO_ARGS", "adb passthrough needs arguments: cdev adb -- <args...>")
    reason = raw_denied(args)
    if reason:
        raise refused("DENYLIST", reason,
                      "If this is truly needed, ask the human to run it directly in their own terminal.")
    bad = msys_suspect(args)
    if bad:
        raise usage("MSYS_PATH_CONVERSION", "argument looks mangled by Git Bash path conversion: %s" % bad,
                    "Prefix the command with MSYS_NO_PATHCONV=1, or run it from PowerShell.")
