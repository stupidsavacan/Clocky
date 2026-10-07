package com.stupidsavacan.clocky.studio

import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.AmPmMode
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TapAction
import com.stupidsavacan.clocky.design.model.TextSizeStep
import com.stupidsavacan.clocky.design.model.ThemeMode
import com.stupidsavacan.clocky.design.model.normalized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignEditsTest {
    private val phase1a = DigitalDesign()
    private val builtin = BuiltinDesigns.default.instantiate()
    private val strip = EditScope(SizeClass.STRIP)

    @Test
    fun scopeAllWritesTheBaseAndScopeClassWritesOnlyThatOverride() {
        val all = DesignEdits.setSize(phase1a, TextTarget.TIME, 80f, EditScope.ALL)
        assertEquals(80f, all.time.style.sizeSp, 0f)
        assertTrue(all.layout.overrides.isEmpty())

        val one = DesignEdits.setSize(phase1a, TextTarget.TIME, 40f, strip)
        assertEquals("base untouched", phase1a.time.style.sizeSp, one.time.style.sizeSp, 0f)
        assertEquals(40f, one.layout.patchFor(SizeClass.STRIP).timeSizeSp)
        assertNull(one.layout.patchFor(SizeClass.CARD).timeSizeSp)
        assertEquals(40f, DesignEdits.sizeOf(one, TextTarget.TIME, strip), 0f)
        assertEquals(64f, DesignEdits.sizeOf(one, TextTarget.TIME, EditScope(SizeClass.CARD)), 0f)
    }

    @Test
    fun revertingAnOverrideRestoresInheritanceAndKeepsStorageMinimal() {
        val overridden = DesignEdits.setWeight(phase1a, TextTarget.DATE, 700, strip)
        assertTrue(DesignEdits.isOverridden(overridden, SizeClass.STRIP, DesignEdits.OverrideField.DATE_WEIGHT))
        val reverted = DesignEdits.revertOverride(overridden, SizeClass.STRIP, DesignEdits.OverrideField.DATE_WEIGHT)
        assertTrue("an empty patch is removed", reverted.layout.overrides.isEmpty())
        assertEquals(phase1a, reverted)
    }

    @Test
    fun valuesAreClampedToTheirDocumentedRanges() {
        assertEquals(256f, DesignEdits.setSize(phase1a, TextTarget.TIME, 9999f, EditScope.ALL).time.style.sizeSp, 0f)
        assertEquals(900, DesignEdits.setWeight(phase1a, TextTarget.TIME, 1200, EditScope.ALL).time.style.weight)
        assertEquals(0.5f, DesignEdits.setLetterSpacing(phase1a, TextTarget.TIME, 3f).time.style.letterSpacingEm, 0f)
        assertEquals(0.6f, DesignEdits.setAmPmScalePercent(phase1a, 99).behavior.amPm.scale, 0.0001f)
        assertEquals(4f, DesignEdits.setBorderWidth(phase1a, 40f).background.borderWidthDp, 0f)
        assertEquals(315, DesignEdits.setGradientAngle(phase1a, -45).background.gradientAngleDeg)
    }

    @Test
    fun editingATokenFontMakesItExplicitButKeepsThePresetsWeight() {
        val tokenDate = builtin.date.style
        assertTrue(FontIds.isToken(tokenDate.fontId))
        val presetWeight = DesignEdits.weightOf(builtin, TextTarget.DATE, EditScope.ALL)

        val changed = DesignEdits.setFont(builtin, TextTarget.DATE, "clocky-poppins")
        assertEquals("clocky-poppins", changed.date.style.fontId)
        assertEquals(presetWeight, changed.date.style.weight)
        assertEquals("the other element still follows its token", builtin.time.style.fontId, changed.time.style.fontId)
        assertNotNull(changed.style)
    }

    @Test
    fun enablingInfoOnATokenlessDesignCopiesTheDatesLookSoItIsReadable() {
        val dateColor = ColorRef.Fixed(0x202020)
        val light = phase1a.copy(date = phase1a.date.copy(style = phase1a.date.style.copy(color = dateColor)))
        val withInfo = DesignEdits.setInfoSource(light, InfoSource.NEXT_ALARM)
        assertEquals(dateColor, withInfo.info.style.color)

        // A tokened design keeps the secondary token, and switching Info off then on does not re-copy.
        val tokened = DesignEdits.setInfoSource(builtin, InfoSource.NEXT_ALARM)
        assertEquals(builtin.info.style, tokened.info.style)
    }

    @Test
    fun prepareFoldsTheQuickTuneStepIntoRealSizesAndKeepsTheTokens() {
        val large = builtin.copy(style = builtin.style!!.copy(textSize = TextSizeStep.LARGE))
        val prepared = DesignEdits.prepare(large)
        assertEquals(large.time.style.sizeSp * 1.2f, prepared.time.style.sizeSp, 0.001f)
        assertEquals(TextSizeStep.MEDIUM, prepared.style!!.textSize)
        assertEquals(large.style!!.palette, prepared.style!!.palette)
        assertEquals(large.style!!.themeMode, prepared.style!!.themeMode)
        assertEquals("medium is already real", builtin, DesignEdits.prepare(builtin))
    }

    @Test
    fun resetSlotRestoresOnlyThatSlot() {
        var d = builtin
        d = DesignEdits.setBackgroundType(d, BackgroundType.GRADIENT)
        d = DesignEdits.setSize(d, TextTarget.TIME, 100f, EditScope.ALL)
        d = DesignEdits.setHourMode(d, com.stupidsavacan.clocky.design.model.HourMode.FORCE_24_HOUR)

        val bgReset = DesignEdits.resetSlot(d, Slot.BACKGROUND, builtin)
        assertEquals(builtin.background, bgReset.background)
        assertEquals(100f, bgReset.time.style.sizeSp, 0f)

        val timeReset = DesignEdits.resetSlot(d, Slot.TIME, builtin)
        assertEquals(builtin.time.style.sizeSp, timeReset.time.style.sizeSp, 0f)
        assertEquals(BackgroundType.GRADIENT, timeReset.background.type)
        assertEquals(com.stupidsavacan.clocky.design.model.HourMode.FORCE_24_HOUR, timeReset.behavior.hourMode)
    }

    @Test
    fun layoutResetAlsoRestoresPlacementButNotTypography() {
        var d = DesignEdits.setAlignment(builtin, TextTarget.TIME, Alignment.START)
        d = DesignEdits.setOffset(d, TextTarget.DATE, 12f, -6f, EditScope.ALL)
        d = DesignEdits.setSize(d, TextTarget.TIME, 90f, EditScope.ALL)
        val reset = DesignEdits.resetSlot(d, Slot.LAYOUT, builtin)
        assertEquals(builtin.time.style.alignment, reset.time.style.alignment)
        assertEquals(builtin.date.style.xDp, reset.date.style.xDp, 0f)
        assertEquals(90f, reset.time.style.sizeSp, 0f)
    }

    @Test
    fun slotModifiedIsFalseOnAnUntouchedDesignAndTrueAfterAnEdit() {
        assertFalse(Slot.entries.any { DesignEdits.isSlotModified(builtin, it, builtin) })
        val d = DesignEdits.setAmPmMode(builtin, AmPmMode.SUFFIX)
        assertTrue(DesignEdits.isSlotModified(d, Slot.BEHAVIOR, builtin))
        assertFalse(DesignEdits.isSlotModified(d, Slot.TIME, builtin))
    }

    @Test
    fun themeModeNeedsTokens() {
        assertEquals(phase1a, DesignEdits.setThemeMode(phase1a, ThemeMode.MATERIAL_YOU))
        assertEquals(ThemeMode.MATERIAL_YOU, DesignEdits.setThemeMode(builtin, ThemeMode.MATERIAL_YOU).style!!.themeMode)
    }

    @Test
    fun tapZonesAreIndependent() {
        val d = DesignEdits.setTap(phase1a, TapZone.DATE, TapAction.OPEN_CALENDAR)
        assertEquals(TapAction.OPEN_CALENDAR, d.behavior.tap.date)
        assertEquals(TapAction.OPEN_CLOCKY, d.behavior.tap.time)
    }

    @Test
    fun everyEditKeepsTheDesignNormalizable() {
        var d = builtin
        d = DesignEdits.setCornerRadius(d, com.stupidsavacan.clocky.design.model.CornerRadius.Dp(500f))
        d = DesignEdits.setPadding(d, -3f)
        d = DesignEdits.setInfoLabel(d, "x".repeat(80))
        assertEquals(d.normalized().background.cornerRadius, d.background.cornerRadius)
        assertEquals(24, d.info.label!!.length)
    }

    @Test
    fun presetReferenceExistsOnlyForBuiltinDerivedDesigns() {
        assertNotNull(DesignEdits.presetReference(builtin))
        assertNull(DesignEdits.presetReference(phase1a))
    }
}
