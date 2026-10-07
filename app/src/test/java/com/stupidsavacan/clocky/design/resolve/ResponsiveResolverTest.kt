package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.*
import org.junit.Assert.*
import org.junit.Test

class ResponsiveResolverTest {
    private val env = RenderEnvironment(34, false, "EEE, MMM d")
    private val size = SizeContext(363, 281, 667, 537)

    @Test fun ownFieldsOverrideCardWhileNullInheritsCardThenBase() {
        val d = DigitalDesign(layout = DesignLayout(overrides = mapOf(
            SizeClass.CARD to LayoutPatch(timeWeight = 700, dateWeight = 300, timeSizeSp = 72f,
                dateVisible = false, timeAlignment = Alignment.START, infoSizeSp = 20f, paddingDp = 12f),
            SizeClass.SQUARE to LayoutPatch(dateVisible = true, timeXDp = 4f),
            SizeClass.LARGE to LayoutPatch(timeSizeSp = 90f),
        )), info = InfoElement(source = InfoSource.SECOND_TIMEZONE))
        val square = DesignResolver.resolve(d, size, env)
        assertEquals(SizeClass.SQUARE, square.sizeClass)
        assertEquals(72f, square.time.sizeSp)
        assertEquals(700, square.time.face.weight)
        assertEquals(300, square.date.face.weight)
        assertEquals(Alignment.START, square.time.alignment)
        assertEquals(4f, square.time.xDp)
        assertTrue(square.dateVisible)
        assertEquals(20f, square.info!!.text.sizeSp)
        assertEquals(12f, square.paddingDp)
        val large = DesignResolver.resolve(d, size.copy(minHeightDp = 206), env)
        assertEquals(SizeClass.LARGE, large.sizeClass)
        assertEquals(90f, large.time.sizeSp)
        assertFalse(large.dateVisible)
        val strip = DesignResolver.resolve(d, size.copy(minHeightDp = 58), env)
        assertEquals(SizeClass.STRIP, strip.sizeClass)
        assertEquals(64f, strip.time.sizeSp)
        assertEquals(400, strip.time.face.weight)
        assertEquals(0f, strip.paddingDp)
    }

    @Test fun everyExistingBuiltinKeepsCardOutputThroughInheritanceOnAllSupportedSdks() {
        BuiltinDesigns.all.forEach { b ->
            assertEquals(1, b.version)
            listOf(23, 28, 34).forEach { sdk ->
                val e = env.copy(sdkInt = sdk)
                val card = DesignResolver.resolve(b.design, size, e, SizeClass.CARD)
                listOf(SizeClass.SQUARE, SizeClass.LARGE).forEach { c ->
                    assertEquals("${b.id} SDK$sdk $c", card.copy(sizeClass = c), DesignResolver.resolve(b.design, size, e, c))
                }
            }
        }
    }

    @Test fun widgetClassIsIndependentOfTheEntryBoundsAndOffsetsRemainRequested() {
        val d = DigitalDesign(layout = DesignLayout(overrides = mapOf(SizeClass.SQUARE to LayoutPatch(timeXDp = 500f, timeYDp = -500f))))
        // Same options choose Square for both entries; fit dimensions are not classifier inputs.
        assertEquals(SizeClass.SQUARE, DesignResolver.sizeClassOf(size))
        listOf(size, size.copy(minWidthDp = 667, maxHeightDp = 281)).forEach {
            assertEquals(SizeClass.SQUARE,DesignResolver.sizeClassOf(it))
            val spec = DesignResolver.resolve(d, it, env, SizeClass.SQUARE)
            assertEquals(SizeClass.SQUARE, spec.sizeClass)
            assertEquals(it.minWidthDp / 2f, spec.time.xDp)
            assertEquals(-it.minHeightDp / 2f, spec.time.yDp)
            assertTrue(spec.degradations.any { note -> note is Degradation.OffsetClamped })
        }
        assertEquals(500f, d.layout.patchFor(SizeClass.SQUARE).timeXDp)
        assertEquals(0f, DesignResolver.resolve(d, size, env.copy(sdkInt = 28)).time.xDp)
    }

    @Test fun newInfoLayoutFieldsVisibilityAndGapResolveWithoutChangingIdentity() {
        val d = DigitalDesign(info = InfoElement(source = InfoSource.SECOND_TIMEZONE), layout = DesignLayout(overrides = mapOf(
            SizeClass.LARGE to LayoutPatch(infoSizeSp = 18f, infoXDp = 3f, infoYDp = 5f,
                infoAlignment = Alignment.END, dateGapDp = -4f, paddingDp = 8f),
        )))
        val s = DesignResolver.resolve(d, size, env, SizeClass.LARGE)
        assertEquals(18f, s.info!!.text.sizeSp)
        assertEquals(3f, s.info.text.xDp)
        assertEquals(5f, s.info.text.yDp)
        assertEquals(Alignment.END, s.info.text.alignment)
        assertEquals(-4f, s.gapDp)
        assertEquals(8f, s.paddingDp)
        assertEquals(d.behavior.tap, s.taps)
        assertNull(DesignResolver.resolve(d.copy(layout = d.layout.withPatch(SizeClass.LARGE,
            d.layout.patchFor(SizeClass.LARGE).copy(infoVisible = false))), size, env, SizeClass.LARGE).info)
    }
}
