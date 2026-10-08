# cdev evidence: committed-source-final

- device: sdk_gphone_x86_64, Android 11 (API 30), locale en-US, 440 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-07 23:56:02 (debuggable)
- installed APK sha256: `8495e09b5cb9b6caf69ce0ac1facde9ee8f40fd4fc36b909eb8ee05eb65e9eb1`
- repo: `aa44ce66b4` on branch `feat/phase3a2-responsive-render`
- launcher: com.google.android.apps.nexuslauncher
- session s-20261008-085636-emulator-5562, captured 2026-10-08T08:58:06+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 5 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |
| 7 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (08:55:53, 88s) | 56 relevant line(s)
CRASH  none
ANR    none
PROC   08:56:02 am_proc_start: [0,27164,10169,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
PROC   08:56:30 am_kill: [0,27164,com.stupidsavacan.clocky,915,kill background]
ACT    none
LAUNCH none
WIDGET none
other  54 line(s) (see logcat_clocky.txt)
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

## Compared to committed-source-baseline

- settings: SAME
- widgets: SAME
