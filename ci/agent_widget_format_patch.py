from pathlib import Path

root = Path('.')


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected one match, got {count}')
    return source.replace(old, new, 1)

policy = root / 'app/src/main/java/com/stupidsavacan/clocky/customization/format/DigitalWidgetFormatPolicy.kt'
policy.parent.mkdir(parents=True, exist_ok=True)
policy.write_text('''package com.stupidsavacan.clocky.customization.format

import com.stupidsavacan.clocky.customization.model.HourMode

/** Pure format mapping for settings whose runtime meaning is explicitly defined. */
object DigitalWidgetFormatPolicy {
    fun timeOverride(hourMode: HourMode): String? = when (hourMode) {
        HourMode.SYSTEM -> null
        HourMode.HOUR_12 -> "h:mm"
        HourMode.HOUR_24 -> "HH:mm"
    }

    fun dateFormat(override: String?, systemDefault: String): String = override ?: systemDefault
}
''', encoding='utf-8')

test = root / 'app/src/test/java/com/stupidsavacan/clocky/customization/format/DigitalWidgetFormatPolicyTest.kt'
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('''package com.stupidsavacan.clocky.customization.format

import com.stupidsavacan.clocky.customization.model.HourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitalWidgetFormatPolicyTest {
    @Test
    fun systemHourModeLeavesExistingTextClockFormatsUntouched() {
        assertNull(DigitalWidgetFormatPolicy.timeOverride(HourMode.SYSTEM))
    }

    @Test
    fun explicitHourModesMatchRepositoryMvpFormats() {
        assertEquals("h:mm", DigitalWidgetFormatPolicy.timeOverride(HourMode.HOUR_12))
        assertEquals("HH:mm", DigitalWidgetFormatPolicy.timeOverride(HourMode.HOUR_24))
    }

    @Test
    fun dateOverrideWinsAndNullFallsBackToSystemPattern() {
        assertEquals("yyyy.MM.dd", DigitalWidgetFormatPolicy.dateFormat("yyyy.MM.dd", "EEE, MMM d"))
        assertEquals("EEE, MMM d", DigitalWidgetFormatPolicy.dateFormat(null, "EEE, MMM d"))
    }
}
''', encoding='utf-8')

renderer = root / 'app/src/main/java/com/stupidsavacan/clocky/customization/font/DigitalWidgetWeightRenderer.kt'
r = renderer.read_text(encoding='utf-8')
r = replace_once(
    r,
    '''        dateFormat: CharSequence,
        dateEnabled: Boolean = true,
''',
    '''        dateFormat: CharSequence,
        timeFormatOverride: CharSequence? = null,
        dateEnabled: Boolean = true,
''',
    'renderer format parameter',
)
r = replace_once(
    r,
    '''            sizePx = clockSizePx,
            dateFormat = null,
            enabled = true,
''',
    '''            sizePx = clockSizePx,
            dateFormat = timeFormatOverride,
            enabled = true,
''',
    'renderer time format application',
)
renderer.write_text(r, encoding='utf-8')

provider = root / 'app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt'
p = provider.read_text(encoding='utf-8')
p = replace_once(
    p,
    '''import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer
''',
    '''import com.stupidsavacan.clocky.customization.font.DigitalWidgetWeightRenderer
import com.stupidsavacan.clocky.customization.format.DigitalWidgetFormatPolicy
''',
    'provider policy import',
)
p = replace_once(
    p,
    '''            // Configure child views of the remote view.
            val dateFormat: CharSequence = getDateFormat(context)
''',
    '''            // Configure only format settings whose runtime semantics are explicit.
            val dateFormat: CharSequence = DigitalWidgetFormatPolicy.dateFormat(
                    widgetSettings.format.datePatternOverride,
                    getDateFormat(context),
            )
            val timeFormatOverride: CharSequence? =
                    DigitalWidgetFormatPolicy.timeOverride(widgetSettings.format.hourMode)
''',
    'provider resolved formats',
)
p = replace_once(
    p,
    '''                    template,
                    nextAlarmTime,
                    resolvedWeights.timeWeight,
''',
    '''                    template,
                    nextAlarmTime,
                    timeFormatOverride,
                    dateFormat,
                    resolvedWeights.timeWeight,
''',
    'provider optimize formats',
)
p = replace_once(
    p,
    '''                    dateFormat = dateFormat,
                    dateEnabled = resolvedWeights.dateEnabled,
''',
    '''                    dateFormat = dateFormat,
                    timeFormatOverride = timeFormatOverride,
                    dateEnabled = resolvedWeights.dateEnabled,
''',
    'provider remote formats',
)
p = replace_once(
    p,
    '''            template: Sizes,
            nextAlarmTime: String?,
            requestedTimeWeight: Int,
''',
    '''            template: Sizes,
            nextAlarmTime: String?,
            timeFormatOverride: CharSequence?,
            dateFormat: CharSequence,
            requestedTimeWeight: Int,
''',
    'provider optimize signature',
)
p = replace_once(
    p,
    '''            // Configure the date to display the current date string.
            val dateFormat: CharSequence = getDateFormat(context)
            val date: TextClock = sizer.findViewById(R.id.date) as TextClock
            val clock: TextClock = sizer.findViewById(R.id.clock) as TextClock
            date.setFormat12Hour(dateFormat)
            date.setFormat24Hour(dateFormat)
''',
    '''            // Configure the same formats that the RemoteViews will display.
            val date: TextClock = sizer.findViewById(R.id.date) as TextClock
            val clock: TextClock = sizer.findViewById(R.id.clock) as TextClock
            date.setFormat12Hour(dateFormat)
            date.setFormat24Hour(dateFormat)
            if (timeFormatOverride != null) {
                clock.setFormat12Hour(timeFormatOverride)
                clock.setFormat24Hour(timeFormatOverride)
            }
''',
    'provider sizer formats',
)
provider.write_text(p, encoding='utf-8')

for path in (policy, test, renderer, provider):
    if not path.exists() or path.stat().st_size == 0:
        raise RuntimeError(f'missing patched file: {path}')
    print(path, path.stat().st_size)
