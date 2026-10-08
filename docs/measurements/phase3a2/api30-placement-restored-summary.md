# cdev evidence: placement-restored

- device: sdk_gphone_x86_64, Android 11 (API 30), locale en-US, 440 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-07 23:56:02 (debuggable)
- installed APK sha256: `8495e09b5cb9b6caf69ce0ac1facde9ee8f40fd4fc36b909eb8ee05eb65e9eb1`
- repo: `0aa4e5d052` on branch `feat/phase3a2-responsive-render`
- launcher: com.google.android.apps.nexuslauncher
- session s-20261008-090311-emulator-5562, captured 2026-10-08T09:11:51+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 5 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |
| 7 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (09:02:27, 519s) | 185 relevant line(s)
CRASH  none
ANR    none
PROC   09:04:08 am_proc_start: [0,28465,10169,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}]
ACT    (40 other lifecycle line(s) hidden; see logcat_clocky.txt)
ACT    09:04:08 wm_create_activity: [0,232225353,29,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,android.appwidget.action.APPWIDGET_CONFIGURE,NULL,NULL,0]
ACT    09:04:10 wm_on_create_called: [232225353,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performCreate]
ACT    09:04:23 wm_finish_activity: [0,232225353,29,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,app-request]
ACT    09:04:24 wm_on_destroy_called: [232225353,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performDestroy]
ACT    09:05:45 wm_create_activity: [0,189076528,30,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,android.appwidget.action.APPWIDGET_CONFIGURE,NULL,NULL,268435456]
ACT    09:05:46 wm_on_create_called: [189076528,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performCreate]
ACT    09:06:34 wm_finish_activity: [0,189076528,30,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,app-request]
ACT    09:06:35 wm_on_destroy_called: [189076528,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performDestroy]
ACT    ... 12 more
LAUNCH none
WIDGET 09:02:41 WidgetPreviewLoader: Can't load widget preview drawable 0x7f080079 for provider: ComponentInfo{com.stupidsavacan.clocky/com.android.alarmclock.AnalogAppWidgetProvider}
WIDGET 09:02:41 WidgetPreviewLoader: Can't load widget preview drawable 0x7f08007d for provider: ComponentInfo{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}
WIDGET 09:04:08 ActivityManager: Start proc 28465:com.stupidsavacan.clocky/u0a169 for broadcast {com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}
other  121 line(s) (see logcat_clocky.txt)
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

## Compared to placement-baseline

- settings: SAME
- widgets: SAME
