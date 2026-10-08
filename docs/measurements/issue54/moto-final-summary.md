# cdev evidence: issue54-final

- device: moto g13, Android 14 (API 34), locale ja-JP, 280 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-09 00:02:41 (debuggable)
- installed APK sha256: `0f3e3bdb7353b02d6aed3164ad2d38c12253d5a180e687e704b5354c87658d27`
- repo: `50a09b1936` (dirty) on branch `fix/issue54-preview-scale`
- launcher: com.motorola.launcher3
- session s-20261008-233651-ZY22GSDPFW, captured 2026-10-09T00:03:34+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 23 | digital | com.motorola.launcher3 | Card (est.) | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (23:36:49, 1601s) | 624 relevant line(s)
CRASH  none
ANR    none
PROC   23:45:17 am_kill: [0,30584,com.stupidsavacan.clocky,900,stop com.stupidsavacan.clocky due to installPackageLI]
PROC   23:45:17 am_proc_start: [0,5483,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}]
PROC   23:53:46 am_kill: [0,5483,com.stupidsavacan.clocky,900,kill background]
PROC   00:02:41 am_proc_start: [0,8735,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
ACT    (76 other lifecycle line(s) hidden; see logcat_clocky.txt)
ACT    23:42:30 wm_finish_activity: [0,243676863,212,com.stupidsavacan.clocky/.widget.studio.StudioActivity,app-request]
ACT    23:42:31 wm_on_destroy_called: [243676863,com.stupidsavacan.clocky.widget.studio.StudioActivity,performDestroy,1]
ACT    23:42:32 wm_finish_activity: [0,93759243,212,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,clear-task-all]
ACT    23:42:32 wm_on_destroy_called: [93759243,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performDestroy,1]
ACT    23:45:17 wm_finish_activity: [0,33492194,211,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,proc died without state saved]
ACT    23:45:31 wm_create_activity: [0,10358656,213,com.stupidsavacan.clocky/.widget.DigitalWidgetConfigActivity,android.appwidget.action.APPWIDGET_CONFIGURE,NULL,NULL,268435456]
ACT    23:45:31 wm_on_create_called: [10358656,com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity,performCreate,494]
ACT    23:45:50 wm_create_activity: [0,241507409,213,com.stupidsavacan.clocky/.widget.studio.StudioActivity,NULL,NULL,NULL,0]
ACT    ... 19 more
LAUNCH none
WIDGET 23:45:17 ActivityManager: Start proc 5483:com.stupidsavacan.clocky/u0a232 for broadcast {com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}
other  516 line(s) (see logcat_clocky.txt)
WARNING buffer main rolled over: logs between the mark and 23:57:52 are lost (~1263s); run `cdev logs`/`collect` right after acting
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

## Compared to issue54-baseline

- settings: SAME
- widgets: DIFFERENT
