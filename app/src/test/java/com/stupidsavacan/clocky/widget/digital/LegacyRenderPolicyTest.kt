package com.stupidsavacan.clocky.widget.digital

import com.stupidsavacan.clocky.design.model.*
import com.stupidsavacan.clocky.design.resolve.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 3A-1 must not activate responsive render selection before 3A-2. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 34])
class LegacyRenderPolicyTest {
    @Test fun previewAndPlacedPairKeepPhaseTwoCardPolicyAtSquareAndLargeSizes() {
        val context = RuntimeEnvironment.getApplication()
        val d = DigitalDesign(layout = DesignLayout(overrides = mapOf(
            SizeClass.CARD to LayoutPatch(timeSizeSp = 80f),
            SizeClass.SQUARE to LayoutPatch(timeSizeSp = 200f, template = Template.MINIMAL),
            SizeClass.LARGE to LayoutPatch(dateVisible = false),
        )))
        listOf(SizeContext(363,206,667,398), SizeContext(363,281,667,537)).forEach { size ->
            val (spec, preview) = DigitalWidgetUpdater.buildPortrait(context,d,size)
            val expected = DesignResolver.resolve(d,size,DigitalWidgetUpdater.environment(context,true),SizeClass.CARD)
            assertEquals(expected,spec)
            assertEquals(SizeClass.CARD,spec.sizeClass)
            assertEquals(80f,spec.time.sizeSp)
            assertTrue(spec.dateVisible)
            assertNotNull(preview.apply(context,android.widget.FrameLayout(context)))
            assertNotNull(DigitalWidgetUpdater.build(context,WidgetInstance(1,d),size,null)
                .apply(context,android.widget.FrameLayout(context)))
        }
    }

    @Test fun unknownTemplateDisclosureIsVisibleAndRequestRemainsIntact() {
        val context = RuntimeEnvironment.getApplication()
        val d = DigitalDesign(layout = DesignLayout(requestedTemplate = "FUTURE_TEMPLATE"))
        val (spec, _) = DigitalWidgetUpdater.buildPortrait(context,d,SizeContext(363,132,667,260))
        assertTrue(DegradationNotices.describe(context,spec.degradations).any { it.contains("FUTURE_TEMPLATE") })
        assertEquals("FUTURE_TEMPLATE",d.layout.requestedTemplate)
    }
}
