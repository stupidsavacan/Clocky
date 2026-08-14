from pathlib import Path
import re

root = Path('.')

policy = root / 'app/src/main/java/com/stupidsavacan/clocky/customization/font/LegacyWidgetFontFamilyPolicy.kt'
policy.write_text('''package com.stupidsavacan.clocky.customization.font

/** Exact platform family aliases retained by the repository-owned MVP source bundle. */
object LegacyWidgetFontFamilyPolicy {
    const val EXACT_WEIGHT = 400

    private val exactFamilies = setOf(
        "sans-serif-light",
        "sans-serif-rounded",
        "serif",
        "sans-serif-condensed",
        "monospace",
    )

    fun exactFamilyOrNull(fontFamily: String, effectiveWeight: Int): String? =
        fontFamily.takeIf { effectiveWeight == EXACT_WEIGHT && it in exactFamilies }
}
''', encoding='utf-8')

test = root / 'app/src/test/java/com/stupidsavacan/clocky/customization/font/LegacyWidgetFontFamilyPolicyTest.kt'
test.write_text('''package com.stupidsavacan.clocky.customization.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyWidgetFontFamilyPolicyTest {
    @Test
    fun repositoryMvpFamiliesAreExactAtNormalWeight() {
        val families = listOf(
            "sans-serif-light",
            "sans-serif-rounded",
            "serif",
            "sans-serif-condensed",
            "monospace",
        )
        families.forEach { family ->
            assertEquals(family, LegacyWidgetFontFamilyPolicy.exactFamilyOrNull(family, 400))
        }
    }

    @Test
    fun otherFamiliesOrWeightsStayOnExistingRenderer() {
        assertNull(LegacyWidgetFontFamilyPolicy.exactFamilyOrNull("system-sans", 400))
        assertNull(LegacyWidgetFontFamilyPolicy.exactFamilyOrNull("not-a-clocky-family", 400))
        assertNull(LegacyWidgetFontFamilyPolicy.exactFamilyOrNull("serif", 300))
        assertNull(LegacyWidgetFontFamilyPolicy.exactFamilyOrNull("serif", 700))
    }
}
''', encoding='utf-8')

renderer = root / 'app/src/main/java/com/stupidsavacan/clocky/customization/font/DigitalWidgetWeightRenderer.kt'
text = renderer.read_text(encoding='utf-8')

def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected one match, got {count}')
    return source.replace(old, new, 1)

text = replace_once(
    text,
    '''    private val dateViewIds = intArrayOf(
        R.id.date_w100,
        R.id.date_w200,
        R.id.date_w300,
        R.id.date_w400,
        R.id.date_w500,
        R.id.date_w600,
        R.id.date_w700,
        R.id.date_w800,
        R.id.date_w900,
    )
''',
    '''    private val dateViewIds = intArrayOf(
        R.id.date_w100,
        R.id.date_w200,
        R.id.date_w300,
        R.id.date_w400,
        R.id.date_w500,
        R.id.date_w600,
        R.id.date_w700,
        R.id.date_w800,
        R.id.date_w900,
    )

    private val legacyTimeViewIds = intArrayOf(
        R.id.clock_legacy_sans_light,
        R.id.clock_legacy_sans_rounded,
        R.id.clock_legacy_serif,
        R.id.clock_legacy_sans_condensed,
        R.id.clock_legacy_monospace,
    )

    private val legacyDateViewIds = intArrayOf(
        R.id.date_legacy_sans_light,
        R.id.date_legacy_sans_rounded,
        R.id.date_legacy_serif,
        R.id.date_legacy_sans_condensed,
        R.id.date_legacy_monospace,
    )
''',
    'legacy view arrays',
)

text = replace_once(
    text,
    '''        timeLetterSpacing: Float = 0f,
        dateLetterSpacing: Float = 0f,
    ) {
        val sdkInt = Build.VERSION.SDK_INT
        val time = RemoteViewsFontWeightPolicy.resolve(requestedTimeWeight, sdkInt)
        val date = RemoteViewsFontWeightPolicy.resolve(requestedDateWeight, sdkInt)
''',
    '''        timeLetterSpacing: Float = 0f,
        dateLetterSpacing: Float = 0f,
        timeFontFamily: String = "system-sans",
        dateFontFamily: String = "system-sans",
    ) {
        val sdkInt = Build.VERSION.SDK_INT
        val time = RemoteViewsFontWeightPolicy.resolve(requestedTimeWeight, sdkInt)
        val date = RemoteViewsFontWeightPolicy.resolve(requestedDateWeight, sdkInt)
''',
    'remote views family params',
)

