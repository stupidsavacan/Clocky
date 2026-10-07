# cdev evidence: after

- device: moto g13, Android 14 (API 34), locale ja-JP, 280 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-08 07:07:40 (debuggable)
- installed APK sha256: `504294c7e5b494f8f23ae24909332b361fb4b66dcb9a9bb420c017aa65e11ee4`
- repo: `15da85ae67` (dirty) on branch `feat/phase3a1-responsive-model`
- launcher: com.motorola.launcher3
- session s-20261008-070548-ZY22GSDPFW, captured 2026-10-08T07:07:58+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 23 | digital | com.motorola.launcher3 | Card (est.) | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (07:05:46, 129s) | 244 relevant line(s)
CRASH  none
ANR    none
PROC   07:05:52 am_proc_start: [0,4703,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
PROC   07:07:40 am_kill: [0,4703,com.stupidsavacan.clocky,955,stop com.stupidsavacan.clocky due to installPackageLI]
PROC   07:07:41 am_proc_start: [0,5163,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
ACT    none
LAUNCH none
WIDGET none
other  241 line(s) (see logcat_clocky.txt)
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
