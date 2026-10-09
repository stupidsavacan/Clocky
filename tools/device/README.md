# cdev — Clocky device layer

A small Python CLI (stdlib only; Pillow optional) that replaces hand-built `adb` command lines when verifying
Clocky on a real device or emulator. Developer tooling only: it never touches the app source.

```
python tools/device/cdev.py <command> [options] [--json] [--serial S]
```

Use `python` (not `python3`, which is a Microsoft Store stub on this machine). Works from PowerShell 5.1 and Git Bash.
Output goes to `build/device/` (git-ignored). Nothing is ever written to `/sdcard`; device temp files are
`/data/local/tmp/clocky-dev-*` and are deleted right after use.

## Device Broker v2 — shared Windows worktrees

Read [DEVICE_BROKER_V2.md](../../docs/architecture/DEVICE_BROKER_V2.md) for architecture,
recovery, limitations and the Issue #51 migration procedure. **Until this implementation
is merged and every participating checkout is updated, agents must not assume the
broker is available.** This PR's host tests do not operate the moto or perform migration.

After migration, normal device work uses `lease run` or a supervised `lease hold`.
Code editing/build/unit tests do not acquire the phone. A/B can work concurrently;
only request immediately before one finite device unit, then return promptly. Later
units requeue. Waiting reads the local persistent FIFO; GitHub comments/replies and
Owner approval are not involved in normal clean handoff.

