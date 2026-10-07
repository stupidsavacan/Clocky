package com.stupidsavacan.clocky.widget.digital

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextClock
import com.android.deskclock.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 31, 34, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AmPmMarkerFontTest {
    @Test fun allHoursShapeAsLatinMarkersInsideAppliedRemoteViews() {
        val context = RuntimeEnvironment.getApplication()
        // Compare outlines without a shadow: separate letter glyphs and one composite glyph
        // can cast different overlapping shadows even when their foreground pixels are equal.
        val rv = RemoteViews(context.packageName, R.layout.clocky_face_ampm_marker_off)
        rv.setCharSequence(R.id.clocky_ampm_marker, "setFormat12Hour", "HH")
        rv.setCharSequence(R.id.clocky_ampm_marker, "setFormat24Hour", "HH")
        val clock = rv.apply(context, FrameLayout(context)) as TextClock
        assertEquals("HH", clock.format12Hour.toString())
        assertEquals("HH", clock.format24Hour.toString())
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, clock.importantForAccessibility)
        clock.paint.textSize = 48f
        clock.paint.color = Color.WHITE
        fun pixels(text: String): IntArray {
            val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            // Shape with the script's direction. Raw drawText's inferred direction produced
            // a reversed Arabic hour in Robolectric 26–34, unlike the native moto TextClock.
            val isRtl = text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }
            Canvas(bitmap).drawTextRun(text, 0, text.length, 0, text.length, 10f, 70f, isRtl, clock.paint)
            return IntArray(200 * 100).also { bitmap.getPixels(it, 0, 200, 0, 0, 200, 100) }
        }
        for (spacing in listOf(0f, -0.2f, 0.5f)) {
            clock.letterSpacing = spacing
            for (hour in 0..23) {
                val digits = "%02d".format(java.util.Locale.ROOT, hour)
                val label = if (hour < 12) "AM" else "PM"
                // At zero spacing the composite is identical to its source letter outlines.
                if (spacing == 0f) assertArrayEquals("hour $hour", pixels(label), pixels(digits))
                val am = clock.paint.measureText("00")
                val pm = clock.paint.measureText("12")
                assertEquals("hour $hour, spacing $spacing", if (hour < 12) am else pm, clock.paint.measureText(digits), 0.001f)
                assertTrue("must draw a marker", pixels(digits).any { it != 0 })
                assertArrayEquals("all hours retain substitution at spacing $spacing", pixels(if (hour < 12) "00" else "12"), pixels(digits))
                val arabic = digits.map { ('\u0660'.code + (it - '0')).toChar() }.joinToString("")
                if (spacing == 0f) assertArrayEquals("localized hour=$hour", pixels(digits), pixels(arabic))
                // Arabic shaping may suppress tracking/shift the run. Compare against its own
                // AM/PM representatives at nonzero spacing; cross-script positions are not equal.
                val arabicReference = if (hour < 12) "\u0660\u0660" else "\u0661\u0662"
                assertArrayEquals("localized substitution hour=$hour spacing=$spacing", pixels(arabicReference), pixels(arabic))
            }
        }
    }

    @Test fun hourPatternsInHostLocalesRemainTwoDecimalDigits() {
        val previous = java.util.Locale.getDefault()
        try {
            for (tag in listOf("ja-JP", "ar-EG", "fa-IR", "hi-IN", "th-TH")) {
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
                val cal = java.util.Calendar.getInstance()
                for (hour in 0..23) {
                    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                    val text = android.text.format.DateFormat.format("HH", cal).toString()
                    assertEquals(2, text.length)
                    assertEquals(hour, text.fold(0) { value, digit -> value * 10 + Character.digit(digit, 10) })
                }
            }
        } finally { java.util.Locale.setDefault(previous) }
    }
}
