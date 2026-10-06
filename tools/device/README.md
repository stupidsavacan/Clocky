# cdev — Clocky device layer

A small Python CLI (stdlib only; Pillow optional) that replaces hand-built `adb` command lines when verifying
Clocky on a real device or emulator. Developer tooling only: it never touches the app source.

```
python tools/device/cdev.py <command> [options] [--json] [--serial S]
```

Use `python` (not `python3`, which is a Microsoft Store stub on this machine). Works from PowerShell 5.1 and Git Bash.
Output goes to `build/device/` (git-ignored). Nothing is ever written to `/sdcard`; device temp files are
`/data/local/tmp/clocky-dev-*` and are deleted right after use.

## Basic flow

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
| `inspect [--all] [--no-screenshot] [--wait-for LABEL]` | uiautomator dump + screenshot (`screen.png`, `screen_small.png`) |
| `tap [LABEL] [--text/--desc/--id/--class/--package] [--contains] [--index N] [--scroll] [--wait S] [--xy X Y]` | tap by selector |
| `long-press` | same selectors, `--ms 1500` |
| `drag` | press-hold-move-release: `--to LABEL` / `--to-id ID` / `--to-xy X Y`, `--hold-ms`, `--steps`, `--move-ms` (API 30+) |
| `swipe X1 Y1 X2 Y2` / `scroll up\|down [--times N] [--in X]` | swipes |
| `key back\|home\|recents\|wakeup\|enter\|KEYCODE_*` | key events (power/sleep keys refused) |
| `wait [SELECTOR] [--gone] [--activity SUBSTR] [--timeout S]` | wait for a node / focus change |
| `widget [--id N] [--locate] [--raw]` | Clocky widget state |
| `logs [--since prepare\|mark:NAME\|30s\|all-buffer] [--raw]` | Clocky log summary (never `logcat -c`) |
| `rotate portrait\|landscape\|reverse-*\|auto` | fixes rotation; original values saved once |
| `restore` | write back the saved settings (verified with `settings get`) |
| `install [APK]` | `adb install -r` (never uninstalls); records sha256 + versionCode |
| `launch app` / `launch config --widget-id N` | start Clocky / the config Activity of an existing widget |
| `collect LABEL [--no-launcher-dump]` | evidence bundle, tolerant of partial failure |
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

## Integration checklist (moto g13 API 34 required; emulator API 30/35 optional)

prepare/status · inspect · open widget picker · drag Clocky Digital to home · config opens (`wait --activity`) ·
save · `widget` (id, host, settings) · add another + `key back` (cancel) · resize 4x2→4x1→4x2 · `rotate landscape` →
`collect` → `restore` · launcher reconfigure keeps id · delete widget (widget 0, settings `{}`) · `collect final` ·
`adb shell ls /data/local/tmp` shows no `clocky-dev-*` · two devices → exit 3 · duplicate label → exit 2 · denylisted raw adb → exit 6.
