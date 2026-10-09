"""Fail-closed prepare/restore/collect/compare/finish transaction."""
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time

from . import constants as C
from .broker import atomic_json, fail
from .cli import Out, command
from .session import load_state, pending_restore
from . import managed_process


def installed_apk(adb, previous=None):
    r = adb.shell("pm path " + C.PACKAGE, check=True)
    paths = [line[8:].strip() for line in r.text.splitlines() if line.startswith("package:")]
    if len(paths) != 1 or not re.fullmatch(r"/data/app/[A-Za-z0-9_./=+~-]+/base\.apk", paths[0]):
        fail("Installed APK path unavailable or split APK: cannot establish provenance")
    r = adb.exec_out("cat " + paths[0], timeout=120, check=True)
    if not r.out.startswith(b"PK"):
        fail("Installed APK is not readable; no SHA can be claimed")
    sha = hashlib.sha256(r.out).hexdigest()
    previous = previous or {}
    return {"sha256": sha, "source_commit": previous.get("source_commit") if previous.get("sha256") == sha else None,
            "observed_at": time.time(), "source": "installed base.apk bytes"}


@command("inventory")
def cmd_inventory(ctx, args):
    """Read installed APK bytes and report verified SHA/provenance."""
    dev = ctx.device()
    broker = getattr(ctx, "broker", None)
    previous = broker.status(dev.identity).get("installed") if broker else None
    info = installed_apk(dev.adb, previous)
    if broker:
        broker.update(dev.identity, ctx.env["CLOCKY_LEASE_SESSION"], ctx.env["CLOCKY_LEASE_TOKEN"], installed=info)
        from .session import save_state
        state = load_state(ctx.build_dir)
        state["install"] = info
        save_state(ctx.build_dir, state)
    return Out(info, ["installed APK SHA-256: " + info["sha256"],
                      "source commit: " + (info["source_commit"] or "UNKNOWN (not inferred from this checkout)")])


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def validate_bundle(path, identity):
    """Partial/missing data cannot certify restoration (Pillow thumbnail optional)."""
    path = Path(path)
    meta, widget = read_json(path / "meta.json"), read_json(path / "widget.json")
    if meta["device"]["identity"] != identity:
        fail("Evidence identity mismatch")
    if [e for e in meta["errors"] if e["part"] != "screen_small"]:
        fail("Partial evidence cannot certify a clean handoff")
    if widget.get("prefs_status") != "ok":
        fail("Widget settings unavailable")
    prefs = (path / "clocky_widget_settings.xml").read_text(encoding="utf-8")
    if not prefs.strip():
        fail("Empty settings capture cannot certify preservation")
    raw = (path / "dumpsys_appwidget.txt").read_text(encoding="utf-8")
    if "Widgets:" not in raw or "Providers:" not in raw:
        fail("Incomplete appwidget capture")
    status = read_json(path / "status.json")
    if status.get("rotation") is None or "settings" not in status:
        fail("Display baseline unavailable")
    launcher = (status.get("launcher") or "").split("/")[0]
    if not launcher or not (status.get("focus") or "").startswith(launcher + "/"):
        fail("Baseline/restored screen must be the same visible launcher page")
    if len(widget["hosts_on_screen"]) != len(widget["widgets"]):
        fail("All existing Clocky widgets must be visible for geometry verification")
    if not (path / "screen.png").read_bytes().startswith(b"\x89PNG"):
        fail("Screenshot baseline unavailable")
    # Ignore changing clock text; compare geometry, IDs and view classes of Clocky
    # descendants. Screenshots remain available for appearance review.
    from . import ui
    ns, screen = ui.parse_hierarchy((path / "ui.xml").read_text(encoding="utf-8"))
    geometry = [(n.rid, n.cls, n.bounds) for n in ui.visible(ns, screen)
                if n.pkg == C.PACKAGE or (n.is_host and any(ui.host_name(c) == (n.desc or n.cls)
                                                         and c.pkg == C.PACKAGE for c in ns))]
    return {"widget_ids": sorted(w["id"] for w in widget["widgets"]),
            "settings_sha256": hashlib.sha256(prefs.encode("utf-8")).hexdigest(),
            "geometry": geometry, "hosts": widget["hosts_on_screen"],
            "display_settings": status["settings"], "rotation": status["rotation"],
            "launcher": launcher}


