import os

FIX = os.path.join(os.path.dirname(__file__), "fixtures")


def fixture(name):
    with open(os.path.join(FIX, name), encoding="utf-8") as f:
        return f.read()


def fixture_bytes(name):
    with open(os.path.join(FIX, name), "rb") as f:
        return f.read()


class FakeAdb:
    """Records shell calls; `responses` maps a substring of the command to stdout text."""

    def __init__(self, responses=None):
        self.responses = responses or {}
        self.calls = []

    class R:
        def __init__(self, text, rc=0):
            self.text = text
            self.out = text.encode("utf-8")
            self.rc = rc
            self.err_text = ""

    def _resp(self, cmd):
        for k, v in self.responses.items():
            if k in cmd:
                return v(cmd) if callable(v) else v
        return ""

    def shell(self, cmd, timeout=30, check=False):
        self.calls.append(cmd)
        return FakeAdb.R(self._resp(cmd))

    def run(self, args, timeout=30, check=False):
        self.calls.append(" ".join(args))
        return FakeAdb.R(self._resp(" ".join(args)))

    def exec_out(self, cmd, timeout=30, check=False):
        self.calls.append("exec-out " + cmd)
        return FakeAdb.R(self._resp(cmd))
