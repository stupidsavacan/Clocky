#!/usr/bin/env python3
from pathlib import Path

manifest = Path("app/src/main/AndroidManifest.xml")
workflow = Path(".github/workflows/apply-widget-config-manifest.yml")
self_path = Path("tools/ci/apply_widget_config_manifest.py")

text = manifest.read_text(encoding="utf-8")
needle = '        <activity android:name=".settings.SettingsActivity" android:excludeFromRecents="true" android:label="@string/settings" android:parentActivityName=".DeskClock" android:taskAffinity="" android:theme="@style/Theme.DeskClock.Settings" />\n'
insert = needle + '''        <activity
            android:name="com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity"
            android:exported="true"
            android:label="@string/clocky_widget_config_label"
            android:theme="@style/Theme.DeskClock.Settings">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>\n'''

count = text.count(needle)
if count != 1:
    raise SystemExit(f"expected one SettingsActivity anchor, found {count}")

manifest.write_text(text.replace(needle, insert, 1), encoding="utf-8")

for path in (workflow, self_path):
    if path.exists():
        path.unlink()
