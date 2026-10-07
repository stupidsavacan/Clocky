"""Clocky-specific constants and exit codes."""

PACKAGE = "com.stupidsavacan.clocky"
DIGITAL_PROVIDER = "com.android.alarmclock.DigitalAppWidgetProvider"
ANALOG_PROVIDER = "com.android.alarmclock.AnalogAppWidgetProvider"
CONFIG_ACTIVITY = "com.stupidsavacan.clocky.widget.DigitalWidgetConfigActivity"
LAUNCH_ACTIVITY = "com.android.deskclock.DeskClock"
PREFS_FILE = "shared_prefs/clocky_widget_settings.xml"
PREFS_KEY_RE = r"^widget\.(\d+)\.settings$"
STRIP_MAX_DP = 100
DEFAULT_APK = "app/build/outputs/apk/debug/app-debug.apk"

DEVICE_TMP_DIR = "/data/local/tmp"
DEVICE_TMP_PREFIX = "clocky-dev-"

# Settings keys cdev may ever write (rotate / --stay-awake). Everything else is refused.
SETTINGS_ALLOWLIST = (
    "system/accelerometer_rotation",
    "system/user_rotation",
    "global/stay_on_while_plugged_in",
)

EXIT_OK = 0
EXIT_USAGE = 2      # usage error / ambiguity: needs a human or Claude decision
EXIT_DEVICE = 3     # device selection failure
EXIT_UI = 4         # UI target not found / dump failure
EXIT_ADB = 5        # adb or command failure
EXIT_REFUSED = 6    # safety boundary
EXIT_CHECK = 7      # observation succeeded, but an explicitly requested condition is false

PROC_KILL_MARK = "proc-kill"
TIME_TEXT_RE = r"^\d{1,2}[:：.]\d{2}([:：.]\d{2})?$"

SESSIONS_WARN_MB = 200
