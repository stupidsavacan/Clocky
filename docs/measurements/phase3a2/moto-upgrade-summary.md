# cdev evidence: pr49-upgrade

- device: moto g13, Android 14 (API 34), locale ja-JP, 280 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-08 10:50:27 (debuggable)
- installed APK sha256: `8495e09b5cb9b6caf69ce0ac1facde9ee8f40fd4fc36b909eb8ee05eb65e9eb1`
- repo: `c5a14b7167` on branch `verify/moto-pr49`
- launcher: com.motorola.launcher3
- session s-20261008-104435-ZY22GSDPFW, captured 2026-10-08T10:50:56+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 23 | digital | com.motorola.launcher3 | Card (est.) | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (10:44:33, 379s) | 182 relevant line(s)
CRASH  none
ANR    none
PROC   10:50:27 am_kill: [0,23820,com.stupidsavacan.clocky,900,stop com.stupidsavacan.clocky due to installPackageLI]
PROC   10:50:27 am_proc_start: [0,24480,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
ACT    10:50:27 wm_finish_activity: [0,117522100,184,com.stupidsavacan.clocky/com.android.deskclock.DeskClock,proc died without state saved]
LAUNCH none
WIDGET none
other  179 line(s) (see logcat_clocky.txt)
```

## Files (local only, may contain personal data: do not attach except this summary)

- `clocky_widget_settings.xml`
- `compare.json`
- `dumpsys_activity_top.txt`
- `dumpsys_appwidget.txt`
- `dumpsys_window.txt`
- `launcher_dumpsys.txt`
- `logcat_clocky.txt`
- `logcat_crash.txt`
- `logs_summary.txt`
- `meta.json`
- `screen.png`
- `screen_small.png`
- `status.json`
- `summary.md`
- `summary.txt`
- `ui.txt`
- `ui.xml`
- `widget.json`

## Compared to baseline

- settings: SAME
- widgets: SAME