text = replace_once(
    text,
    '''        applyGroup(
            remoteViews = remoteViews,
            ids = timeViewIds,
            effectiveWeight = time.effective,
''',
    '''        applyGroup(
            remoteViews = remoteViews,
            ids = timeViewIds,
            legacyIds = legacyTimeViewIds,
            legacySelectedId = legacyViewId(timeFontFamily, time.effective, isDate = false),
            effectiveWeight = time.effective,
''',
    'time legacy group',
)
text = replace_once(
    text,
    '''        applyGroup(
            remoteViews = remoteViews,
            ids = dateViewIds,
            effectiveWeight = date.effective,
''',
    '''        applyGroup(
            remoteViews = remoteViews,
            ids = dateViewIds,
            legacyIds = legacyDateViewIds,
            legacySelectedId = legacyViewId(dateFontFamily, date.effective, isDate = true),
            effectiveWeight = date.effective,
''',
    'date legacy group',
)

text = replace_once(
    text,
    '''        timeLetterSpacing: Float = 0f,
        dateLetterSpacing: Float = 0f,
    ) {
        val sdkInt = Build.VERSION.SDK_INT
        val time = RemoteViewsFontWeightPolicy.resolve(requestedTimeWeight, sdkInt)
        val dateWeight = RemoteViewsFontWeightPolicy.resolve(requestedDateWeight, sdkInt)
        clock.typeface = typefaceFor(time.effective)
        date.typeface = typefaceFor(dateWeight.effective)
''',
    '''        timeLetterSpacing: Float = 0f,
        dateLetterSpacing: Float = 0f,
        timeFontFamily: String = "system-sans",
        dateFontFamily: String = "system-sans",
    ) {
        val sdkInt = Build.VERSION.SDK_INT
        val time = RemoteViewsFontWeightPolicy.resolve(requestedTimeWeight, sdkInt)
        val dateWeight = RemoteViewsFontWeightPolicy.resolve(requestedDateWeight, sdkInt)
        clock.typeface = typefaceFor(time.effective, timeFontFamily)
        date.typeface = typefaceFor(dateWeight.effective, dateFontFamily)
''',
    'sizer family params',
)

text = replace_once(
    text,
    '''        ids: IntArray,
        effectiveWeight: Int,
''',
    '''        ids: IntArray,
        legacyIds: IntArray,
        legacySelectedId: Int?,
        effectiveWeight: Int,
''',
    'applyGroup params',
)

text = replace_once(
    text,
    '''        val selectedIndex = RemoteViewsFontWeightPolicy.canonicalIndex(effectiveWeight)
        val safeLetterSpacing = WidgetLetterSpacingPolicy.normalize(letterSpacing)
        ids.forEachIndexed { index, id ->
            val visible = enabled && index == selectedIndex
            remoteViews.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
            remoteViews.setFloat(id, "setLetterSpacing", safeLetterSpacing)
            if (visible) {
                remoteViews.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, sizePx)
            }
            if (dateFormat != null) {
                remoteViews.setCharSequence(id, "setFormat12Hour", dateFormat)
                remoteViews.setCharSequence(id, "setFormat24Hour", dateFormat)
            }
        }
    }

    private fun typefaceFor(weight: Int): Typeface {
''',
    '''        val selectedIndex = RemoteViewsFontWeightPolicy.canonicalIndex(effectiveWeight)
        val safeLetterSpacing = WidgetLetterSpacingPolicy.normalize(letterSpacing)
        val useLegacyFamily = legacySelectedId != null
        ids.forEachIndexed { index, id ->
            applyView(
                remoteViews,
                id,
                enabled && !useLegacyFamily && index == selectedIndex,
                sizePx,
                dateFormat,
                safeLetterSpacing,
            )
        }
        legacyIds.forEach { id ->
            applyView(
                remoteViews,
                id,
                enabled && id == legacySelectedId,
                sizePx,
                dateFormat,
                safeLetterSpacing,
            )
        }
    }

    private fun applyView(
        remoteViews: RemoteViews,
        id: Int,
        visible: Boolean,
        sizePx: Float,
        dateFormat: CharSequence?,
        letterSpacing: Float,
    ) {
        remoteViews.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
        remoteViews.setFloat(id, "setLetterSpacing", letterSpacing)
        if (visible) {
            remoteViews.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_PX, sizePx)
        }
        if (dateFormat != null) {
            remoteViews.setCharSequence(id, "setFormat12Hour", dateFormat)
            remoteViews.setCharSequence(id, "setFormat24Hour", dateFormat)
        }
    }

    private fun legacyViewId(fontFamily: String, effectiveWeight: Int, isDate: Boolean): Int? {
        return when (LegacyWidgetFontFamilyPolicy.exactFamilyOrNull(fontFamily, effectiveWeight)) {
            "sans-serif-light" -> if (isDate) R.id.date_legacy_sans_light else R.id.clock_legacy_sans_light
            "sans-serif-rounded" -> if (isDate) R.id.date_legacy_sans_rounded else R.id.clock_legacy_sans_rounded
            "serif" -> if (isDate) R.id.date_legacy_serif else R.id.clock_legacy_serif
            "sans-serif-condensed" -> if (isDate) R.id.date_legacy_sans_condensed else R.id.clock_legacy_sans_condensed
            "monospace" -> if (isDate) R.id.date_legacy_monospace else R.id.clock_legacy_monospace
            else -> null
        }
    }

    private fun typefaceFor(weight: Int, fontFamily: String): Typeface {
        LegacyWidgetFontFamilyPolicy.exactFamilyOrNull(fontFamily, weight)?.let { family ->
            return Typeface.create(family, Typeface.NORMAL)
        }
''',
    'group application and typeface',
)
renderer.write_text(text, encoding='utf-8')

