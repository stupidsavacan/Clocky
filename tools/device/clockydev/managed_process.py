"""Finite host subprocess trees. Windows children enter a Job before execution."""
import os
import subprocess
import tempfile
import time


def run(argv, env=None, cwd=None, timeout=300):
    """Capture a child; terminate its descendants before returning, even on failure.

    Broker run/hook commands must be finite and must not detach device clients.
    Windows Job assignment failures fail before any child code executes.
    """
    if os.name != "nt":
        import signal
        with tempfile.TemporaryFile() as out, tempfile.TemporaryFile() as err:
            p = subprocess.Popen(argv, env=env, cwd=cwd, stdout=out,
                                 stderr=err, stdin=subprocess.DEVNULL, start_new_session=True)
            try:
                p.wait(timeout=timeout)
            finally:
                try:
                    os.killpg(p.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                p.wait()
            out.seek(0)
            err.seek(0)
            return subprocess.CompletedProcess(argv, p.returncode, out.read(), err.read())
    return _windows_run(argv, env, cwd, timeout)


def _windows_run(argv, env, cwd, timeout):
    import ctypes
    from ctypes import wintypes as W
    import msvcrt
    import _winapi

    K = ctypes.WinDLL("kernel32", use_last_error=True)
    K.CreateJobObjectW.argtypes = [ctypes.c_void_p, W.LPCWSTR]
    K.CreateJobObjectW.restype = W.HANDLE
    K.SetInformationJobObject.argtypes = [W.HANDLE, ctypes.c_int, ctypes.c_void_p, W.DWORD]
    K.AssignProcessToJobObject.argtypes = [W.HANDLE, W.HANDLE]
    K.TerminateJobObject.argtypes = [W.HANDLE, W.UINT]
    K.CloseHandle.argtypes = [W.HANDLE]
    K.ResumeThread.argtypes = [W.HANDLE]
    K.ResumeThread.restype = W.DWORD
    K.QueryInformationJobObject.argtypes = [W.HANDLE, ctypes.c_int, ctypes.c_void_p, W.DWORD, ctypes.c_void_p]

    class ACCOUNTING(ctypes.Structure):
        _fields_ = [(n, ctypes.c_int64) for n in ("user_time", "kernel_time", "period_user", "period_kernel")] + [
            (n, W.DWORD) for n in ("faults", "total", "active", "terminated")]

    class BASIC(ctypes.Structure):
        _fields_ = [("process_time", ctypes.c_int64), ("job_time", ctypes.c_int64),
                    ("flags", W.DWORD), ("min_ws", ctypes.c_size_t), ("max_ws", ctypes.c_size_t),
                    ("active_limit", W.DWORD), ("affinity", ctypes.c_size_t),
                    ("priority", W.DWORD), ("scheduling", W.DWORD)]

    class IO(ctypes.Structure):
        _fields_ = [(n, ctypes.c_uint64) for n in ("read_ops", "write_ops", "other_ops", "read_bytes", "write_bytes", "other_bytes")]

    class EXTENDED(ctypes.Structure):
        _fields_ = [("basic", BASIC), ("io", IO), ("process_mem", ctypes.c_size_t),
                    ("job_mem", ctypes.c_size_t), ("peak_process", ctypes.c_size_t), ("peak_job", ctypes.c_size_t)]

    job = K.CreateJobObjectW(None, None)
    if not job:
        raise ctypes.WinError(ctypes.get_last_error())
    hp = ht = None
    def stop_job():
        if not K.TerminateJobObject(job, 1):
            raise ctypes.WinError(ctypes.get_last_error())
        deadline = time.monotonic() + 5
        while True:
            counts = ACCOUNTING()
            if not K.QueryInformationJobObject(job, 1, ctypes.byref(counts), ctypes.sizeof(counts), None):
                raise ctypes.WinError(ctypes.get_last_error())
            if counts.active == 0:
                return
            if time.monotonic() > deadline:
                raise OSError("Managed subprocess tree did not stop; recovery required")
            time.sleep(.01)
    try:
        info = EXTENDED()
        info.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        if not K.SetInformationJobObject(job, 9, ctypes.byref(info), ctypes.sizeof(info)):
            raise ctypes.WinError(ctypes.get_last_error())
        with tempfile.TemporaryFile() as out, tempfile.TemporaryFile() as err, open(os.devnull, "rb") as inp:
            handles = [msvcrt.get_osfhandle(f.fileno()) for f in (inp, out, err)]
            si = subprocess.STARTUPINFO()
            si.dwFlags = subprocess.STARTF_USESTDHANDLES
            si.hStdInput, si.hStdOutput, si.hStdError = handles
            si.lpAttributeList = {"handle_list": handles}
            try:
                for h in handles:
                    os.set_handle_inheritable(h, True)
                hp, ht, _pid, _tid = _winapi.CreateProcess(
                    None, subprocess.list2cmdline([str(a) for a in argv]), None, None, True,
                    0x4 | subprocess.CREATE_NO_WINDOW, env, cwd, si)  # CREATE_SUSPENDED
            finally:
                for h in handles:
                    os.set_handle_inheritable(h, False)
            if not K.AssignProcessToJobObject(job, hp):
                _winapi.TerminateProcess(hp, 1)
                raise ctypes.WinError(ctypes.get_last_error())
            if K.ResumeThread(ht) == 0xFFFFFFFF:
                raise ctypes.WinError(ctypes.get_last_error())
            deadline = time.monotonic() + timeout
            while _winapi.WaitForSingleObject(hp, 50) == _winapi.WAIT_TIMEOUT:
                if time.monotonic() >= deadline:
                    raise subprocess.TimeoutExpired(argv, timeout)
            rc = _winapi.GetExitCodeProcess(hp)
            # Kill any detached descendant before reading files/releasing device.
            stop_job()
            out.seek(0)
            err.seek(0)
            return subprocess.CompletedProcess(argv, rc, out.read(), err.read())
    finally:
        try:
            stop_job()
        finally:
            if hp is not None:
                _winapi.WaitForSingleObject(hp, 5000)
                _winapi.CloseHandle(hp)
            if ht is not None:
                _winapi.CloseHandle(ht)
            K.CloseHandle(job)
