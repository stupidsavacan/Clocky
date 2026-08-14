#!/usr/bin/env python3
"""Fix runtime-relevant lint findings left by the initial AOSP standalone port.

This stage intentionally avoids localization cleanup and focuses on Android platform/runtime
compatibility. Every edit is idempotent and fails loudly if the expected source shape changes.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        print(f"already remediated {path}")
        return
    if old not in text:
        raise RuntimeError(f"expected pattern not found in {path}: {old[:120]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"updated {path}")


def replace_one_of(path: str, olds: list[str], new: str) -> None:
    """Replace one accepted predecessor with the final form.

    This is useful when an earlier remediation version may already have produced an intermediate
    representation. Fresh upstream and already-remediated trees therefore converge identically.
    """
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        print(f"already remediated {path}")
        return
    for old in olds:
        if old in text:
            target.write_text(text.replace(old, new, 1), encoding="utf-8")
            print(f"updated {path}")
            return
    raise RuntimeError(
        f"none of the expected predecessor patterns were found in {path}: "
        + ", ".join(repr(old[:80]) for old in olds)
    )


def replace_all(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old not in text and new in text:
        print(f"already remediated {path}")
        return
    if old not in text:
        raise RuntimeError(f"expected pattern not found in {path}: {old!r}")
    count = text.count(old)
    target.write_text(text.replace(old, new), encoding="utf-8")
    print(f"updated {path}: {count} replacements")


# Centralize Android 13+ POST_NOTIFICATIONS checks so non-FGS notifications fail closed rather
# than crashing or relying on lint suppression. Foreground-service notifications still use
# Service.startForeground directly and are intentionally not routed through this helper.
notification_utils = "app/src/main/java/com/android/deskclock/NotificationUtils.kt"
replace_once(
    notification_utils,
    "import android.app.NotificationChannel\nimport android.content.Context\n",
    "import android.Manifest\n"
    "import android.app.Notification\n"
    "import android.app.NotificationChannel\n"
    "import android.content.Context\n"
    "import android.content.pm.PackageManager\n"
    "import android.os.Build\n",
)
replace_once(
    notification_utils,
    "import androidx.core.app.NotificationManagerCompat\n",
    "import androidx.core.app.NotificationManagerCompat\nimport androidx.core.content.ContextCompat\n",
)
replace_once(
    notification_utils,
    "    @JvmStatic\n    fun createChannel(context: Context, id: String) {\n",
    "    @JvmStatic\n"
    "    fun notifyIfAllowed(\n"
    "        context: Context,\n"
    "        manager: NotificationManagerCompat,\n"
    "        id: Int,\n"
    "        notification: Notification\n"
    "    ) {\n"
    "        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||\n"
    "                ContextCompat.checkSelfPermission(\n"
    "                        context, Manifest.permission.POST_NOTIFICATIONS) ==\n"
    "                PackageManager.PERMISSION_GRANTED\n"
    "        if (allowed) {\n"
    "            manager.notify(id, notification)\n"
    "        }\n"
    "    }\n\n"
    "    @JvmStatic\n"
    "    fun createChannel(context: Context, id: String) {\n",
)

alarm_notifications = "app/src/main/java/com/android/deskclock/alarms/AlarmNotifications.kt"
replace_once(
    alarm_notifications,
    "import com.android.deskclock.LogUtils\n",
    "import com.android.deskclock.LogUtils\nimport com.android.deskclock.NotificationUtils\n",
)
replace_all(
    alarm_notifications,
    "nm.notify(",
    "NotificationUtils.notifyIfAllowed(context, nm, ",
)

stopwatch_model = "app/src/main/java/com/android/deskclock/data/StopwatchModel.kt"
replace_once(
    stopwatch_model,
    "import androidx.core.app.NotificationManagerCompat\n\nimport kotlin.math.max\n",
    "import androidx.core.app.NotificationManagerCompat\n\n"
    "import com.android.deskclock.NotificationUtils\n\n"
    "import kotlin.math.max\n",
)
replace_all(
    stopwatch_model,
    "mNotificationManager.notify(",
    "NotificationUtils.notifyIfAllowed(mContext, mNotificationManager, ",
)

timer_model = "app/src/main/java/com/android/deskclock/data/TimerModel.kt"
replace_once(
    timer_model,
    "import com.android.deskclock.LogUtils\n",
    "import com.android.deskclock.LogUtils\nimport com.android.deskclock.NotificationUtils\n",
)
replace_all(
    timer_model,
    "mNotificationManager.notify(",
    "NotificationUtils.notifyIfAllowed(mContext, mNotificationManager, ",
)

# These callbacks intentionally do local work but still need the Activity base implementation.
replace_once(
    "app/src/main/java/com/android/deskclock/DeskClock.kt",
    "    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {\n"
    "        // Recreate the activity if any settings have been changed\n",
    "    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {\n"
    "        super.onActivityResult(requestCode, resultCode, data)\n"
    "        // Recreate the activity if any settings have been changed\n",
)
replace_once(
    "app/src/main/java/com/android/deskclock/ringtone/RingtonePickerActivity.kt",
    "    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {\n"
    "        if (resultCode != RESULT_OK) {\n",
    "    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {\n"
    "        super.onActivityResult(requestCode, resultCode, data)\n"
    "        if (resultCode != RESULT_OK) {\n",
)

# Use the public AndroidX tint API rather than an appcompat group-restricted method.
label_dialog = "app/src/main/java/com/android/deskclock/LabelDialogFragment.kt"
replace_once(
    label_dialog,
    "import androidx.fragment.app.FragmentManager\n",
    "import androidx.fragment.app.FragmentManager\nimport androidx.core.view.ViewCompat\n",
)
replace_once(
    label_dialog,
    "        mLabelBox?.setSupportBackgroundTintList(ColorStateList(\n"
    "                arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),\n"
    "                intArrayOf(colorControlActivated, colorControlNormal)))\n",
    "        mLabelBox?.let {\n"
    "            ViewCompat.setBackgroundTintList(it, ColorStateList(\n"
    "                    arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),\n"
    "                    intArrayOf(colorControlActivated, colorControlNormal)))\n"
    "        }\n",
)

# Material's SnackbarLayout class is library-internal. Detect a snackbar dependency through its
# public resource id instead of referencing the restricted implementation class.
snackbar_behavior = "app/src/main/java/com/android/deskclock/widget/toast/SnackbarSlidingBehavior.kt"
replace_once(
    snackbar_behavior,
    "import com.google.android.material.snackbar.Snackbar\n\n",
    "import com.google.android.material.R as MaterialR\n\n",
)
replace_once(
    snackbar_behavior,
    " * Custom [CoordinatorLayout.Behavior] that slides with the [Snackbar].\n",
    " * Custom [CoordinatorLayout.Behavior] that slides with Material snackbars.\n",
)
replace_once(
    snackbar_behavior,
    "        return dependency is Snackbar.SnackbarLayout\n",
    "        return dependency.findViewById<View>(MaterialR.id.snackbar_text) != null\n",
)

# timer_notifications_less_min is plain text. Return it before the generic format-resource path so
# lint can prove that every resource passed to String.format actually contains format arguments.
timer_formatter = "app/src/main/java/com/android/deskclock/data/TimerStringFormatter.kt"
timer_original = (
    "        } else if (showSeconds) {\n"
    "            formatStringId = R.string.timer_notifications_seconds\n"
    "        } else if (!shouldShowSeconds) {\n"
    "            formatStringId = R.string.timer_notifications_less_min\n"
    "        }\n\n"
    "        return if (formatStringId == -1) {\n"
    "            null\n"
    "        } else {\n"
    "            String.format(context.getString(formatStringId), hourSeq, minSeq,\n"
    "                    remainingSuffix, secSeq)\n"
    "        }\n"
)
timer_intermediate = (
    "        } else if (showSeconds) {\n"
    "            formatStringId = R.string.timer_notifications_seconds\n"
    "        } else if (!shouldShowSeconds) {\n"
    "            formatStringId = R.string.timer_notifications_less_min\n"
    "        }\n\n"
    "        return when (formatStringId) {\n"
    "            -1 -> null\n"
    "            R.string.timer_notifications_less_min -> context.getString(formatStringId)\n"
    "            else -> String.format(context.getString(formatStringId), hourSeq, minSeq,\n"
    "                    remainingSuffix, secSeq)\n"
    "        }\n"
)
timer_final = (
    "        } else if (showSeconds) {\n"
    "            formatStringId = R.string.timer_notifications_seconds\n"
    "        } else if (!shouldShowSeconds) {\n"
    "            return context.getString(R.string.timer_notifications_less_min)\n"
    "        }\n\n"
    "        return if (formatStringId == -1) {\n"
    "            null\n"
    "        } else {\n"
    "            String.format(context.getString(formatStringId), hourSeq, minSeq,\n"
    "                    remainingSuffix, secSeq)\n"
    "        }\n"
)
replace_one_of(timer_formatter, [timer_original, timer_intermediate], timer_final)

# The service's completion broadcast targets an in-app, non-exported dynamic receiver. Restrict
# delivery to Clocky's own package instead of launching an unsafe implicit broadcast.
replace_once(
    "app/src/main/java/com/android/deskclock/alarms/AlarmService.kt",
    "        sendBroadcast(Intent(ALARM_DONE_ACTION))\n",
    "        sendBroadcast(Intent(ALARM_DONE_ACTION).setPackage(packageName))\n",
)

print("Runtime lint remediation complete")