provider = root / 'app/src/main/java/com/android/alarmclock/DigitalAppWidgetProvider.kt'
p = provider.read_text(encoding='utf-8')
p = replace_once(
    p,
    '''                    resolvedWeights.dateEnabled,
                    widgetSettings.time.letterSpacing,
                    widgetSettings.date.letterSpacing,
''',
    '''                    resolvedWeights.dateEnabled,
                    widgetSettings.time.fontFamily,
                    widgetSettings.date.fontFamily,
                    widgetSettings.time.letterSpacing,
                    widgetSettings.date.letterSpacing,
''',
    'provider optimize call',
)
p = replace_once(
    p,
    '''                    timeLetterSpacing = widgetSettings.time.letterSpacing,
                    dateLetterSpacing = widgetSettings.date.letterSpacing,
''',
    '''                    timeLetterSpacing = widgetSettings.time.letterSpacing,
                    dateLetterSpacing = widgetSettings.date.letterSpacing,
                    timeFontFamily = widgetSettings.time.fontFamily,
                    dateFontFamily = widgetSettings.date.fontFamily,
''',
    'provider remote render call',
)
p = replace_once(
    p,
    '''            dateEnabled: Boolean,
            timeLetterSpacing: Float,
            dateLetterSpacing: Float,
''',
    '''            dateEnabled: Boolean,
            timeFontFamily: String,
            dateFontFamily: String,
            timeLetterSpacing: Float,
            dateLetterSpacing: Float,
''',
    'provider optimize signature',
)
p = replace_once(
    p,
    '''                    dateEnabled,
                    timeLetterSpacing,
                    dateLetterSpacing,
            )
''',
    '''                    dateEnabled,
                    timeLetterSpacing,
                    dateLetterSpacing,
                    timeFontFamily,
                    dateFontFamily,
            )
''',
    'provider sizer render call',
)
provider.write_text(p, encoding='utf-8')

layout = root / 'app/src/main/res/layout/digital_widget.xml'
x = layout.read_text(encoding='utf-8')
time_views = '''

        <!-- Exact normal-weight platform families retained by the repository MVP source. -->
        <TextClock
            android:id="@+id/clock_legacy_sans_light"
            style="@style/widget_big_thin"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="none"
            android:fontFamily="sans-serif-light"
            android:format12Hour="@string/lock_screen_12_hour_format"
            android:format24Hour="@string/lock_screen_24_hour_format"
            android:includeFontPadding="false"
            android:singleLine="true"
            android:textColor="@color/white"
            android:visibility="gone" />

        <TextClock
            android:id="@+id/clock_legacy_sans_rounded"
            style="@style/widget_big_thin"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="none"
            android:fontFamily="sans-serif-rounded"
            android:format12Hour="@string/lock_screen_12_hour_format"
            android:format24Hour="@string/lock_screen_24_hour_format"
            android:includeFontPadding="false"
            android:singleLine="true"
            android:textColor="@color/white"
            android:visibility="gone" />

        <TextClock
            android:id="@+id/clock_legacy_serif"
            style="@style/widget_big_thin"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="none"
            android:fontFamily="serif"
            android:format12Hour="@string/lock_screen_12_hour_format"
            android:format24Hour="@string/lock_screen_24_hour_format"
            android:includeFontPadding="false"
            android:singleLine="true"
            android:textColor="@color/white"
            android:visibility="gone" />

        <TextClock
            android:id="@+id/clock_legacy_sans_condensed"
            style="@style/widget_big_thin"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="none"
            android:fontFamily="sans-serif-condensed"
            android:format12Hour="@string/lock_screen_12_hour_format"
            android:format24Hour="@string/lock_screen_24_hour_format"
            android:includeFontPadding="false"
            android:singleLine="true"
            android:textColor="@color/white"
            android:visibility="gone" />

        <TextClock
            android:id="@+id/clock_legacy_monospace"
            style="@style/widget_big_thin"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="none"
            android:fontFamily="monospace"
            android:format12Hour="@string/lock_screen_12_hour_format"
            android:format24Hour="@string/lock_screen_24_hour_format"
            android:includeFontPadding="false"
            android:singleLine="true"
            android:textColor="@color/white"
            android:visibility="gone" />'''

