package com.stupidsavacan.clocky.widget.digital

import android.app.PendingIntent
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.AmPmMode
import com.stupidsavacan.clocky.design.model.AmPmStyle
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.Behavior
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.Effects
import com.stupidsavacan.clocky.design.model.InfoElement
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.ShadowLevel
import com.stupidsavacan.clocky.design.model.TapAction
import com.stupidsavacan.clocky.design.model.TapActions
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.FontCatalog
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.ResolvedFace
import com.stupidsavacan.clocky.design.resolve.ShadowVariant
import com.stupidsavacan.clocky.design.resolve.SizeContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 2 composition through the production RemoteViews: Info, AM/PM, rendered background, shadows, taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DigitalWidgetComposerPhase2Test {
    private val context = org.robolectric.RuntimeEnvironment.getApplication()
    private val card = SizeContext(363, 132, 667, 260)
    private val strip = SizeContext(363, 58, 667, 122)

    private fun spec(design: DigitalDesign, size: SizeContext = card, alarm: String? = "Mon 7:30 AM"): ResolvedDigitalSpec =
        DesignResolver.resolve(design, size, DigitalWidgetUpdater.environment(context).copy(nextAlarmText = alarm, supportsLatinAmPmMarker = true))

    private fun applied(
        s: ResolvedDigitalSpec,
        zones: ZoneClicks? = null,
        bounds: Pair<Int, Int>? = null,
    ): View = DigitalWidgetComposer.compose(
        context, s, DigitalWidgetFit.requested(context, s), zones = zones, boundsPx = bounds,
    ).apply(context, FrameLayout(context))

    private fun slot(root: View, id: Int) = root.findViewById<LinearLayout>(id)

    // ---- Info ----

    @Test
    fun nextAlarmInfoIsATextClockWithALiteralPattern() {
        val root = applied(spec(DigitalDesign(info = InfoElement(source = InfoSource.NEXT_ALARM))))
        val infoSlot = slot(root, R.id.clocky_info_slot)
        assertEquals(View.VISIBLE, infoSlot.visibility)
        val face = DigitalWidgetFit.visibleTextIn(root, R.id.clocky_info_slot) as TextClock
        assertEquals("'Mon 7:30 AM'", face.format12Hour.toString())
    }

    @Test
    fun secondTimezoneInfoSetsTheClocksZone() {
        val design = DigitalDesign(info = InfoElement(source = InfoSource.SECOND_TIMEZONE, timeZoneId = "Asia/Tokyo"))
        val face = DigitalWidgetFit.visibleTextIn(applied(spec(design)), R.id.clocky_info_slot) as TextClock
        assertEquals("Asia/Tokyo", face.timeZone)
    }

    @Test
    fun noInfoLeavesTheSlotEmptyAndGoneAndReappliedViewsKeepOneFragment() {
        val root = applied(spec(DigitalDesign()))
        assertEquals(View.GONE, slot(root, R.id.clocky_info_slot).visibility)
        assertEquals(0, slot(root, R.id.clocky_info_slot).childCount)

        val s = spec(DigitalDesign(info = InfoElement(source = InfoSource.NEXT_ALARM)))
        val rv = DigitalWidgetComposer.compose(context, s, DigitalWidgetFit.requested(context, s))
        val live = rv.apply(context, FrameLayout(context))
        repeat(2) { rv.reapply(context, live) }
        assertEquals(1, slot(live, R.id.clocky_info_slot).childCount)
    }

    @Test
    fun stripShowsNoInfoRow() {
        val design = DigitalDesign(info = InfoElement(source = InfoSource.NEXT_ALARM))
        val root = applied(spec(design, strip), bounds = null)
        assertEquals(0, slot(root, R.id.clocky_info_slot).childCount)
    }

    // ---- AM/PM and seconds ----

    @Test
    fun amPmIsASecondTextClockAfterTheTimeUsingTheAmPmPattern() {
        val design = DigitalDesign(behavior = Behavior(amPm = AmPmStyle(AmPmMode.SUFFIX, 0.5f)))
        val root = applied(spec(design))
        val timeSlot = slot(root, R.id.clocky_time_slot)
        assertEquals(2, timeSlot.childCount)
        val faces = DigitalWidgetFit.visibleTextsIn(root, R.id.clocky_time_slot)
        assertEquals(2, faces.size)
        assertEquals("HH", (faces[1] as TextClock).format12Hour.toString())
        assertEquals("", (faces[1] as TextClock).format24Hour.toString())
        assertTrue("AM/PM is smaller than the time", faces[1].textSize < faces[0].textSize)
    }

    @Test
    fun secondsAppearInBothTimeFormats() {
        val design = DigitalDesign(behavior = Behavior(showSeconds = true))
        val time = DigitalWidgetFit.visibleTextIn(applied(spec(design)), R.id.clocky_time_slot) as TextClock
        assertEquals("h:mm:ss", time.format12Hour.toString())
        assertEquals("HH:mm:ss", time.format24Hour.toString())
    }

    @Test
    fun fitCountsTheAmPmAndInfoRows() {
        val plain = spec(DigitalDesign())
        val rich = spec(
            DigitalDesign(
                behavior = Behavior(amPm = AmPmStyle(AmPmMode.SUFFIX, 0.6f)),
                info = InfoElement(source = InfoSource.NEXT_ALARM),
            ),
        )
        val d = context.resources.displayMetrics.density
        val w = (180 * d).toInt()
        val h = (100 * d).toInt()
        val plainFit = DigitalWidgetFit.fit(context, plain, w, h)
        val richFit = DigitalWidgetFit.fit(context, rich, w, h)
        assertTrue("extra rows must shrink the time to fit", richFit.timePx <= plainFit.timePx)
        assertTrue(richFit.infoPx > 0f)
        // The shrink keeps the requested ratio between time and info.
        val requested = DigitalWidgetFit.requested(context, rich)
        assertEquals(requested.infoPx / requested.timePx, richFit.infoPx / richFit.timePx, 0.05f)
    }

    // ---- Rendered backgrounds ----

    @Test
    fun gradientAndOutlineUseABitmapSizedToTheWidget() {
        val gradient = DigitalDesign(
            background = BackgroundElement(
                type = BackgroundType.GRADIENT,
                color = ColorRef.Fixed(0xFF0000),
                gradientEnd = ColorRef.Fixed(0x0000FF),
                gradientAngleDeg = 90,
                opacity = 1f,
            ),
        )
        val root = applied(spec(gradient), bounds = 400 to 200)
        val bg = root.findViewById<ImageView>(R.id.clocky_widget_background)
        assertEquals(View.VISIBLE, bg.visibility)
        val bitmap = (bg.drawable as android.graphics.drawable.BitmapDrawable).bitmap
        assertEquals(400, bitmap.width)
        assertEquals(200, bitmap.height)
        val left = bitmap.getPixel(20, 100)
        val right = bitmap.getPixel(380, 100)
        assertTrue("start color red on the left", (left shr 16 and 0xFF) > (left and 0xFF))
        assertTrue("end color blue on the right", (right and 0xFF) > (right shr 16 and 0xFF))
    }

    @Test
    fun outlineIsStrokeOnlyWithATransparentCenter() {
        val outline = DigitalDesign(
            background = BackgroundElement(type = BackgroundType.OUTLINE, color = ColorRef.Fixed(0x00FF00), borderWidthDp = 4f, opacity = 1f),
        )
        val root = applied(spec(outline), bounds = 400 to 200)
        val bitmap = (root.findViewById<ImageView>(R.id.clocky_widget_background).drawable as android.graphics.drawable.BitmapDrawable).bitmap
        assertEquals(0, bitmap.getPixel(200, 100) ushr 24)
        assertTrue("edge pixels are drawn", bitmap.getPixel(200, 1) ushr 24 > 0)
    }

    @Test
    fun withoutBoundsARenderedBackgroundStaysHiddenSoFitMeasurementIsUnaffected() {
        val gradient = DigitalDesign(background = BackgroundElement(type = BackgroundType.GRADIENT))
        val root = applied(spec(gradient), bounds = null)
        assertEquals(View.GONE, root.findViewById<ImageView>(R.id.clocky_widget_background).visibility)
    }

    @Test
    fun hugeWidgetsAreDrawnBelowTheBitmapBudget() {
        val bg = spec(DigitalDesign(background = BackgroundElement(type = BackgroundType.GRADIENT))).background
        val bitmap: Bitmap = RenderedBackground.create(context, bg, 4000, 3000)!!
        assertTrue(bitmap.width * bitmap.height <= 1_250_000)
        assertEquals(4000f / 3000f, bitmap.width.toFloat() / bitmap.height, 0.02f)
    }

    // ---- Shadows and fonts ----

    @Test
    fun everyFontFragmentInSupportedRangeInflatesAndHasItsFaceView() {
        for (family in FontCatalog.families) {
            for (caps in listOf(false, true)) {
                for (shadow in ShadowVariant.entries) {
                    val weight = family.weights.first()
                    val face = ResolvedFace(family.id, weight)
                    val layout = FontFragments.layoutFor(family.id, caps, shadow)
                    val inflated = android.widget.RemoteViews(context.packageName, layout).apply(context, FrameLayout(context))
                    assertNotNull(
                        "${family.id} caps=$caps $shadow has no face view for $weight",
                        inflated.findViewById<View>(FontFragments.faceViewId(face)) ?: if (inflated.id == FontFragments.faceViewId(face)) inflated else null,
                    )
                }
            }
        }
    }

    @Test
    fun shadowLevelsMapToDistinctPreBuiltFragments() {
        val layouts = ShadowVariant.entries.map { FontFragments.layoutFor("clocky-poppins", false, it) }
        assertEquals(ShadowVariant.entries.size, layouts.toSet().size)
    }

    @Test
    fun offShadowRemovesTheShadowAndStrongAddsOne() {
        fun radiusOf(level: ShadowLevel): Float {
            val s = spec(DigitalDesign(effects = Effects(level), time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0xFFFFFF)))))
            return DigitalWidgetFit.visibleTextIn(applied(s), R.id.clocky_time_slot)!!.shadowRadius
        }
        assertEquals(0f, radiusOf(ShadowLevel.OFF), 0f)
        assertTrue(radiusOf(ShadowLevel.STRONG) > radiusOf(ShadowLevel.SOFT))
        assertTrue("classic keeps the AOSP shadow", radiusOf(ShadowLevel.CLASSIC) > 0f)
    }

    @Test
    fun bundledFontIsAppliedToItsOwnFaceView() {
        val design = DigitalDesign(time = TimeElement(TextStyle(fontId = "clocky-poppins", weight = 500, sizeSp = 64f)))
        val face = DigitalWidgetFit.visibleTextIn(applied(spec(design)), R.id.clocky_time_slot)!!
        assertEquals(R.id.clocky_face_w500, face.id)
    }

    @Test
    fun dateCaseFollowsTheRequest() {
        fun isAllCaps(uppercase: Boolean): Boolean {
            val root = applied(spec(DigitalDesign(date = DateElement(uppercase = uppercase))))
            val face = DigitalWidgetFit.visibleTextIn(root, R.id.clocky_date_slot)!!
            // Single-line text always has a transformation method; only all-caps changes the characters.
            return face.transformationMethod?.getTransformation("aB", face)?.toString() == "AB"
        }
        assertTrue(isAllCaps(true))
        assertFalse(isAllCaps(false))
    }

    // ---- Tap zones ----

    private fun pi(code: Int) = PendingIntent.getActivity(context, code, android.content.Intent("x"), PendingIntent.FLAG_IMMUTABLE)

    @Test
    fun zonesOnlyGetClickTargetsTheyWereGiven() {
        val s = spec(DigitalDesign(info = InfoElement(source = InfoSource.NEXT_ALARM)))
        val root = applied(s, zones = ZoneClicks(root = pi(1), time = pi(2), date = null, info = pi(3)))
        assertTrue(slot(root, R.id.clocky_time_slot).hasOnClickListeners())
        assertFalse(slot(root, R.id.clocky_date_slot).hasOnClickListeners())
        assertTrue(slot(root, R.id.clocky_info_slot).hasOnClickListeners())
    }

    @Test
    fun defaultTapsCreateNoZoneIntentsAndOtherActionsDo() {
        val none = TapIntents.create(context, 7, TapActions())
        assertNotNull(none.root)
        assertNull(none.time)
        assertNull(none.date)
        val custom = TapIntents.create(context, 7, TapActions(time = TapAction.OPEN_TIMER, date = TapAction.NONE, info = TapAction.OPEN_ALARMS))
        assertNotNull(custom.time)
        assertNotNull(custom.date)
        assertNotNull(custom.info)
        val tab = TapIntents.intentFor(context, 7, TapAction.OPEN_STOPWATCH)
        assertEquals("STOPWATCH", tab.getStringExtra(com.android.deskclock.DeskClock.EXTRA_SELECT_TAB))
    }
}
