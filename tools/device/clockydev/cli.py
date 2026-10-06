"""Argument parsing, command dispatch, and human/JSON rendering."""
import argparse
import json
import os
import sys

from . import __version__
from . import constants as C
from .adb import Adb, resolve_adb
from .errors import CdevError, adb_err, usage

COMMANDS = {}   # name -> (setup(parser), handler(ctx, args) -> Out)


class Out:
    """Command result: `result` goes to JSON; `lines` is the human rendering."""

    def __init__(self, result=None, lines=None, warnings=None, artifacts=None, exit_code=0):
        self.exit_code = exit_code
        self.result = result if result is not None else {}
        self.lines = lines or []
        self.warnings = list(warnings or [])
        self.artifacts = list(artifacts or [])


def command(name, setup=None):
    def deco(fn):
        COMMANDS[name] = (setup or (lambda p: None), fn)
        return fn
    return deco


def repo_root():
    # tools/device/clockydev/cli.py -> repo root is 4 levels up
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))


class Ctx:
    def __init__(self, args, env=None):
        self.args = args
        self.env = os.environ if env is None else env
        self.root = repo_root()
        self.build_dir = os.path.join(self.root, "build", "device")
        self.json = bool(getattr(args, "json", False))
        self.flag_serial = getattr(args, "serial", None)
        self.adb_calls = []
        self._exe = None
        self._device = None
        self.command = args.command

    def rel(self, path):
        try:
            return os.path.relpath(path, self.root).replace("\\", "/")
        except ValueError:
            return path

    def adb_exe(self):
        if self._exe is None:
            exe = resolve_adb(self.env, repo_root=self.root)
            if not exe:
                raise adb_err("ADB_NOT_FOUND", "adb executable not found",
                              "Install Android platform-tools or set CLOCKY_ADB to the adb path.")
            self._exe = exe
        return self._exe

    def _log_adb(self, args, res):
        self.adb_calls.append({"args": list(args)[:6], "rc": res.rc})

    def raw_adb(self):
        return Adb(self.adb_exe(), None, self._log_adb)

    def device(self):
        """Select the device per policy; returns a Device bundle (cached)."""
        if self._device is None:
            from .session import select_for_ctx
            self._device = select_for_ctx(self)
        return self._device


def build_parser():
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--json", action="store_true", default=argparse.SUPPRESS,
                        help="emit a single JSON object on stdout")
    common.add_argument("--serial", default=argparse.SUPPRESS, help="adb serial to use")
    p = argparse.ArgumentParser(prog="cdev", parents=[common],
                                description="Clocky device layer: semantic adb for on-device verification.")
    p.add_argument("--version", action="version", version="cdev " + __version__)
    sub = p.add_subparsers(dest="command", metavar="<command>")
    _load_commands()
    for name, (setup, fn) in sorted(COMMANDS.items()):
        sp = sub.add_parser(name, parents=[common], help=(fn.__doc__ or "").strip().split("\n")[0])
        setup(sp)
    return p


def _load_commands():
    from . import commands  # noqa: F401  (registers via @command)


def _cand(c):
    if not isinstance(c, dict):
        return str(c)
    if "bounds" in c:
        return "[%s] %r id=%s bounds=%s flags=%s host=%s" % (c.get("index"), c.get("label"), (c.get("id") or "-").split(":id/")[-1],
                                                           c.get("bounds"), c.get("flags"), c.get("host"))
    return json.dumps(c, ensure_ascii=False)


def render_human(out, ctx):
    lines = list(out.lines)
    for w in out.warnings:
        lines.append("WARNING " + w)
    return "\n".join(lines)


def emit_json(obj):
    sys.stdout.write(json.dumps(obj, ensure_ascii=True, indent=None, default=str) + "\n")


def main(argv=None, env=None):
    parser = build_parser()
    argv = sys.argv[1:] if argv is None else argv
    # `adb -- <args>`: keep everything after the first `--` out of argparse
    raw_tail = None
    if "--" in argv:
        i = argv.index("--")
        argv, raw_tail = argv[:i], argv[i + 1:]
    try:
        args = parser.parse_args(argv)
    except SystemExit as e:
        return C.EXIT_OK if e.code in (0, None) else C.EXIT_USAGE
    if not args.command:
        parser.print_help()
        return C.EXIT_USAGE
    args.raw_tail = raw_tail
    ctx = Ctx(args, env)
    handler = COMMANDS[args.command][1]
    try:
        out = handler(ctx, args)
        code = C.EXIT_OK
    except CdevError as e:
        _finish_log(ctx, False, e.message)
        if ctx.json:
            emit_json({"ok": False, "command": args.command, "error": e.to_json()})
        else:
            sys.stderr.write("ERROR [%s] %s\n" % (e.code, e.message))
            for c in e.candidates:
                sys.stderr.write("  - %s\n" % (json.dumps(c, ensure_ascii=False) if isinstance(c, dict) else c))
            if e.hint:
                sys.stderr.write("NEXT  %s\n" % e.hint)
        return e.exit_code
    except KeyboardInterrupt:
        return 130
    code = out.exit_code
    _finish_log(ctx, code == 0, None, out)
    if ctx.json:
        dev = ctx._device
        emit_json({"ok": True, "command": args.command,
                   "device": dev.summary() if dev else None,
                   "result": out.result, "warnings": out.warnings,
                   "artifacts": out.artifacts})
    else:
        text = render_human(out, ctx)
        if text:
            sys.stdout.write(text + "\n")
    return code


def _finish_log(ctx, ok, err, out=None):
    try:
        from .session import log_command
        log_command(ctx, ok, err, out)
    except Exception:
        pass
