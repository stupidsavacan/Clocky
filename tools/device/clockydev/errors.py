"""Error type carrying exit code, machine code, and a next-step hint."""
from . import constants as C


class CdevError(Exception):
    def __init__(self, exit_code, code, message, hint=None, candidates=None):
        super().__init__(message)
        self.exit_code = exit_code
        self.code = code
        self.message = message
        self.hint = hint
        self.candidates = candidates or []

    def to_json(self):
        d = {"code": self.code, "message": self.message}
        if self.hint:
            d["hint"] = self.hint
        if self.candidates:
            d["candidates"] = self.candidates
        return d


def usage(code, message, hint=None, candidates=None):
    return CdevError(C.EXIT_USAGE, code, message, hint, candidates)


def device_err(code, message, hint=None, candidates=None):
    return CdevError(C.EXIT_DEVICE, code, message, hint, candidates)


def ui_err(code, message, hint=None, candidates=None):
    return CdevError(C.EXIT_UI, code, message, hint, candidates)


def adb_err(code, message, hint=None):
    return CdevError(C.EXIT_ADB, code, message, hint)


def refused(code, message, hint=None):
    return CdevError(C.EXIT_REFUSED, code, message, hint)


def check_failed(code, message, hint=None, candidates=None):
    return CdevError(C.EXIT_CHECK, code, message, hint, candidates)