The shared default is `%LOCALAPPDATA%\Clocky\device-broker\` for the same Windows
account. `CLOCKY_BROKER_ROOT` is an optional **shared local** override; all agents
must agree on it. Never use separate roots to bypass another holder. Once enabled,
every device-touching cdev command requires a registered serial and valid lease
identity/session/token; even screenshot/status/log reads are refused without them.
`lease status` and local `compare` require no device access. Before enablement, the
unconfigured v1 behavior/test suite remains available.

One-time migration (operator action **after merge**, not performed in this task):

```powershell
$deviceId = '<physical-ro.serialno>'
$usbSerial = '<known-USB-adb-serial>'
# Offline registration creates DIRTY / RECOVERY_REQUIRED, never FREE.
python tools/device/cdev.py lease register --identity $deviceId --transport $usbSerial
python tools/device/cdev.py lease status --identity $deviceId --json
# Only after stopping old clients, reconciling Owner/legacy sessions, and placing
# all existing Clocky widgets on the visible launcher page:
python tools/device/cdev.py lease recover --identity $deviceId --serial $usbSerial --agent operator --acknowledge-quiescent --reason 'Owner reconciled legacy sessions; bootstrap baseline' -- python tools/device/examples/broker_smoke.py
```

The shared ADB server must already be running with the selected ADB client protocol.
Broker-managed commands do not start/restart it. Missing server, protocol mismatch,
custom server endpoints, unreadable installed APK/settings or incomplete evidence
stop verification. Do not fix these failures by resetting the phone/server.

Independent agent A, from its own updated worktree:

```powershell
python tools/device/cdev.py lease run --identity $deviceId --serial $usbSerial --agent agent-A --duration 180 --wait-timeout 900 --credential-file build/device/agent-A-unit1.json -- python tools/device/examples/broker_smoke.py
```

Independent agent B, from its own updated worktree, can issue this concurrently:

```powershell
python tools/device/cdev.py lease run --identity $deviceId --serial $usbSerial --agent agent-B --duration 180 --wait-timeout 900 --credential-file build/device/agent-B-unit1.json -- python tools/device/examples/broker_smoke.py
```

The first request owns the phone; the other waits. A clean return automatically
allows the FIFO head to acquire on its next local poll. A completed failed test can
still return clean if restoration verifies; test failure remains recorded. Exceptions,
incomplete evidence or failed restoration leave DIRTY, blocking subsequent acquisition.
No timeout or process disappearance grants another ordinary owner.

For multiple interactive commands, keep a finite supervisor in terminal 1:

```powershell
python tools/device/cdev.py lease hold --identity $deviceId --serial $usbSerial --agent agent-A --duration 300 --credential-file build/device/agent-A-unit2.json
```

Wait for terminal 1's `Lease ACTIVE` message. In terminal 2 of the same worktree:

```powershell
$leaseCred = Get-Content build/device/agent-A-unit2.json -Raw | ConvertFrom-Json
$env:CLOCKY_BROKER_ROOT = $leaseCred.root
$env:CLOCKY_SERIAL = $leaseCred.serial
$env:CLOCKY_DEVICE_IDENTITY = $leaseCred.identity
$env:CLOCKY_LEASE_SESSION = $leaseCred.session
$env:CLOCKY_LEASE_TOKEN = $leaseCred.token
python tools/device/cdev.py status
python tools/device/cdev.py widget --locate
# Optional in-place install only with verified build provenance (full source SHA):
# python tools/device/cdev.py install path/to/tested.apk --source-commit <40-character-source-commit>
# Optional finite GUI/recording; close GUI to let other cdev commands proceed:
# python tools/device/cdev.py project --duration 60 --record build/device/unit2.mkv
python tools/device/cdev.py lease release --identity $deviceId --credential-file build/device/agent-A-unit2.json
```

`release` asks the hold supervisor to restore/compare/finish; it does not directly
grant FREE. Wait for its clean result. Repeated release is refused. `cdev finish`
alone does not return a broker lease. Use a **new credential filename/session for
each unit**; used/cancelled credentials are retired. Tokens are local bearer secrets:
do not attach credential files, registry contents or raw evidence to issues/PRs.
The token expires on return even if terminal 2 retains its environment variables.

Detached request/cancellation/resume (no ADB while waiting):

```powershell
python tools/device/cdev.py lease request --identity $deviceId --serial $usbSerial --agent agent-B --credential-file build/device/agent-B-unit2.json
python tools/device/cdev.py lease status --identity $deviceId
# Either cancel before acquisition:
# python tools/device/cdev.py lease cancel --identity $deviceId --credential-file build/device/agent-B-unit2.json
# Or start the waiting supervisor for that exact request:
python tools/device/cdev.py lease run --identity $deviceId --serial $usbSerial --agent agent-B --request-file build/device/agent-B-unit2.json -- python tools/device/examples/broker_smoke.py
```

Broker evidence and cdev pending settings are shared under the registry's
`devices/<identity-hash>/leases/<session>/`; another worktree with the same credentials
sees the same session. `handoff.json`, test stdout/stderr, screenshots and raw data
stay local. Continue attaching **only collect's `summary.md`**, whose broker session
ID contains no physical serial. Inventory explicitly reports installed source UNKNOWN
unless recorded provenance matches the actual installed bytes.

Widget-changing tests need `--restore-script path/to/restore_unit.py`. This finite
Python script inherits cleanup credentials and `CLOCKY_BASELINE_DIR`; use cdev to
restore original widget configuration/placement, then leave the original launcher
page visible. cdev restore itself restores rotation/stay-awake, **not app widget
settings**. No restore script is needed for the provided preservation-only smoke
script. The broker independently compares original/restored settings, widget IDs,
geometry and display state, runs finish and checks no PENDING RESTORE before release.
It cannot automatically restore arbitrary widget edits or certify pixel equality.

Keep the current installed APK when a safe baseline return is unavailable; never
downgrade automatically. The handoff records the remaining SHA/source commit and
widget/settings preservation result. Do not delete existing widgets, uninstall,
pm clear, force-stop, restart the ADB server, rewrite launcher data or reset settings.

On DIRTY, ordinary commands stop. After reviewing preserved evidence and stopping
all old/external clients, an explicit operator recovery runs a finite repair command
against the **original** saved baseline, with the same final restoration checks:

```powershell
python tools/device/cdev.py lease recover --identity $deviceId --serial $usbSerial --agent operator --acknowledge-quiescent --reason 'Reviewed failed handoff; restore original baseline' --restore-script path/to/restore_unit.py -- python path/to/recovery_check.py
```

Recovery cannot steal a live owner. Failed recovery stays DIRTY. If the original
baseline was never captured, a reviewed original `--baseline <bundle>` is required;
it cannot replace an existing baseline. Preserve corrupt/missing enabled registry
files for diagnosis; deleting lock/state files is not a handoff/recovery mechanism.

This is cooperative enforcement. Same-user external programs, older cdev checkouts,
edited registry/root paths and direct ADB/scrcpy are not blocked by OS permissions.
Only use cdev and its managed projection/recording route. All participating agents
must adopt it; no claim of mandatory OS-wide access denial is made.

## Basic flow

The following v1 command recipes run inside a broker lease after migration. Examples
that add/remove widgets are historical v1 recipes; they do **not** authorize deleting
existing widgets or expanding the current moto/Phase 5+ scope.

```
cdev prepare            # wake, pin device, create session + log mark
cdev inspect            # screen summary + node table + screenshot
cdev tap "LABEL"        # semantic tap (exit 2 if ambiguous, 4 if not found)
cdev widget --locate    # Clocky widget ids / host / size class / settings / checks
cdev logs               # Clocky-relevant log lines since prepare
cdev collect after-save # evidence bundle (attach only summary.md to PRs)
cdev finish             # restore device settings, close session, clean temp files
```

## Command cheat sheet

| Command | What it does |
|---|---|
| `devices` | list devices, transport, which one would be selected |
| `status` | one-screen state: launcher, rotation, keyguard, IME, Clocky version, log-buffer age, `PENDING RESTORE` |
| `prepare [--stay-awake] [--home]` | wake the screen, pin the device, start a session, record the `prepare` log mark |
| `mark NAME` | named time boundary for `logs --since mark:NAME` |
| `inspect [--all] [--no-screenshot] [--wait-for LABEL] [--diff]` | uiautomator dump + screenshot (`screen.png`, `screen_small.png`); `--diff` prints only what changed since the previous inspect |
| `tap [LABEL] [--text/--desc/--id/--class/--package] [--contains] [--index N] [--scroll] [--wait S] [--xy X Y]` | tap by selector |
| `long-press` | same selectors, `--ms 1500` |
| `drag` | press-hold-move-release: `--to LABEL` / `--to-id ID` / `--to-xy X Y`, `--hold-ms`, `--steps`, `--move-ms` (API 30+) |
| `swipe X1 Y1 X2 Y2` / `scroll up\|down [--times N] [--in X]` | swipes |
| `key back\|home\|recents\|wakeup\|enter\|KEYCODE_*` | key events (power/sleep keys refused) |
| `wait [SELECTOR] [--gone] [--activity SUBSTR] [--timeout S]` | wait for a node / focus change |
| `widget [--id N] [--locate] [--raw]` | Clocky widget state |
| `widget --ticking [--timeout S] [--index N] [--no-process]` | prove the on-screen widget clock advances (exit 7 if not); `--no-process` also requires that no Clocky process exists |
| `proc [status]` / `proc kill [--hard] [--timeout S]` | Clocky pid list / `am kill` (refuses while Clocky is in the foreground; `--hard` = `run-as kill -9` on debuggable builds); records mark `proc-kill` |
| `logs [--since prepare\|mark:NAME\|30s\|all-buffer] [--raw]` | Clocky log summary (never `logcat -c`) |
| `rotate portrait\|landscape\|reverse-*\|auto` | fixes rotation; original values saved once |
| `restore` | write back the saved settings (verified with `settings get`) |
| `install [APK]` | `adb install -r` (never uninstalls); records sha256 + versionCode |
| `launch app` / `launch config --widget-id N` | start Clocky / the config Activity of an existing widget (waits up to `--wait 5` s for focus) |
| `launch activity .pkg.Class [--ei K V] [--es K V] [--ez K true\|false]` | start any Activity class inside the Clocky APK (debug probes); other packages exit 6 |
| `collect LABEL [--no-launcher-dump] [--compare-to LABEL2 [--expect-same settings\|all]]` | evidence bundle, tolerant of partial failure; optionally compared with an earlier bundle |
| `compare A B [--only settings\|widgets\|meta\|logs] [--expect-same settings\|all]` | diff two bundles (label, step dir, `<session>/<label>` or path); no device needed |
| `adb -- <args>` | raw adb with serial + safety guard |
| `cleanup` / `finish` | remove leftover temp files / restore + close session |

## Exit codes

| Code | Meaning | What to do |
|---|---|---|
| 0 | ok | |
| 2 | usage error or **ambiguous** (several nodes match, MSYS-mangled path, keyguard showing) | read the candidates; add `--index N` / narrower selector; do not guess |
| 3 | device selection failure (none, several, unauthorized, pinned device missing, identity mismatch) | pass `--serial`, accept the USB prompt, or `prepare --serial` |
| 4 | UI target not found / dump failure | `inspect`; use `--scroll`, `--wait`, `--contains`, or `--xy` from the screenshot |
| 5 | adb or command failure | read the message; do not retry blindly |
| 6 | refused by a safety boundary | ask the human to do it |
| 7 | **check failed**: the observation worked but an explicitly requested condition is false (`--expect-same`, `widget --ticking`) | the evidence is saved; read it, do not retry blindly |

`--json` prints exactly one object: `{"ok":true,"command":..,"device":{..},"result":{..},"warnings":[..],"artifacts":[..]}` or
`{"ok":false,"command":..,"error":{"code","message","hint","candidates"}}`. ASCII-escaped, so it is safe in any console.

## Device selection

1. `--serial S`, else `CLOCKY_SERIAL`, else `ANDROID_SERIAL` (must be connected, else exit 3).
2. Else the device pinned by the last `prepare` (matched by `ro.serialno`, USB preferred over Wi-Fi; a transport change warns).
3. If the pinned device is gone but another is attached: exit 3 (never silently switches).
4. No pin: exactly one device identity is used; more than one (even physical + emulator) is exit 3.
Only `prepare` pins. `rotate`/`restore` refuse to run on a device different from the one whose settings are pending.

## Safety boundaries

Automatic: read-only queries, `KEYCODE_WAKEUP`, `/data/local/tmp/clocky-dev-*`.
Only when you run the command: taps/gestures/keys, `launch`, `install -r`, `rotate`, `prepare --stay-awake|--home`.
Never implemented / refused (exit 6): uninstall, `pm clear`, `logcat -c/-G`, `settings put|delete` outside
{`system/accelerometer_rotation`, `system/user_rotation`, `global/stay_on_while_plugged_in`}, reboot, root, remount,
`rm` outside `clocky-dev-*`, `install -d`, unlocking the device. The raw `adb --` denylist is a developer safety guard,
not a security sandbox.

`am force-stop` is **not** used by cdev and should not be used raw either: it puts Clocky in the stopped state, which
cancels AlarmManager alarms and blocks broadcasts, invalidating widget-ticking and alarm checks. `proc kill` uses `am kill`;
`--hard` uses `run-as com.stupidsavacan.clocky kill -9 <pid>` only for debuggable builds and only for pids it just read.
The tool never unlocks the device: with a keyguard showing `prepare` stops (exit 2) and asks you to unlock manually.

## Evidence & privacy

`build/device/sessions/<session>/NNNN-<label>/`. Raw logcat, screenshots, UI dumps and `dumpsys` output can contain
other apps' data and notifications: they stay local. **Attach only `summary.md`** (Clocky-filtered, no serial, no raw logs).
The device log buffers are ~256 KiB (about 10 minutes on a moto g13): run `logs`/`collect` right after acting;
gaps are reported as `buffer ... rolled over`.

## Recipes (moto g13 Motorola Launcher3 / Pixel Launcher)

```
# add Digital widget (moto launcher3)
cdev prepare --home
cdev mark add1
cdev long-press --xy 150 200                       # empty home area (pick one from `inspect`)
cdev tap "ウィジェット"                              # menu entry (label is localized; check `inspect`)
cdev tap Clocky --id app_title --scroll            # expand Clocky in the picker
cdev drag --id widget_preview_container --index 1 --to-xy 360 1000   # index 1 = Digital (check the dp row)
cdev wait --activity DigitalWidgetConfigActivity
cdev tap --id clocky_widget_save --scroll          # save   (cancel instead: cdev key back)
cdev widget --locate
cdev logs --since mark:add1

