# cdev evidence: restored

- device: sdk_gphone_x86_64, Android 11 (API 30), locale en-US, 440 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-07 15:44:55 (debuggable)
- installed APK sha256: `79d6257d24a2bc46a2143adcc334ba19ec7af022570a77d8c2c47d6e1c6a4f9d`
- repo: `dddd16c7db` (dirty) on branch `spike/3a-size-map`
- launcher: com.google.android.apps.nexuslauncher
- session s-20261008-001636-emulator-5562, captured 2026-10-08T00:46:28+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 5 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |
| 7 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (00:16:35, 1791s) | 939 relevant line(s)
CRASH  00:43:42 AndroidRuntime: Shutting down VM
CRASH  00:43:42 AndroidRuntime: FATAL EXCEPTION: main
CRASH  00:43:42 AndroidRuntime: Process: com.stupidsavacan.clocky, PID: 18806
CRASH  00:43:42 AndroidRuntime: android.view.InflateException: Binary XML file line #43 in com.stupidsavacan.clocky:layout/clocky_digital_widget_split_row: Binary XML file line #43 in com.stup
CRASH  00:43:42 AndroidRuntime: Caused by: android.view.InflateException: Binary XML file line #43 in com.stupidsavacan.clocky:layout/clocky_digital_widget_split_row: Error inflating class and
CRASH  00:43:42 AndroidRuntime: Caused by: android.view.InflateException: Binary XML file line #43 in com.stupidsavacan.clocky:layout/clocky_digital_widget_split_row: Class not allowed to be i
CRASH  00:43:42 AndroidRuntime: 	at android.view.LayoutInflater.failNotAllowed(LayoutInflater.java:895)
CRASH  00:43:42 AndroidRuntime: 	at android.view.LayoutInflater.createView(LayoutInflater.java:819)
CRASH  ... 30 more
ANR    none
PROC   00:17:22 am_proc_start: [0,5522,10169,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
PROC   00:41:41 am_kill: [0,5522,com.stupidsavacan.clocky,900,stop com.stupidsavacan.clocky due to installPackageLI]
PROC   00:41:42 am_proc_start: [0,18576,10169,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
PROC   00:43:41 am_kill: [0,18576,com.stupidsavacan.clocky,935,stop com.stupidsavacan.clocky due to installPackageLI]
PROC   00:43:41 am_proc_start: [0,18806,10169,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
PROC   00:43:43 am_proc_died: [0,18806,com.stupidsavacan.clocky,200,2]
PROC   00:43:48 am_proc_start: [0,18914,10169,com.stupidsavacan.clocky,pre-top-activity,{com.stupidsavacan.clocky/com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity}]
PROC   00:44:55 am_kill: [0,18914,com.stupidsavacan.clocky,900,stop com.stupidsavacan.clocky due to installPackageLI]
PROC   ... 2 more
ACT    (46 other lifecycle line(s) hidden; see logcat_clocky.txt)
ACT    00:18:19 wm_create_activity: [0,234709192,19,com.stupidsavacan.clocky/.widget.digital.SizeSpikeActivity,NULL,NULL,NULL,268435456]
ACT    00:18:19 wm_finish_activity: [0,234709192,19,com.stupidsavacan.clocky/.widget.digital.SizeSpikeActivity,app-request]
ACT    00:18:19 wm_on_create_called: [234709192,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performCreate]
ACT    00:18:19 wm_on_destroy_called: [234709192,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performDestroy]
ACT    00:43:42 wm_create_activity: [0,159305641,20,com.stupidsavacan.clocky/.widget.digital.SizeSpikeActivity,NULL,NULL,NULL,268435456]
ACT    00:43:42 wm_on_create_called: [159305641,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performCreate]
ACT    00:43:42 wm_finish_activity: [0,159305641,20,com.stupidsavacan.clocky/.widget.digital.SizeSpikeActivity,force-crash]
ACT    00:43:48 wm_create_activity: [0,126042641,21,com.stupidsavacan.clocky/.widget.digital.SizeSpikeActivity,NULL,NULL,NULL,268435456]
ACT    ... 19 more
LAUNCH none
WIDGET 00:17:26 ClockySizeDiag: event=onUpdate appWidgetId=5 launcher=com.google.android.apps.nexuslauncher density=2.75 minW=276.0 minH=123.0 maxW=514.0 maxH=210.0 OPTION_APPWIDGET_SIZES=[]
WIDGET 00:17:26 ClockySizeDiag: appWidgetId=5 minPair=276.0x123.0dp ratio=2.2439 candidate=CARD marginStrip=23.00dp marginLarge=-77.00dp marginRatio=0.8439
WIDGET 00:17:26 ClockySizeDiag: appWidgetId=5 maxPair=514.0x210.0dp ratio=2.4476 candidate=LARGE marginStrip=110.00dp marginLarge=10.00dp marginRatio=1.0476
WIDGET 00:17:26 ClockySizeDiag: appWidgetId=5 portraitLike=276.0x210.0dp ratio=1.3143 candidate=SQUARE marginStrip=110.00dp marginLarge=10.00dp marginRatio=-0.0857
WIDGET 00:17:26 ClockySizeDiag: appWidgetId=5 landscapeLike=514.0x123.0dp ratio=4.1789 candidate=CARD marginStrip=23.00dp marginLarge=-77.00dp marginRatio=2.7789
WIDGET 00:17:26 ClockySizeDiag: event=onUpdate appWidgetId=7 launcher=com.google.android.apps.nexuslauncher density=2.75 minW=276.0 minH=123.0 maxW=514.0 maxH=210.0 OPTION_APPWIDGET_SIZES=[]
WIDGET 00:17:26 ClockySizeDiag: appWidgetId=7 minPair=276.0x123.0dp ratio=2.2439 candidate=CARD marginStrip=23.00dp marginLarge=-77.00dp marginRatio=0.8439
WIDGET 00:17:26 ClockySizeDiag: appWidgetId=7 maxPair=514.0x210.0dp ratio=2.4476 candidate=LARGE marginStrip=110.00dp marginLarge=10.00dp marginRatio=1.0476
WIDGET ... 198 more
other  612 line(s) (see logcat_clocky.txt)
WARNING buffer crash rolled over: logs between the mark and 00:43:42 are lost (~1627s); run `cdev logs`/`collect` right after acting
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
