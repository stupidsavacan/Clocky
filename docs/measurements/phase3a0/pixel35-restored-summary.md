# cdev evidence: restored

- device: sdk_gphone64_x86_64, Android 15 (API 35), locale en-US, 420 dpi, rotation 0
- Clocky: versionName 0.2.0-aosp-port, versionCode 2, last updated 2026-10-07 16:06:11 (debuggable)
- installed APK sha256: `0349ebb6bc98eaf1401d506193169ab5232a5aa8dfde010ee1105e27f295cc8c`
- repo: `dddd16c7db` (dirty) on branch `spike/3a-size-map`
- launcher: com.google.android.apps.nexuslauncher
- session s-20261008-012114-emulator-5560, captured 2026-10-08T01:22:16+0900, cdev 1.0.0

## Widgets

| id | kind | host | size class | settings |
|---|---|---|---|---|
| 2 | digital | com.google.android.apps.nexuslauncher | Card (est.) | schema 2 |

## Checks

- `settings_without_widget` id=3 orphan settings (widget deleted without cleanup?)
- `settings_without_widget` id=5 orphan settings (widget deleted without cleanup?)

## Clocky log summary (since prepare/mark, Clocky-filtered)

```
logs since (01:20:30, 60s) | 60 relevant line(s)
CRASH  none
ANR    none
PROC   none
ACT    none
LAUNCH none
WIDGET 01:21:21 b/267448330: provider: ComponentInfo{com.stupidsavacan.clocky/com.android.alarmclock.DigitalAppWidgetProvider}, paddedSizes: [360.38095x233.90475, 360.38095x224.0, 676.5714x
WIDGET 01:21:21 ClockySizeDiag: event=onAppWidgetOptionsChanged appWidgetId=2 launcher=com.google.android.apps.nexuslauncher density=2.625 minW=360.0 minH=135.0 maxW=676.0 maxH=233.0 OPTION_AP
WIDGET 01:21:21 ClockySizeDiag: appWidgetId=2 minPair=360.0x135.0dp ratio=2.6667 candidate=CARD marginStrip=35.00dp marginLarge=-65.00dp marginRatio=1.2667
WIDGET 01:21:21 ClockySizeDiag: appWidgetId=2 maxPair=676.0x233.0dp ratio=2.9013 candidate=LARGE marginStrip=133.00dp marginLarge=33.00dp marginRatio=1.5013
WIDGET 01:21:21 ClockySizeDiag: appWidgetId=2 portraitLike=360.0x233.0dp ratio=1.5451 candidate=LARGE marginStrip=133.00dp marginLarge=33.00dp marginRatio=0.1451
WIDGET 01:21:21 ClockySizeDiag: appWidgetId=2 landscapeLike=676.0x135.0dp ratio=5.0074 candidate=CARD marginStrip=35.00dp marginLarge=-65.00dp marginRatio=3.6074
WIDGET 01:21:21 ClockySizeDiag: appWidgetId=2 reported=360.38095x233.90475dp ratio=1.5407 candidate=LARGE marginStrip=133.90dp marginLarge=33.90dp marginRatio=0.1407
WIDGET 01:21:21 ClockySizeDiag: appWidgetId=2 reported=360.38095x224.0dp ratio=1.6088 candidate=LARGE marginStrip=124.00dp marginLarge=24.00dp marginRatio=0.2088
WIDGET ... 3 more
other  49 line(s) (see logcat_clocky.txt)
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

## Compared to build/device/sessions/s-20261007-235716-emulator-5560/0012-collect-phase2-baseline

- settings: SAME
- widgets: SAME