# resize (moto: handles have ids; Pixel Launcher: handles have no ids -> coordinates from `inspect --all`)
cdev long-press --id/--xy <widget>                 # shows the resize frame
cdev drag --id widget_resize_bottom_handle --to-xy 360 1100 --hold-ms 300
cdev key home; cdev widget --locate

# Studio: change and save a setting (no coordinates)
cdev launch config --widget-id N
cdev tap --id clocky_tune_detail --wait 5
cdev tap 動作 --contains --scroll          # the tab row scrolls horizontally if needed
cdev tap 12時間 --scroll
cdev tap --id clocky_studio_save; cdev key home
cdev inspect --diff                        # only what changed since the previous inspect

# process-free ticking proof
cdev key home; cdev proc kill [--hard]; cdev widget --ticking --no-process; cdev collect post-kill

# restore proof (settings byte-identical, or JSON paths that differ)
cdev collect baseline ...  cdev collect restored --compare-to baseline --expect-same settings

# reconfigure: tap the pencil shown in resize mode
cdev tap --id widget_reconfigure_button
# rotate and come back
cdev rotate landscape; cdev collect landscape; cdev restore
# delete: drag the widget to the launcher's remove target (see `inspect` while dragging), wait for undo, then `cdev widget`
```

## Findings (U1–U6)

| | Result |
|---|---|
| U1 | **`dumpsys appwidget` has no `options=` line** for placed widgets on moto g13 (API 34) and the API 30 emulator. `widget` therefore reports `options: unavailable`. With `--locate` and exactly one Clocky widget + one host view on screen, the size class is **estimated** from the launcher host-view bounds (shown as `Strip (est.)`/`Card (est.)`). The rule is a tool replica of `SizeClassResolver` (0 < minHeight < 100dp → Strip), not the app's own decision. Fix candidate: a debug-only log in the app (out of scope for cdev). |
| U2 | Launcher host views contain Clocky RemoteViews with package `com.stupidsavacan.clocky`; ids use the `com.stupidsavacan.clocky:id/` prefix (selectors match by `:id/<name>` suffix). Hosts are identified by their descendants' package, so older builds work too. |
| U3 | `logcat -v epoch,uid` works on API 30/34 (uid column is numeric, e.g. 10232). The tool falls back to `-v epoch` if unsupported. Events: `wm_create_activity` (API 30) / `wm_on_create_called`, `wm_finish_activity`, `am_proc_start`. The moto launcher logs `was not returned` on cancel; Pixel Launcher (API 30) does not, use `wm_finish_activity` without a following save. |
| U4 | `input motionevent` drag works on moto g13 (picker → home) and API 30 (resize handles). |
| U5 | Wi-Fi serial format is handled by regex (`ip:port`, `adb-*._adb-tls-connect._tcp`) and unit-tested; not exercised on hardware (no wireless session available). |
| U6 | Git Bash decodes the console as UTF-8 while Python on Windows writes cp932; `cdev.py` switches stdout to UTF-8 when `MSYSTEM` is set (or `CDEV_UTF8=1`). Non-encodable characters are escaped, never crash. |

Verification notes (moto g13, Motorola Launcher3):
- The smallest launcher resize (1 row) is ~122 dp inner height, so Strip (<100 dp) is **not reachable** by resizing on this launcher; the 4x2→1 row→4x2 round trip works and reports `Card (est.)`. Strip was confirmed on the API 30 emulator (Pixel Launcher: ~97 dp).
- Launcher3 resize handles have ids (`widget_resize_*_handle`, `widget_reconfigure_button`); Pixel Launcher handles have none (use `--xy` from `inspect --all`).
- Remove a widget by dragging it to the "削除" target at the top-left (≈ `--to-xy 170 175` at 720x1600), then wait a few seconds.
- UI dumps fail while a finger is held down (mid-drag); `inspect` saves a screenshot instead (`NNNN-inspect-failed/`).
- Right after `drag`/`launch`, the config screen may still be inflating: use `tap ... --scroll` (waits up to 3 s) or `--wait 5`.
- The config Save button can sit in a ~55 px scroll viewport (landscape); `--scroll` handles this with long swipes, and nodes clipped by a scroll viewport are never offered as tap targets.
- Multi-page drops (hold at the screen edge) are not wrapped; use `cdev adb -- shell "input motionevent ..."` for that rare case.

Other notes: the config Activity forces landscape on moto g13 while open (rotation 1); the installed APK on the moto
may be an older build (settings schema 1 → `schema!=2` check). Run `cdev install` after building to test schema 2.

## Not in v1

sendevent gestures for API < 30 (drag exits 6 there; tap/inspect/widget/logs/collect still work), Wi-Fi pairing,
background logcat streaming, high-level `widget add` recipes, automatic evidence cleanup, CI wiring.

## Tests

```
python -m unittest discover -s tools/device/tests -t tools/device
```
Pure unit tests, no device needed, using sanitized captures in `tools/device/tests/fixtures`.

## Integration checklist (moto g13 API34 current host; other devices Phase 5+)

Current owner scope is [moto personal use](../../docs/product/VERIFICATION_SCOPE.md). Non-moto device/emulator verification is deferred to optional Phase 5+ and should not be resumed without an owner request. The checklist below is a tool-integration recipe, not a request to repeat accepted PR49 checks or delete existing widgets. Preserve existing state and restore temporary changes.

prepare/status · inspect · open widget picker · drag Clocky Digital to home · config opens (`wait --activity`) ·
save · `widget` (id, host, settings) · add another + `key back` (cancel) · resize 4x2→4x1→4x2 · `rotate landscape` →
`collect` → `restore` · launcher reconfigure keeps id · delete widget (widget 0, settings `{}`) · `collect final` ·
`adb shell ls /data/local/tmp` shows no `clocky-dev-*` · two devices → exit 3 · duplicate label → exit 2 · denylisted raw adb → exit 6.