class Transaction:
    def __init__(self, lease, serial, repo, restore_script=None, runner=managed_process.run):
        self.lease, self.serial, self.repo = lease, serial, Path(repo).resolve()
        self.restore_script, self.runner = restore_script, runner
        self.env = dict(os.environ, CLOCKY_BROKER_ROOT=str(lease.broker.root),
                        CLOCKY_SERIAL=serial, CLOCKY_DEVICE_IDENTITY=lease.identity,
                        CLOCKY_LEASE_SESSION=lease.session, CLOCKY_LEASE_TOKEN=lease.access_token,
                        CDEV_UTF8="1")
        self.baseline = lease.broker.status(lease.identity).get("baseline")
        if self.baseline:
            self.env["CLOCKY_BASELINE_DIR"] = self.baseline["path"]
        self.errors = []

    def cdev(self, *args):
        result = self.runner([sys.executable, str(self.repo / "tools/device/cdev.py"),
                              *args, "--serial", self.serial, "--json"],
                             env=self.env, cwd=str(self.repo), timeout=360)
        try:
            body = json.loads(result.stdout.decode("utf-8"))
        except (ValueError, UnicodeError):
            fail("cdev %s did not return valid JSON" % args[0])
        if result.returncode != 0 or not body.get("ok"):
            fail("cdev %s failed: %s" % (args[0], body.get("error", result.stderr.decode("utf-8", "replace"))))
        return body["result"]

    def collect(self, label, baseline=None):
        args = ["collect", label]
        if baseline:
            args += ["--compare-to", baseline, "--expect-same", "all"]
        result = self.cdev(*args)
        path = (self.repo / result["dir"]).resolve()
        validate_bundle(path, self.lease.identity)
        return str(path)

    def start(self):
        owner = self.lease.broker.status(self.lease.identity)["owner"]
        if owner.get("needs_baseline") and not self.baseline:
            fail("Previous session failed before baseline capture; explicit reviewed --baseline required for recovery")
        installed = self.cdev("inventory")
        # On recovery preserve the original baseline AND original saved settings;
        # never bless the dirty state by collecting a replacement baseline.
        if pending_restore(load_state(str(self.lease.build_dir))) and not self.baseline:
            fail("Pending settings without an original baseline; investigate before bootstrap")
        self.cdev("prepare")
        if not self.baseline:
            path = self.collect("broker-baseline")
            self.baseline = {"path": path, "build_dir": str(self.lease.build_dir),
                             "installed": installed, "metadata": validate_bundle(path, self.lease.identity)}
            self.lease.broker.update(self.lease.identity, self.lease.session, self.lease.access_token, baseline=self.baseline)
        self.env["CLOCKY_BASELINE_DIR"] = self.baseline["path"]
        self.lease.ready()
        self.env["CLOCKY_LEASE_TOKEN"] = self.lease.token

    def finish(self, test_rc):
        evidence = {"session": self.lease.session, "clean": False, "test_exit_code": test_rc,
                    "finished_at": time.time(), "baseline": self.baseline, "errors": self.errors}
        if getattr(self, "recovery_reason", None):
            evidence["recovery_reason"] = self.recovery_reason
        self.lease.begin_release()
        self.env["CLOCKY_LEASE_TOKEN"] = self.lease.cleanup

        def attempt(name, fn):
            try:
                return fn()
            except Exception as e:
                self.errors.append({"step": name, "error": str(e)})
                return None

        if self.restore_script:
            def restore_hook():
                r = self.runner([sys.executable, str(Path(self.restore_script).resolve())],
                                env=self.env, cwd=str(self.repo), timeout=300)
                if r.returncode:
                    fail("Restoration script failed (exit %s)" % r.returncode)
            attempt("restore-script", restore_hook)
        attempt("restore", lambda: self.cdev("restore"))
        if self.baseline:
            restored = attempt("collect/compare", lambda: self.collect("broker-restored", self.baseline["path"]))
            if restored:
                evidence["restored"] = restored
                def geometry_check():
                    before = validate_bundle(self.baseline["path"], self.lease.identity)
                    after = validate_bundle(restored, self.lease.identity)
                    if before != after:
                        fail("Widget geometry/settings/screen baseline differs after restoration")
                attempt("baseline-match", geometry_check)
        else:
            self.errors.append({"step": "baseline", "error": "No complete original baseline"})
        attempt("finish", lambda: self.cdev("finish"))
        installed = attempt("inventory", lambda: self.cdev("inventory"))
        evidence["installed"] = installed
        evidence["apk_policy"] = "Current APK retained; no automatic downgrade. Source commit may be UNKNOWN."
        if pending_restore(load_state(str(self.lease.build_dir))):
            self.errors.append({"step": "pending-restore", "error": "PENDING RESTORE remains"})
        evidence["clean"] = not self.errors
        atomic_json(self.lease.build_dir / "handoff.json", evidence)
        self.lease.complete(evidence)
        return evidence
