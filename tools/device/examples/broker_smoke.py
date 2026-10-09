"""Finite preservation-only verification unit, invoked inside `lease run`.

This script does not install, launch, reconfigure, delete or clear anything.
Hardware execution is an explicit later operator action, not part of broker tests.
"""
import json
import os
from pathlib import Path
import subprocess
import sys


def main():
    cdev = Path(__file__).resolve().parents[1] / "cdev.py"
    required = ("CLOCKY_LEASE_TOKEN", "CLOCKY_LEASE_SESSION", "CLOCKY_DEVICE_IDENTITY", "CLOCKY_SERIAL")
    if any(not os.environ.get(k) for k in required):
        raise RuntimeError("Run this finite unit via cdev lease run")
    for args in (("status",), ("widget", "--locate"), ("collect", "smoke")):
        p = subprocess.run([sys.executable, str(cdev), *args, "--json"], capture_output=True, timeout=360)
        if p.returncode:
            sys.stderr.buffer.write(p.stderr)
            sys.stdout.buffer.write(p.stdout)
            return p.returncode
        result = json.loads(p.stdout)
        if args[0] == "collect" and any(e["part"] != "screen_small" for e in result["result"]["errors"]):
            return 7
    return 0


if __name__ == "__main__":
    sys.exit(main())
