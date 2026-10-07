# cdev evidence: restored

- device: moto g13, Android 14 (API 34), locale ja-JP, 280 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-08 01:22:33 (debuggable)
- installed APK sha256: `0349ebb6bc98eaf1401d506193169ab5232a5aa8dfde010ee1105e27f295cc8c`
- repo: `dddd16c7db` (dirty) on branch `spike/3a-size-map`
- launcher: com.motorola.launcher3
- session s-20261008-012219-ZY22GSDPFW, captured 2026-10-08T01:31:36+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 23 | digital | com.motorola.launcher3 | Card (est.) | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (01:22:17, 556s) | 783 relevant line(s)
CRASH  none
ANR    none
PROC   01:22:33 am_kill: [0,31808,com.stupidsavacan.clocky,900,stop com.stupidsavacan.clocky due to installPackageLI]
PROC   01:22:33 am_proc_start: [0,5177,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
PROC   01:22:47 am_kill: [0,5177,com.stupidsavacan.clocky,975,empty #9]
PROC   01:22:47 am_proc_died: [0,5177,com.stupidsavacan.clocky,975,19]
PROC   01:24:16 am_proc_start: [0,5519,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}]
ACT    (37 other lifecycle line(s) hidden; see logcat_clocky.txt)
ACT    01:24:19 wm_create_activity: [0,153158878,182,com.stupidsavacan.clocky/.widget.digital.SizeSpikeActivity,NULL,NULL,NULL,268435456]
ACT    01:24:19 wm_on_create_called: [153158878,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performCreate,52]
ACT    01:25:41 wm_on_destroy_called: [153158878,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performDestroy,0]
ACT    01:25:41 wm_on_create_called: [153158878,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performCreate,15]
ACT    01:27:37 wm_on_destroy_called: [153158878,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performDestroy,0]
ACT    01:27:38 wm_finish_activity: [0,153158878,182,com.stupidsavacan.clocky/.widget.digital.SizeSpikeActivity,app-request]
ACT    01:27:38 wm_on_create_called: [153158878,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performCreate,200]
ACT    01:27:38 wm_on_destroy_called: [153158878,com.stupidsavacan.clocky.widget.digital.SizeSpikeActivity,performDestroy,0]
ACT    ... 6 more
LAUNCH none
WIDGET 01:24:16 ActivityManager: Start proc 5519:com.stupidsavacan.clocky/u0a232 for broadcast {com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}
WIDGET 01:27:38 ClockySizeDiag: event=probeActivity appWidgetId=23 launcher=com.motorola.launcher3 density=1.75 minW=363.0 minH=281.0 maxW=667.0 maxH=537.0 OPTION_APPWIDGET_SIZES=[363.42856x53
WIDGET 01:27:38 ClockySizeDiag: appWidgetId=23 minPair=363.0x281.0dp ratio=1.2918 candidate=SQUARE marginStrip=181.00dp marginLarge=81.00dp marginRatio=-0.1082
WIDGET 01:27:38 ClockySizeDiag: appWidgetId=23 maxPair=667.0x537.0dp ratio=1.2421 candidate=SQUARE marginStrip=437.00dp marginLarge=337.00dp marginRatio=-0.1579
WIDGET 01:27:38 ClockySizeDiag: appWidgetId=23 portraitLike=363.0x537.0dp ratio=0.6760 candidate=SQUARE marginStrip=437.00dp marginLarge=337.00dp marginRatio=-0.7240
WIDGET 01:27:38 ClockySizeDiag: appWidgetId=23 landscapeLike=667.0x281.0dp ratio=2.3737 candidate=LARGE marginStrip=181.00dp marginLarge=81.00dp marginRatio=0.9737
WIDGET 01:27:38 ClockySizeDiag: appWidgetId=23 reported=363.42856x537.1429dp ratio=0.6766 candidate=SQUARE marginStrip=437.14dp marginLarge=337.14dp marginRatio=-0.7234
WIDGET 01:27:38 ClockySizeDiag: appWidgetId=23 reported=667.4286x281.14285dp ratio=2.3740 candidate=LARGE marginStrip=181.14dp marginLarge=81.14dp marginRatio=0.9740
WIDGET ... 49 more
other  670 line(s) (see logcat_clocky.txt)
WARNING buffer main rolled over: logs between the mark and 01:25:50 are lost (~213s); run `cdev logs`/`collect` right after acting
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
