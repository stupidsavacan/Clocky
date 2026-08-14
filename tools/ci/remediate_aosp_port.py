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
    # Check the desired form first. Some replacements intentionally contain the old
    # text as a suffix (for example, inserting an import immediately before another
    # import); checking `old` first would therefore apply the edit repeatedly.
    if new in text:
        print(f"already remediated {path}")
        return
    if old in text:
        target.write_text(text.replace(old, new, 1), encoding="utf-8")
        print(f"updated {path}")
        return
    raise RuntimeError(f"expected pattern not found in {path}: {old[:100]!r}")


def replace_all(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old not in text and new in text:
        print(f"already remediated {path}")
        return
    if old in text:
        count = text.count(old)
        target.write_text(text.replace(old, new), encoding="utf-8")
        print(f"updated {path}: {count} replacements")
        return
    raise RuntimeError(f"expected pattern not found in {path}: {old!r}")


def dedupe_exact_line(path: str, line: str) -> None:
    """Keep the first exact occurrence of `line` and remove accidental duplicates."""
    target = ROOT / path
    lines = target.read_text(encoding="utf-8").splitlines(keepends=True)
    seen = False
    changed = False
    output: list[str] = []
    for item in lines:
        if item.rstrip("\r\n") == line:
            if seen:
                changed = True
                continue
            seen = True
        output.append(item)
    if changed:
        target.write_text("".join(output), encoding="utf-8")
        print(f"deduplicated {path}: {line}")


alarm_activity = "app/src/main/java/com/android/deskclock/alarms/AlarmActivity.kt"
dedupe_exact_line(alarm_activity, "import android.os.Build")
dedupe_exact_line(alarm_activity, "import androidx.core.content.ContextCompat")
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

alarm_service = "app/src/main/java/com/android/deskclock/alarms/AlarmService.kt"
replace_once(
    alarm_service,
    "import android.telephony.TelephonyManager\n\nimport com.android.deskclock.AlarmAlertWakeLock",
    "import android.telephony.TelephonyManager\nimport androidx.core.content.ContextCompat\n\nimport com.android.deskclock.AlarmAlertWakeLock",
)
replace_once(
    alarm_service,
    "        registerReceiver(mActionsReceiver, filter, Context.RECEIVER_EXPORTED)\n",
    "        ContextCompat.registerReceiver(\n"
    "                this, mActionsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)\n",
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

alarm_state_manager = "app/src/main/java/com/android/deskclock/alarms/AlarmStateManager.kt"
replace_once(
    alarm_state_manager,
    "            val am: AlarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager\n"
    "            if (Utils.isMOrLater) {\n"
    "                // Ensure the alarm fires even if the device is dozing.\n"
    "                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)\n"
    "            } else {\n"
    "                am.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)\n"
    "            }",
    "            val am: AlarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager\n"
    "            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||\n"
    "                    am.canScheduleExactAlarms()) {\n"
    "                try {\n"
    "                    if (Utils.isMOrLater) {\n"
    "                        // Ensure the alarm fires even if the device is dozing.\n"
    "                        am.setExactAndAllowWhileIdle(\n"
    "                                AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)\n"
    "                    } else {\n"
    "                        am.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)\n"
    "                    }\n"
    "                    return\n"
    "                } catch (_: SecurityException) {\n"
    "                    // Exact-alarm access can be revoked after the capability check.\n"
    "                }\n"
    "            }\n\n"
    "            // Keep the state machine alive with best-effort timing instead of crashing.\n"
    "            if (Utils.isMOrLater) {\n"
    "                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)\n"
    "            } else {\n"
    "                am.set(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)\n"
    "            }",
)

timer_model = "app/src/main/java/com/android/deskclock/data/TimerModel.kt"
replace_once(
    timer_model,
    "        fun schedulePendingIntent(am: AlarmManager, triggerTime: Long, pi: PendingIntent) {\n"
    "            if (Utils.isMOrLater) {\n"
    "                // Ensure the timer fires even if the device is dozing.\n"
    "                am.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)\n"
    "            } else {\n"
    "                am.setExact(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)\n"
    "            }\n"
    "        }",
    "        fun schedulePendingIntent(am: AlarmManager, triggerTime: Long, pi: PendingIntent) {\n"
    "            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||\n"
    "                    am.canScheduleExactAlarms()) {\n"
    "                try {\n"
    "                    if (Utils.isMOrLater) {\n"
    "                        // Ensure the timer fires even if the device is dozing.\n"
    "                        am.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)\n"
    "                    } else {\n"
    "                        am.setExact(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)\n"
    "                    }\n"
    "                    return\n"
    "                } catch (_: SecurityException) {\n"
    "                    // Exact-alarm access can be revoked after the capability check.\n"
    "                }\n"
    "            }\n\n"
    "            if (Utils.isMOrLater) {\n"
    "                am.setAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)\n"
    "            } else {\n"
    "                am.set(ELAPSED_REALTIME_WAKEUP, triggerTime, pi)\n"
    "            }\n"
    "        }",
)

replace_once(
    "app/src/main/java/com/android/deskclock/DeskClockBackupAgent.kt",
    "        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, restoreIntent)\n",
    "        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, restoreIntent)\n",
)
replace_once(
    "app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt",
    "        getAlarmManager(context).setExact(AlarmManager.RTC, nextDay.time, pi)\n",
    "        // Day-boundary refresh does not require exact-alarm privilege.\n"
    "        getAlarmManager(context).set(AlarmManager.RTC, nextDay.time, pi)\n",
)

manifest = "app/src/main/AndroidManifest.xml"
dedupe_exact_line(
    manifest,
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />',
)
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
replace_all(
    "app/src/main/java/com/android/deskclock/data/StopwatchNotificationBuilder.kt",
    "NotificationManagerCompat.IMPORTANCE_",
    "android.app.NotificationManager.IMPORTANCE_",
)
replace_all(
    "app/src/main/java/com/android/deskclock/data/TimerNotificationBuilder.kt",
    "NotificationManagerCompat.IMPORTANCE_",
    "android.app.NotificationManager.IMPORTANCE_",
)

replace_once(
    "app/src/main/java/com/android/deskclock/NotificationUtils.kt",
    "    private fun getAllExistingChannelIds(nm: NotificationManagerCompat): Set<String> {\n",
    "    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)\n"
    "    private fun getAllExistingChannelIds(nm: NotificationManagerCompat): Set<String> {\n",
)
replace_once(
    "app/src/main/java/com/android/deskclock/settings/SettingsActivity.kt",
    "                listPref.setSummary(Utils.getNumberFormattedQuantityString(getActivity()!!,\n",
    "                listPref.setSummary(Utils.getNumberFormattedQuantityString(requireActivity(),\n",
)
replace_once(
    "app/src/main/java/com/android/deskclock/provider/Alarm.kt",
    "        val CREATOR: Parcelable.Creator<Alarm> = object : Parcelable.Creator<Alarm> {\n",
    "        @JvmField\n"
    "        val CREATOR: Parcelable.Creator<Alarm> = object : Parcelable.Creator<Alarm> {\n",
)

print("AOSP standalone remediation complete")
