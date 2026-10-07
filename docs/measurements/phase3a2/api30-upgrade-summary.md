# cdev evidence: api30-final

- device: sdk_gphone_x86_64, Android 11 (API 30), locale en-US, 440 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-07 23:17:32 (debuggable)
- installed APK sha256: `b583cea59c4076f449ed59f87fad9e88fde86a8816821149d3dc108bf23f7d82`
- repo: `7b7ab11bbd` (dirty) on branch `feat/phase3a2-responsive-render`
- launcher: com.google.android.apps.nexuslauncher
- session s-20261008-081000-emulator-5562, captured 2026-10-08T08:33:36+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 5 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |
| 7 | digital | com.google.android.apps.nexuslauncher | unknown | schema 2 |

_no Clocky host view on the current screen (not on this launcher page, or the launcher is in resize mode: press back/tap empty space first)_

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (08:09:16, 1415s) | 699 relevant line(s)
CRASH  none
ANR    none
PROC   08:11:08 am_proc_start: [0,21382,10169,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
PROC   08:12:29 am_kill: [0,21382,com.stupidsavacan.clocky,700,kill background]
PROC   08:17:32 am_proc_start: [0,22308,10169,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
ACT    (54 other lifecycle line(s) hidden; see logcat_clocky.txt)
ACT    08:11:39 wm_create_activity: [0,173574514,26,com.stupidsavacan.clocky/.widget.digital.ResponsiveAuditActivity,NULL,NULL,NULL,268435456]
ACT    08:11:39 wm_on_create_called: [173574514,com.stupidsavacan.clocky.widget.digital.ResponsiveAuditActivity,performCreate]
ACT    08:17:32 wm_finish_activity: [0,173574514,26,com.stupidsavacan.clocky/.widget.digital.ResponsiveAuditActivity,force-stop]
ACT    08:21:19 wm_create_activity: [0,89660978,27,com.stupidsavacan.clocky/.widget.digital.ResponsiveAuditActivity,NULL,NULL,NULL,268435456]
ACT    08:21:19 wm_on_create_called: [89660978,com.stupidsavacan.clocky.widget.digital.ResponsiveAuditActivity,performCreate]
ACT    08:27:14 wm_finish_activity: [0,89660978,27,com.stupidsavacan.clocky/.widget.digital.ResponsiveAuditActivity,app-request]
ACT    08:27:14 wm_on_destroy_called: [89660978,com.stupidsavacan.clocky.widget.digital.ResponsiveAuditActivity,performDestroy]
ACT    08:27:15 wm_create_activity: [0,156370778,28,com.stupidsavacan.clocky/.widget.digital.ResponsiveAuditActivity,NULL,NULL,NULL,268435456]
ACT    ... 9 more
LAUNCH none
WIDGET none
other  625 line(s) (see logcat_clocky.txt)
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

## Compared to baseline-api30

- settings: SAME
- widgets: SAME
