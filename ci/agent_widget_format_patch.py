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

/** Pure hour-mode mapping for settings whose runtime meaning is explicitly defined. */
object DigitalWidgetFormatPolicy {
    fun timeOverride(hourMode: HourMode): String? = when (hourMode) {
        HourMode.FOLLOW_SYSTEM -> null
        HourMode.FORCE_12_HOUR -> "h:mm"
        HourMode.FORCE_24_HOUR -> "HH:mm"
    }
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
    fun followSystemLeavesExistingTextClockFormatsUntouched() {
        assertNull(DigitalWidgetFormatPolicy.timeOverride(HourMode.FOLLOW_SYSTEM))
    }

    @Test
    fun explicitHourModesMatchRepositoryMvpFormats() {
        assertEquals("h:mm", DigitalWidgetFormatPolicy.timeOverride(HourMode.FORCE_12_HOUR))
        assertEquals("HH:mm", DigitalWidgetFormatPolicy.timeOverride(HourMode.FORCE_24_HOUR))
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
    '''            // Preserve the existing locale-driven formats unless an explicit hour mode exists.
            val dateFormat: CharSequence = getDateFormat(context)
            val timeFormatOverride: CharSequence? =
                    DigitalWidgetFormatPolicy.timeOverride(widgetSettings.time.hourMode)
''',
    'provider resolved hour mode',
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
                    resolvedWeights.timeWeight,
''',
    'provider optimize hour mode',
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
    'provider remote hour mode',
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
            requestedTimeWeight: Int,
''',
    'provider optimize signature',
)
p = replace_once(
    p,
    '''            date.setFormat12Hour(dateFormat)
            date.setFormat24Hour(dateFormat)
            DigitalWidgetWeightRenderer.applySizerWeights(
''',
    '''            date.setFormat12Hour(dateFormat)
            date.setFormat24Hour(dateFormat)
            if (timeFormatOverride != null) {
                clock.setFormat12Hour(timeFormatOverride)
                clock.setFormat24Hour(timeFormatOverride)
            }
            DigitalWidgetWeightRenderer.applySizerWeights(
''',
    'provider sizer hour mode',
)
provider.write_text(p, encoding='utf-8')

for path in (policy, test, renderer, provider):
    if not path.exists() or path.stat().st_size == 0:
        raise RuntimeError(f'missing patched file: {path}')
    print(path, path.stat().st_size)
