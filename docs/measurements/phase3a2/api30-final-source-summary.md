# cdev evidence: final-source-ticking

- device: sdk_gphone_x86_64, Android 11 (API 30), locale en-US, 440 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-07 23:44:16 (debuggable)
- installed APK sha256: `179ed57e293a34490db434e24b924c4ae4b2d16382da449361fb47954b937bf2`
- repo: `7b7ab11bbd` (dirty) on branch `feat/phase3a2-responsive-render`
- launcher: com.google.android.apps.nexuslauncher
- session s-20261008-084339-emulator-5562, captured 2026-10-08T08:47:14+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 5 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |
| 7 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (08:42:55, 214s) | 71 relevant line(s)
CRASH  none
ANR    none
PROC   08:44:16 am_kill: [0,22308,com.stupidsavacan.clocky,700,stop com.stupidsavacan.clocky due to installPackageLI]
PROC   08:44:17 am_proc_start: [0,26137,10169,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
PROC   08:45:25 am_kill: [0,26137,com.stupidsavacan.clocky,915,kill background]
ACT    (1 other lifecycle line(s) hidden; see logcat_clocky.txt)
ACT    08:44:16 wm_finish_activity: [0,156370778,28,com.stupidsavacan.clocky/.widget.digital.ResponsiveAuditActivity,proc died without state saved]
LAUNCH none
WIDGET 08:45:02 WidgetPreviewLoader: Can't load widget preview drawable 0x7f08007d for provider: ComponentInfo{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}
WIDGET 08:45:02 WidgetPreviewLoader: Can't load widget preview drawable 0x7f080079 for provider: ComponentInfo{com.stupidsavacan.clocky/com.android.alarmclock.AnalogAppWidgetProvider}
other  64 line(s) (see logcat_clocky.txt)
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

## Compared to final-source-baseline

- settings: SAME
- widgets: SAME
