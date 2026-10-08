# cdev evidence: final-audit-lifecycle

- device: moto g13, Android 14 (API 34), locale ja-JP, 280 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-08 17:42:14 (debuggable)
- installed APK sha256: `099caa47396e9e542d4a30bffeeb863009f3182a318cd28c5f3ac841c2799402`
- repo: `c5a14b7167` (dirty) on branch `verify/moto-pr49`
- launcher: com.motorola.launcher3
- session s-20261008-174210-ZY22GSDPFW, captured 2026-10-08T17:42:30+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 23 | digital | com.motorola.launcher3 | Card (est.) | schema 2 |

## Checks

- none

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (17:42:08, 19s) | 211 relevant line(s)
CRASH  none
ANR    none
PROC   17:42:15 am_proc_start: [0,25881,10232,com.stupidsavacan.clocky,broadcast,{com.stupidsavacan.clocky/com.android.deskclock.AlarmInitReceiver}]
ACT    (8 other lifecycle line(s) hidden; see logcat_clocky.txt)
ACT    17:42:16 wm_create_activity: [0,184991143,199,com.stupidsavacan.clocky/.widget.digital.ResponsiveAuditActivity,NULL,NULL,NULL,268435456]
ACT    17:42:16 wm_on_create_called: [184991143,com.stupidsavacan.clocky.widget.digital.ResponsiveAuditActivity,performCreate,44]
ACT    17:42:18 wm_finish_activity: [0,184991143,199,com.stupidsavacan.clocky/.widget.digital.ResponsiveAuditActivity,app-request]
ACT    17:42:18 wm_on_destroy_called: [184991143,com.stupidsavacan.clocky.widget.digital.ResponsiveAuditActivity,performDestroy,3]
LAUNCH none
WIDGET none
other  198 line(s) (see logcat_clocky.txt)
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

## Compared to s-20261008-104435-ZY22GSDPFW/baseline

- settings: SAME
- widgets: SAME