date_views = '''

            <!-- Exact normal-weight platform families retained by the repository MVP source. -->
            <TextClock
                android:id="@+id/date_legacy_sans_light"
                style="@style/widget_label"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="none"
                android:fontFamily="sans-serif-light"
                android:includeFontPadding="false"
                android:singleLine="true"
                android:textAllCaps="true"
                android:textColor="@color/white"
                android:visibility="gone" />

            <TextClock
                android:id="@+id/date_legacy_sans_rounded"
                style="@style/widget_label"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="none"
                android:fontFamily="sans-serif-rounded"
                android:includeFontPadding="false"
                android:singleLine="true"
                android:textAllCaps="true"
                android:textColor="@color/white"
                android:visibility="gone" />

            <TextClock
                android:id="@+id/date_legacy_serif"
                style="@style/widget_label"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="none"
                android:fontFamily="serif"
                android:includeFontPadding="false"
                android:singleLine="true"
                android:textAllCaps="true"
                android:textColor="@color/white"
                android:visibility="gone" />

            <TextClock
                android:id="@+id/date_legacy_sans_condensed"
                style="@style/widget_label"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="none"
                android:fontFamily="sans-serif-condensed"
                android:includeFontPadding="false"
                android:singleLine="true"
                android:textAllCaps="true"
                android:textColor="@color/white"
                android:visibility="gone" />

            <TextClock
                android:id="@+id/date_legacy_monospace"
                style="@style/widget_label"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="none"
                android:fontFamily="monospace"
                android:includeFontPadding="false"
                android:singleLine="true"
                android:textAllCaps="true"
                android:textColor="@color/white"
                android:visibility="gone" />'''

x, time_count = re.subn(
    r'(        <TextClock\n            android:id="@\+id/clock_w900".*?android:visibility="gone" />)(\n    </FrameLayout>)',
    lambda match: match.group(1) + time_views + match.group(2),
    x,
    count=1,
    flags=re.S,
)
x, date_count = re.subn(
    r'(            <TextClock\n                android:id="@\+id/date_w900".*?android:visibility="gone" />)(\n        </FrameLayout>)',
    lambda match: match.group(1) + date_views + match.group(2),
    x,
    count=1,
    flags=re.S,
)
if time_count != 1 or date_count != 1:
    raise RuntimeError(f'layout insertion failed: time={time_count}, date={date_count}')
layout.write_text(x, encoding='utf-8')

offset = root / 'app/src/main/java/com/stupidsavacan/clocky/customization/position/DigitalWidgetOffsetRenderer.kt'
o = offset.read_text(encoding='utf-8')
o = replace_once(
    o,
    '''        R.id.clock_w900,
    )
''',
    '''        R.id.clock_w900,
        R.id.clock_legacy_sans_light,
        R.id.clock_legacy_sans_rounded,
        R.id.clock_legacy_serif,
        R.id.clock_legacy_sans_condensed,
        R.id.clock_legacy_monospace,
    )
''',
    'time offset ids',
)
o = replace_once(
    o,
    '''        R.id.date_w900,
    )
''',
    '''        R.id.date_w900,
        R.id.date_legacy_sans_light,
        R.id.date_legacy_sans_rounded,
        R.id.date_legacy_serif,
        R.id.date_legacy_sans_condensed,
        R.id.date_legacy_monospace,
    )
''',
    'date offset ids',
)
offset.write_text(o, encoding='utf-8')

required = [
    renderer,
    provider,
    layout,
    offset,
    policy,
    test,
]
for path in required:
    if not path.exists() or path.stat().st_size == 0:
        raise RuntimeError(f'missing patched file: {path}')

print('patched files:')
for path in required:
    print(path, path.stat().st_size)
