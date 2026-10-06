#!/usr/bin/env python
"""Clocky device layer entry point. See tools/device/README.md."""
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Git Bash (MSYSTEM) and the Claude tool decode output as UTF-8; the Windows console default is cp932.
_enc = "utf-8" if (os.environ.get("MSYSTEM") or os.environ.get("CDEV_UTF8")) else None
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(errors="backslashreplace", **({"encoding": _enc} if _enc else {}))
    except Exception:
        pass

from clockydev.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
