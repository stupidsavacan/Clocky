package com.stupidsavacan.clocky.widget.digital

import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import org.robolectric.RuntimeEnvironment
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.CornerRadius
import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DesignLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.SizeContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Applies the production RemoteViews in-process (the same path PreviewHost uses). This proves the
 * actions are remotable and the composition is correct; launcher rendering still needs devices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 34])
class DigitalWidgetComposerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val card = SizeContext(363, 132, 667, 260)
    private val strip = SizeContext(363, 58, 667, 122)

    private fun spec(design: DigitalDesign, size: SizeContext = card): ResolvedDigitalSpec =
        DesignResolver.resolve(design, size, DigitalWidgetUpdater.environment(context))

    private fun applied(design: DigitalDesign, size: SizeContext = card): View {
        val s = spec(design, size)
        return DigitalWidgetComposer.compose(context, s, DigitalWidgetFit.requested(context, s))
            .apply(context, FrameLayout(context))
    }

    private fun slot(root: View, id: Int) = root.findViewById<LinearLayout>(id)

    /** LinearLayout.getGravity() is API 24+; the field is what setGravity wrote on every API. */
    private fun gravityOf(layout: LinearLayout): Int =
        LinearLayout::class.java.getDeclaredField("mGravity").apply { isAccessible = true }.getInt(layout) and
            (Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK or Gravity.VERTICAL_GRAVITY_MASK)

    private fun visibleFace(root: View, slotId: Int): TextView {
        val face = DigitalWidgetFit.visibleTextIn(root, slotId)
        assertNotNull("slot must contain a visible face", face)
        return face!!
    }

    @Test
    fun eachVisibleElementGetsExactlyOneFragmentEvenWhenReapplied() {
        val s = spec(DigitalDesign())
        val rv = DigitalWidgetComposer.compose(context, s, DigitalWidgetFit.requested(context, s))
        val root = rv.apply(context, FrameLayout(context))
        repeat(2) { rv.reapply(context, root) }
        assertEquals(1, slot(root, R.id.clocky_time_slot).childCount)
        assertEquals(1, slot(root, R.id.clocky_date_slot).childCount)
    }

    @Test
    fun selectsTheResolvedWeightFaceAndHidesTheRest() {
        val root = applied(DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, weight = 700))))
        val face = visibleFace(root, R.id.clocky_time_slot)
        assertTrue(face is TextClock)
        assertEquals(R.id.clocky_face_w700, face.id)
        val fragment = slot(root, R.id.clocky_time_slot).getChildAt(0) as ViewGroup
        val visible = (0 until fragment.childCount).count { fragment.getChildAt(it).visibility == View.VISIBLE }
        assertEquals(1, visible)
    }

    @Test
    fun legacyFamilyUsesItsSingleFaceFragment() {
        val root = applied(DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, fontId = "monospace"))))
        val face = visibleFace(root, R.id.clocky_time_slot)
        assertEquals(R.id.clocky_face_single, face.id)
        assertEquals(Typeface.MONOSPACE, face.typeface)
    }

    @Test
    fun appliesColorSizeSpacingAndFormats() {
        val design = DigitalDesign(
            time = TimeElement(
                TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0x336699), opacity = 0.5f, letterSpacingEm = 0.1f),
            ),
            date = DateElement(formatPattern = "yyyy.MM.dd"),
        )
        val s = spec(design)
        val root = DigitalWidgetComposer.compose(context, s, FitSizes(timePx = 100f, datePx = 20f))
            .apply(context, FrameLayout(context))
        val time = visibleFace(root, R.id.clocky_time_slot) as TextClock
        assertEquals(0x80336699.toInt(), time.currentTextColor)
        assertEquals(100f, time.textSize)
        assertEquals(0.1f, time.letterSpacing, 1e-6f)
        assertEquals("h:mm", time.format12Hour.toString())
        assertEquals("HH:mm", time.format24Hour.toString())

        val date = visibleFace(root, R.id.clocky_date_slot) as TextClock
        assertEquals(20f, date.textSize)
        assertEquals("yyyy.MM.dd", date.format12Hour.toString())
        assertNotNull("date keeps the uppercase style of the Phase 0 widget", date.transformationMethod)
    }

    @Test
    fun alignmentBecomesSlotGravity() {
        val root = applied(
            DigitalDesign(
                time = TimeElement(TextStyle(sizeSp = 64f, alignment = Alignment.START)),
                date = DateElement(style = TextStyle(sizeSp = 14f, alignment = Alignment.END)),
            ),
        )
        assertEquals(Gravity.START or Gravity.CENTER_VERTICAL, gravityOf(slot(root, R.id.clocky_time_slot)))
        assertEquals(Gravity.END or Gravity.CENTER_VERTICAL, gravityOf(slot(root, R.id.clocky_date_slot)))
    }

    @Test
    fun hiddenDateAddsNoFragment() {
        val design = DigitalDesign(
            layout = DesignLayout(overrides = mapOf(SizeClass.STRIP to LayoutPatch(dateVisible = false))),
        )
        val stripRoot = applied(design, strip)
        assertEquals(View.GONE, slot(stripRoot, R.id.clocky_date_slot).visibility)
        assertEquals(0, slot(stripRoot, R.id.clocky_date_slot).childCount)
        assertEquals(1, slot(applied(design, card), R.id.clocky_date_slot).childCount)
    }

    @Test
    fun noneBackgroundIsGoneAndSolidIsTintedFadedAndPadded() {
        val none = applied(DigitalDesign())
        assertEquals(View.GONE, none.findViewById<ImageView>(R.id.clocky_widget_background).visibility)

        val solid = applied(
            DigitalDesign(
                background = BackgroundElement(
                    type = BackgroundType.SOLID,
                    color = ColorRef.Fixed(0x111111),
                    opacity = 0.9f,
                    cornerRadius = CornerRadius.Dp(24f),
                    paddingDp = 10f,
                ),
            ),
        )
        val bg = solid.findViewById<ImageView>(R.id.clocky_widget_background)
        assertEquals(View.VISIBLE, bg.visibility)
        assertEquals(230, bg.imageAlpha)
        assertNotNull(bg.colorFilter)
        val padPx = (10f * context.resources.displayMetrics.density).toInt()
        assertEquals(padPx, solid.findViewById<View>(R.id.clocky_widget_content).paddingTop)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue("API 31+ clips to the RemoteViews outline radius", bg.clipToOutline)
        }
    }

    @Test
    fun fitKeepsRequestedSizesWhenTheyFit() {
        val s = spec(DigitalDesign())
        assertEquals(DigitalWidgetFit.requested(context, s), DigitalWidgetFit.fit(context, s, 5000, 5000))
        assertEquals(DigitalWidgetFit.requested(context, s), DigitalWidgetFit.fit(context, s, 0, 0))
    }

    /** Real text metrics need Robolectric native graphics (API 26+); legacy mode measures ~0. */
    @Test
    @Config(sdk = [28, 34])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun fitShrinksTimeAndDateTogetherIntoSmallBounds() {
        val s = spec(DigitalDesign(time = TimeElement(TextStyle(sizeSp = 200f))))
        val requested = DigitalWidgetFit.requested(context, s)
        val fitted = DigitalWidgetFit.fit(context, s, 300, 120)
        assertTrue(fitted.timePx < requested.timePx)
        val requestedRatio = requested.timePx / requested.datePx
        // Common scale; only whole-px rounding of the small date size perturbs the ratio.
        assertEquals(requestedRatio, fitted.timePx / fitted.datePx, requestedRatio * 0.1f)

        val root = DigitalWidgetComposer.compose(context, s, fitted).apply(context, FrameLayout(context))
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        root.measure(unspecified, unspecified)
        assertTrue("fitted widget height ${root.measuredHeight} must fit 120px", root.measuredHeight <= 120)
    }
}
