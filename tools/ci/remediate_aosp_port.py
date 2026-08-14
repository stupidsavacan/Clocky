#!/usr/bin/env python3
"""Apply deterministic standalone-app remediations to the imported AOSP DeskClock tree.

The script is intentionally idempotent: each edit accepts either the upstream form or the
already-remediated form. It does not touch the vendor/reference material.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old in text:
        text = text.replace(old, new, 1)
        target.write_text(text, encoding="utf-8")
        print(f"updated {path}")
        return
    if new in text:
        print(f"already remediated {path}")
        return
    raise RuntimeError(f"expected pattern not found in {path}: {old[:100]!r}")


def replace_all(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old in text:
        count = text.count(old)
        target.write_text(text.replace(old, new), encoding="utf-8")
        print(f"updated {path}: {count} replacements")
        return
    if new in text:
        print(f"already remediated {path}")
        return
    raise RuntimeError(f"expected pattern not found in {path}: {old!r}")


alarm_activity = "app/src/main/java/com/android/deskclock/alarms/AlarmActivity.kt"
replace_once(
    alarm_activity,
    "package com.android.deskclock.alarms\n\nimport android.accessibilityservice.AccessibilityServiceInfo",
    "package com.android.deskclock.alarms\n\nimport android.annotation.SuppressLint\nimport android.accessibilityservice.AccessibilityServiceInfo",
)
replace_once(
    alarm_activity,
    "import android.os.Bundle\n",
    "import android.os.Build\nimport android.os.Bundle\n",
)
replace_once(
    alarm_activity,
    "import androidx.core.graphics.ColorUtils\n",
    "import androidx.core.content.ContextCompat\nimport androidx.core.graphics.ColorUtils\n",
)
replace_once(
    alarm_activity,
    "        if (Utils.isOOrLater) {\n",
    "        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {\n",
)
replace_once(
    alarm_activity,
    "        // Close dialogs and window shade, so this is fully visible\n"
    "        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))\n\n",
    "        // A regular third-party app cannot close system dialogs; rely on the lock-screen\n"
    "        // presentation flags above instead of requesting a privileged system permission.\n\n",
)
replace_once(
    alarm_activity,
    "            registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED)\n",
    "            ContextCompat.registerReceiver(\n"
    "                    this, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)\n",
)
replace_once(
    alarm_activity,
    "    override fun onBackPressed() {\n        // Don't allow back to dismiss.\n    }",
    "    @SuppressLint(\"MissingSuperCall\")\n"
    "    override fun onBackPressed() {\n"
    "        // Intentionally consume Back while an alarm is firing.\n"
    "    }",
)

utils = "app/src/main/java/com/android/deskclock/Utils.kt"
replace_once(
    utils,
    "    @TargetApi(Build.VERSION_CODES.LOLLIPOP)\n"
    "    fun updateNextAlarm(am: AlarmManager, info: AlarmClockInfo, op: PendingIntent) {\n"
    "        am.setAlarmClock(info, op)\n"
    "    }",
    "    @TargetApi(Build.VERSION_CODES.LOLLIPOP)\n"
    "    fun updateNextAlarm(am: AlarmManager, info: AlarmClockInfo, op: PendingIntent) {\n"
    "        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {\n"
    "            try {\n"
    "                am.setAlarmClock(info, op)\n"
    "                return\n"
    "            } catch (_: SecurityException) {\n"
    "                // Permission can be revoked between the capability check and the call.\n"
    "            }\n"
    "        }\n\n"
    "        // Preserve a best-effort alarm instead of crashing when exact-alarm access is denied.\n"
    "        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, info.triggerTime, op)\n"
    "    }",
)

manifest = "app/src/main/AndroidManifest.xml"
replace_once(
    manifest,
    "    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />\n",
    "    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />\n"
    "    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE_SPECIAL_USE\" />\n",
)
replace_once(
    manifest,
    "        <service android:name=\".alarms.AlarmService\" android:directBootAware=\"true\" />",
    "        <service android:name=\".alarms.AlarmService\" android:directBootAware=\"true\" android:foregroundServiceType=\"specialUse\">\n"
    "            <property android:name=\"android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE\" android:value=\"user_visible_alarm_ringing\" />\n"
    "        </service>",
)
replace_once(
    manifest,
    "        <service android:name=\".timer.TimerService\" android:description=\"@string/timer_service_desc\" android:directBootAware=\"true\" />",
    "        <service android:name=\".timer.TimerService\" android:description=\"@string/timer_service_desc\" android:directBootAware=\"true\" android:foregroundServiceType=\"specialUse\">\n"
    "            <property android:name=\"android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE\" android:value=\"user_visible_countdown_timer\" />\n"
    "        </service>",
)
replace_once(
    manifest,
    "        <service android:name=\".stopwatch.StopwatchService\" android:description=\"@string/stopwatch_service_desc\" android:directBootAware=\"true\" />",
    "        <service android:name=\".stopwatch.StopwatchService\" android:description=\"@string/stopwatch_service_desc\" android:directBootAware=\"true\" android:foregroundServiceType=\"specialUse\">\n"
    "            <property android:name=\"android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE\" android:value=\"user_visible_stopwatch\" />\n"
    "        </service>",
)

replace_all(
    "app/src/main/java/com/android/deskclock/alarms/AlarmNotifications.kt",
    "NotificationManagerCompat.IMPORTANCE_",
    "NotificationManager.IMPORTANCE_",
)

print("AOSP standalone remediation complete")
