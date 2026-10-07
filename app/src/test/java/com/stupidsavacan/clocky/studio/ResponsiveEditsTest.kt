package com.stupidsavacan.clocky.studio

import com.stupidsavacan.clocky.design.model.*
import org.junit.Assert.*
import org.junit.Test

class ResponsiveEditsTest {
    private val base = DigitalDesign(layout = DesignLayout(overrides = mapOf(
        SizeClass.CARD to LayoutPatch(timeSizeSp = 70f, infoSizeSp = 18f, paddingDp = 12f),
    ), preservedOverrides = mapOf("poster" to "{\"future\":true}")))

    @Test fun allFourClassesCanEditAndRevertEveryPermittedLayoutField() {
        SizeClass.entries.forEach { c ->
            val scope = EditScope(c)
            var d = base
            d = DesignEdits.setSize(d, TextTarget.TIME, 90f, scope)
            d = DesignEdits.setSize(d, TextTarget.DATE, 20f, scope)
            d = DesignEdits.setSize(d, TextTarget.INFO, 22f, scope)
            TextTarget.entries.forEach {
                d = DesignEdits.setOffset(d, it, 8f, -4f, scope)
                d = DesignEdits.setAlignment(d, it, Alignment.END, scope)
            }
            d = DesignEdits.setDateVisible(d, false, scope)
            d = DesignEdits.setInfoVisible(d, false, scope)
            d = DesignEdits.setPadding(d, 16f, scope)
            d = DesignEdits.setDateGap(d, -3f, scope)
            d = DesignEdits.setTemplate(d, Template.MINIMAL, scope)
            val fields = DesignEdits.OverrideField.entries.filter { it != DesignEdits.OverrideField.TIME_WEIGHT && it != DesignEdits.OverrideField.DATE_WEIGHT }
            fields.forEach { assertTrue("$c $it", DesignEdits.isOverridden(d, c, it)) }
            assertEquals(90f, DesignEdits.sizeOf(d, TextTarget.TIME, scope))
            assertEquals(22f, DesignEdits.sizeOf(d, TextTarget.INFO, scope))
            assertEquals(8f to -4f, DesignEdits.offsetOf(d, TextTarget.INFO, scope))
            assertEquals(Alignment.END, DesignEdits.alignmentOf(d, TextTarget.INFO, scope))
            assertFalse(DesignEdits.infoVisibleOf(d, scope))
            assertFalse(DesignEdits.dateVisibleOf(d, scope))
            assertEquals(16f, DesignEdits.paddingOf(d, scope))
            assertEquals(-3f, DesignEdits.dateGapOf(d, scope))
            fields.forEach { d = DesignEdits.revertOverride(d, c, it); assertFalse(DesignEdits.isOverridden(d, c, it)) }
            assertEquals(base.time, d.time)
            assertEquals(base.info, d.info)
            assertEquals(base.layout.preservedOverrides, d.layout.preservedOverrides)
            val inheritedTime = if (c == SizeClass.SQUARE || c == SizeClass.LARGE) 70f else 64f
            assertEquals(inheritedTime, DesignEdits.sizeOf(d, TextTarget.TIME, scope))
        }
    }

    @Test fun explicitOverridesAreOwnOnlyAndRevertRestoresCardInheritance() {
        val scope = EditScope(SizeClass.SQUARE)
        assertEquals(70f, DesignEdits.sizeOf(base, TextTarget.TIME, scope))
        assertFalse(DesignEdits.isOverridden(base, SizeClass.SQUARE, DesignEdits.OverrideField.TIME_SIZE))
        val edited = DesignEdits.setSize(base, TextTarget.TIME, 88f, scope)
        assertEquals(88f, DesignEdits.sizeOf(edited, TextTarget.TIME, scope))
        val reverted = DesignEdits.revertOverride(edited, SizeClass.SQUARE, DesignEdits.OverrideField.TIME_SIZE)
        assertEquals(base, reverted)
    }

    @Test fun legacyWeightEditsRemainAndSquareLargeCreationIsRejected() {
        listOf(SizeClass.STRIP, SizeClass.CARD).forEach { c ->
            val d = DesignEdits.setWeight(base, TextTarget.TIME, 700, EditScope(c))
            assertEquals(700, d.layout.patchFor(c).timeWeight)
            assertEquals(700, DesignEdits.weightOf(d, TextTarget.TIME, EditScope(c)))
        }
        listOf(SizeClass.SQUARE, SizeClass.LARGE).forEach { c ->
            assertThrows(IllegalArgumentException::class.java) { DesignEdits.setWeight(base, TextTarget.TIME, 700, EditScope(c)) }
            assertThrows(IllegalArgumentException::class.java) { DesignEdits.setWeight(base, TextTarget.DATE, 700, EditScope(c)) }
            assertThrows(IllegalArgumentException::class.java) { base.layout.withPatch(c, LayoutPatch(timeWeight = 700)) }
        }
    }

    @Test fun editApiKeepsOffsetRequestsAndUnknownPayloads() {
        val layout = base.layout.withPatch(SizeClass.LARGE, LayoutPatch(preserved = mapOf("future.path" to "[1,null,{\"x\":true}]")))
        val d = DesignEdits.setOffset(base.copy(layout = layout), TextTarget.INFO, 500f, -500f, EditScope(SizeClass.LARGE))
        assertEquals(500f, d.layout.patchFor(SizeClass.LARGE).infoXDp)
        assertEquals(layout.patchFor(SizeClass.LARGE).preserved, d.layout.patchFor(SizeClass.LARGE).preserved)
        assertEquals(base.layout.preservedOverrides, d.layout.preservedOverrides)
    }
}
