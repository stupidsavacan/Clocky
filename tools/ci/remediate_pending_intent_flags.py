#!/usr/bin/env python3
"""Add explicit PendingIntent mutability to the standalone AOSP DeskClock port.

Most PendingIntents are immutable because their semantics never need to be changed by the sender.
The Digital Cities collection template is deliberately mutable because RemoteViews combines each
row's fillInIntent with that template when a city is tapped.
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
        raise RuntimeError(f"expected pattern not found in {path}: {old!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"updated {path}")


# Alarm state lookup/cancellation: no caller needs to alter the explicit service intent.
replace_once(
    "app/src/main/java/com/android/deskclock/alarms/AlarmStateManager.kt",
    "                    PendingIntent.FLAG_NO_CREATE)\n",
    "                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)\n",
)

# Analog widget root click opens a fixed explicit activity.
replace_once(
    "app/src/main/java/com/android/alarmclock/AnalogAppWidgetProvider.kt",
    "                val pi: PendingIntent = PendingIntent.getActivity(context, 0, openApp, 0)\n",
    "                val pi: PendingIntent = PendingIntent.getActivity(\n"
    "                        context, 0, openApp, PendingIntent.FLAG_IMMUTABLE)\n",
)

# Backup completion is a fixed explicit broadcast and can be immutable.
replace_once(
    "app/src/main/java/com/android/deskclock/DeskClockBackupAgent.kt",
    "                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_CANCEL_CURRENT)\n",
    "                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_CANCEL_CURRENT or\n"
    "                        PendingIntent.FLAG_IMMUTABLE)\n",
)

# Digital widget day-change callbacks and the root click do not accept fill-in data.
digital = "app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt"
replace_once(
    digital,
    "                PendingIntent.getBroadcast(context, 0, DAY_CHANGE_INTENT, FLAG_UPDATE_CURRENT)\n",
    "                PendingIntent.getBroadcast(context, 0, DAY_CHANGE_INTENT,\n"
    "                        FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n",
)
replace_once(
    digital,
    "                PendingIntent.getBroadcast(context, 0, DAY_CHANGE_INTENT, FLAG_NO_CREATE)\n",
    "                PendingIntent.getBroadcast(context, 0, DAY_CHANGE_INTENT,\n"
    "                        FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)\n",
)
replace_once(
    digital,
    "                val pi: PendingIntent = PendingIntent.getActivity(context, 0, openApp, 0)\n",
    "                val pi: PendingIntent = PendingIntent.getActivity(\n"
    "                        context, 0, openApp, PendingIntent.FLAG_IMMUTABLE)\n",
)

# Collection rows supply a fillInIntent, so this one template must remain mutable.
replace_once(
    digital,
    "                    val pi: PendingIntent = PendingIntent.getActivity(context, 0, selectCity, 0)\n",
    "                    val pi: PendingIntent = PendingIntent.getActivity(\n"
    "                            context, 0, selectCity, PendingIntent.FLAG_MUTABLE)\n",
)

# Timer callbacks are fixed explicit service intents; apply the same immutability flag to both
# creation and FLAG_NO_CREATE lookup so the PendingIntent identity matches during cancellation.
timer_model = "app/src/main/java/com/android/deskclock/data/TimerModel.kt"
replace_once(
    timer_model,
    "                    0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE)\n",
    "                    0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE or\n"
    "                            PendingIntent.FLAG_IMMUTABLE)\n",
)
replace_once(
    timer_model,
    "                    0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)\n",
    "                    0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or\n"
    "                            PendingIntent.FLAG_IMMUTABLE)\n",
)

# Notification refresh callbacks are likewise fixed explicit service intents.
timer_notification = "app/src/main/java/com/android/deskclock/data/TimerNotificationBuilder.kt"
replace_once(
    timer_notification,
    "                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT)\n",
    "                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or\n"
    "                                PendingIntent.FLAG_IMMUTABLE)\n",
)
replace_once(
    timer_notification,
    "                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE)\n",
    "                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_NO_CREATE or\n"
    "                                PendingIntent.FLAG_IMMUTABLE)\n",
)

print("PendingIntent mutability remediation complete")
