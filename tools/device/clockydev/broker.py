"""Persistent, cooperative device arbitration. No ADB calls in this module."""
import contextlib
import hashlib
import json
import os
from pathlib import Path
import secrets
import re
import time
import uuid

from .errors import refused


def fail(message):
    raise refused("BROKER_REFUSED", message, "Use `cdev lease status`; recovery never steals a live lease.")


class FileLock:
    """An OS byte-range lock on Windows; flock on POSIX for host-only tests.

    The file is permanent. Its existence has no ownership meaning.
    """
    def __init__(self, path):
        self.path = Path(path)
        self.file = None

    def acquire(self, timeout=None):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.file = open(self.path, "a+b")
        if self.path.stat().st_size == 0:
            self.file.write(b"0")
            self.file.flush()
        deadline = None if timeout is None else time.monotonic() + timeout
        while True:
            try:
                self.file.seek(0)
                if os.name == "nt":
                    import msvcrt
                    msvcrt.locking(self.file.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(self.file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                return self
            except OSError:
                if deadline is not None and time.monotonic() >= deadline:
                    self.file.close()
                    self.file = None
                    return None
                time.sleep(0.05)

    def close(self):
        if self.file is not None:
            try:
                self.file.seek(0)
                if os.name == "nt":
                    import msvcrt
                    msvcrt.locking(self.file.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(self.file.fileno(), fcntl.LOCK_UN)
            finally:
                self.file.close()
                self.file = None

    def __enter__(self):
        return self.acquire()

    def __exit__(self, *exc):
        self.close()


def atomic_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    try:
        with open(tmp, "x", encoding="utf-8") as f:
            json.dump(value, f, indent=2, ensure_ascii=True)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
    finally:
        tmp.unlink(missing_ok=True)


def root_from_env(env=None):
    env = os.environ if env is None else env
    if env.get("CLOCKY_BROKER_ROOT"):
        return Path(env["CLOCKY_BROKER_ROOT"]).resolve()
    if env.get("LOCALAPPDATA"):
        return Path(env["LOCALAPPDATA"]) / "Clocky" / "device-broker"
    return Path.home() / ".local" / "share" / "Clocky" / "device-broker"


def digest(token):
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


class Broker:
    def __init__(self, root):
        self.root = Path(root).resolve()
        self.path = self.root / "registry.json"

    def device_dir(self, identity):
        return self.root / "devices" / digest(identity)

    def lock(self, identity, name):
        return FileLock(self.device_dir(identity) / (name + ".lock"))

    @contextlib.contextmanager
    def edit(self):
        with FileLock(self.root / "registry.lock"):
            try:
                with open(self.path, encoding="utf-8") as f:
                    state = json.load(f)
                if state.get("version") != 2 or not isinstance(state.get("devices"), dict):
                    fail("Invalid registry schema; preserve the file and investigate")
                for key, d in state["devices"].items():
                    if d.get("identity") != key or d.get("state") not in ("FREE", "ACTIVE", "RELEASING", "DIRTY"):
                        fail("Invalid device state; preserve the registry")
                    if not isinstance(d.get("queue"), list) or "owner" not in d:
                        fail("Invalid device queue/owner")
                    if not isinstance(d.get("retired_sessions"), list) or not isinstance(d.get("serials"), list):
                        fail("Invalid retired sessions/transport aliases")
                    if type(d.get("recovery_required")) is not bool:
                        fail("Invalid recovery flag")
                    if d["state"] == "FREE" and (d["owner"] or d["recovery_required"]):
                        fail("Inconsistent FREE device state")
                    if d["state"] in ("ACTIVE", "RELEASING") and not d["owner"]:
                        fail("Active state has no owner")
                    sessions = []
                    for entry in [d["owner"], *d["queue"]]:
                        if entry is None:
                            continue
                        if (not isinstance(entry, dict) or not isinstance(entry.get("agent"), str)
                                or not isinstance(entry.get("session"), str)
                                or not re.fullmatch(r"[A-Za-z0-9-]{1,80}", entry["session"])
                                or not re.fullmatch(r"[0-9a-f]{64}", entry.get("token_hash", ""))):
                            fail("Invalid request/owner record")
                        sessions.append(entry["session"])
                    if len(sessions) != len(set(sessions)):
                        fail("Duplicate sessions in registry")
                    if d["owner"] and (not d["owner"].get("build_dir") or not re.fullmatch(
                            r"[0-9a-f]{64}", d["owner"].get("cleanup_hash", ""))):
                        fail("Invalid owner cleanup/evidence record")
            except FileNotFoundError:
                # Missing registry is legacy mode only BEFORE registration. A marker
                # makes accidental deletion fail closed thereafter.
                if (self.root / "enabled").exists():
                    fail("Registry missing after enablement; recovery required")
                state = {"version": 2, "devices": {}}
            except (ValueError, TypeError, AttributeError) as e:
                fail("Unreadable registry: %s" % e)
            try:
                yield state
            finally:
                # Orphan detection must persist even when authorization refuses.
                atomic_json(self.path, state)

    def enabled(self):
        return self.path.exists() or (self.root / "enabled").exists()

    def register(self, identity, serials):
        if not identity or not serials or any(not s for s in serials):
            fail("Identity and explicit serial aliases required")
        with self.edit() as st:
            for d in st["devices"].values():
                if d["identity"] != identity and set(d["serials"]) & set(serials):
                    fail("Transport alias already belongs to another identity")
            if identity in st["devices"]:
                fail("Already registered; do not reset existing ownership/baselines")
            st["devices"][identity] = {
                "identity": identity, "serials": list(dict.fromkeys(serials)),
                "state": "DIRTY", "recovery_required": True,
                "reason": "Initial migration requires quiescence and clean baseline verification",
                "owner": None, "queue": [], "installed": None,
                "baseline": None, "last_clean_handoff": None, "last_result": None,
                "retired_sessions": [],
            }
            (self.root / "enabled").touch(exist_ok=True)

    def _device(self, st, identity):
        d = st["devices"].get(identity)
        if d is None:
            fail("Unregistered device identity %s" % identity)
        return d

    def _detect_death(self, d):
        if d["state"] in ("ACTIVE", "RELEASING"):
            probe = self.lock(d["identity"], "owner")
            if probe.acquire(timeout=0):
                probe.close()
                d.update(state="DIRTY", recovery_required=True,
                         reason="RECOVERY_REQUIRED: owner supervisor disappeared")

    def status(self, identity):
        with self.edit() as st:
            d = self._device(st, identity)
            self._detect_death(d)
            # Never publish bearer credential hashes through diagnostics.
            out = json.loads(json.dumps(d))
            for item in [out.get("owner"), *out["queue"]]:
                if item:
                    item.pop("token_hash", None)
                    item.pop("cleanup_hash", None)
                    item.pop("setup_hash", None)
            return out

    def request(self, identity, agent, session, token, worktree):
        if not agent or not session or not token:
            fail("Agent, session and token required")
        if not re.fullmatch(r"[A-Za-z0-9-]{1,80}", session):
            fail("Session must be a simple ID, not a path")
        with self.edit() as st:
            d = self._device(st, identity)
            self._detect_death(d)
            if session in d["retired_sessions"]:
                fail("Session is retired; use fresh credentials for each finite test unit")
            matches = [x for x in [d["owner"], *d["queue"]] if x and x["session"] == session]
            if matches:
                x = matches[0]
                if x["agent"] != agent or x["token_hash"] != digest(token):
                    fail("Session already registered with different credentials")
                return x["session"]
            d["queue"].append({"agent": agent, "session": session,
                               "token_hash": digest(token), "worktree": str(Path(worktree).resolve()),
                               "requested_at": time.time()})
            return session

    def cancel(self, identity, session, token):
        with self.edit() as st:
            d = self._device(st, identity)
            for i, x in enumerate(d["queue"]):
                if x["session"] == session:
                    if x["token_hash"] != digest(token):
                        fail("Invalid cancellation token")
                    d["queue"].pop(i)
                    d["retired_sessions"].append(session)
                    return
            fail("No queued request for this session (ACTIVE requests cannot be cancelled)")

    def claim(self, identity, session, token, recovery=False, agent=None, worktree=None, preparing=False):
        # Lifetime lock is held by the caller until complete() or abnormal exit.
        life = self.lock(identity, "owner")
        if not life.acquire(timeout=0):
            return None
        transferred = False
        lease = None
        try:
            with self.edit() as st:
                d = self._device(st, identity)
                # We hold the previously free lifetime lock: any ACTIVE record is orphaned.
                if d["state"] in ("ACTIVE", "RELEASING"):
                    d.update(state="DIRTY", recovery_required=True, reason="RECOVERY_REQUIRED: orphaned owner")
                if recovery:
                    if d["state"] != "DIRTY":
                        fail("Recovery is allowed only for DIRTY devices")
                    if not re.fullmatch(r"[A-Za-z0-9-]{1,80}", session) or session in d["retired_sessions"]:
                        fail("Recovery requires a fresh simple session ID")
                    if not agent or not token or any(x["session"] == session for x in d["queue"]):
                        fail("Recovery requires fresh credentials, not a queued ordinary request")
                    entry = {"agent": agent, "session": session, "token_hash": digest(token),
                             "worktree": str(Path(worktree).resolve()), "recovery": True,
                             "needs_baseline": bool(d["owner"] and not d.get("baseline"))}
                    if d["owner"] and d["owner"]["session"] not in d["retired_sessions"]:
                        d["retired_sessions"].append(d["owner"]["session"])
                else:
                    if d["state"] == "DIRTY" or d["recovery_required"]:
                        # Save orphan detection before returning. Waiting callers never take over.
                        return None
                    if d["state"] != "FREE" or not d["queue"]:
                        return None
                    entry = d["queue"][0]
                    if entry["session"] != session:
                        return None
                    if entry["token_hash"] != digest(token):
                        fail("Invalid claim token")
                    d["queue"].pop(0)
                cleanup = secrets.token_urlsafe(32)
                setup_token = secrets.token_urlsafe(32)
                entry.update(cleanup_hash=digest(cleanup), supervisor_pid=os.getpid(),
                             acquired_at=time.time(), release_requested=False,
                             phase="PREPARING" if preparing else "READY", setup_hash=digest(setup_token))
                entry["build_dir"] = ((d.get("baseline") or {}).get("build_dir") if recovery else None) or str(
                    self.device_dir(identity) / "leases" / session)
                if recovery and d["owner"] and not d.get("baseline"):
                    entry["build_dir"] = d["owner"]["build_dir"]
                d.update(owner=entry, state="ACTIVE")
                lease = Lease(self, identity, session, token, cleanup, life, entry["build_dir"], setup_token, preparing)
            transferred = True
            return lease
        finally:
            if not transferred:
                life.close()

    def _authorize(self, d, session, token):
        self._detect_death(d)
        owner = d["owner"]
        if d["state"] not in ("ACTIVE", "RELEASING") or not owner:
            fail("Device is %s; RECOVERY_REQUIRED=%s" % (d["state"], d["recovery_required"]))
        expected = owner["cleanup_hash"] if d["state"] == "RELEASING" else owner["token_hash"]
        if d["state"] == "ACTIVE" and owner.get("phase") == "PREPARING":
            expected = owner["setup_hash"]
        if owner["session"] != session or not secrets.compare_digest(expected, digest(token or "")):
            fail("Invalid lease token/session, or cleanup has already begun")
        return owner

    @contextlib.contextmanager
    def operation(self, identity, session, token):
        # Keep the operation lock through subprocess completion, preventing release races.
        with self.lock(identity, "operation"):
            with self.edit() as st:
                owner = dict(self._authorize(self._device(st, identity), session, token))
            yield owner

    def resolve_serial(self, serial):
        with self.edit() as st:
            matches = [d["identity"] for d in st["devices"].values() if serial in d["serials"]]
            if len(matches) != 1:
                fail("Explicit registered --serial required; discovery cannot probe another owner's phone")
            return matches[0]

    def update(self, identity, session, token, **fields):
        if set(fields) - {"baseline", "installed", "last_result"}:
            fail("Unsupported metadata update")
        with self.edit() as st:
            d = self._device(st, identity)
            self._authorize(d, session, token)
            d.update(fields)

    def request_release(self, identity, session, token):
        with self.edit() as st:
            d = self._device(st, identity)
            owner = self._authorize(d, session, token)
            if d["state"] != "ACTIVE" or owner["release_requested"]:
                fail("Release already requested")
            owner["release_requested"] = True


class Lease:
    def __init__(self, broker, identity, session, token, cleanup, life, build_dir, setup_token, preparing):
        self.broker, self.identity, self.session = broker, identity, session
        self.token, self.cleanup, self.life = token, cleanup, life
        self._build_dir = Path(build_dir)
        self.setup_token, self.preparing = setup_token, preparing

    @property
    def access_token(self):
        return self.setup_token if self.preparing else self.token

    def ready(self):
        with self.broker.edit() as st:
            d = self.broker._device(st, self.identity)
            self.broker._authorize(d, self.session, self.access_token)
            d["owner"]["phase"] = "READY"
        self.preparing = False

    @property
    def build_dir(self):
        return self._build_dir

    def begin_release(self):
        with self.broker.lock(self.identity, "operation"):
            with self.broker.edit() as st:
                d = self.broker._device(st, self.identity)
                self.broker._authorize(d, self.session, self.access_token)
                d["state"] = "RELEASING"

    def complete(self, evidence):
        with self.broker.lock(self.identity, "operation"):
            with self.broker.edit() as st:
                d = self.broker._device(st, self.identity)
                self.broker._authorize(d, self.session, self.cleanup)
                clean = evidence.get("clean") is True
                d["last_result"] = evidence
                if self.session not in d["retired_sessions"]:
                    d["retired_sessions"].append(self.session)
                if clean:
                    d.update(state="FREE", owner=None, recovery_required=False, reason=None,
                             last_clean_handoff=evidence, baseline=None)
                else:
                    d.update(state="DIRTY", recovery_required=True, reason="Restoration/verification failed")
                # Unlock while registry mutex is still held, so the next head cannot
                # mistake an intentionally released owner for an orphan.
                self.life.close()


@contextlib.contextmanager
def command_access(ctx):
    """Before ANY ADB (including discovery); local compare/lease need no device."""
    broker = Broker(root_from_env(ctx.env))
    credentials = any(ctx.env.get(k) for k in ("CLOCKY_LEASE_TOKEN", "CLOCKY_LEASE_SESSION", "CLOCKY_DEVICE_IDENTITY"))
    if ctx.command in ("compare", "lease"):
        yield
        return
    if not broker.enabled() and not credentials:
        yield                         # backwards-compatible unconfigured v1
        return
    serial = ctx.flag_serial or ctx.env.get("CLOCKY_SERIAL") or ctx.env.get("ANDROID_SERIAL")
    identity = broker.resolve_serial(serial)
    if ctx.env.get("CLOCKY_DEVICE_IDENTITY") != identity:
        fail("Lease device identity does not match the registered serial")
    session = ctx.env.get("CLOCKY_LEASE_SESSION")
    token = ctx.env.get("CLOCKY_LEASE_TOKEN")
    with broker.operation(identity, session, token) as owner:
        ctx.broker = broker
        ctx.broker_identity = identity
        ctx.build_dir = owner["build_dir"]
        yield
