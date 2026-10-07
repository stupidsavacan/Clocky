package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.InfoElement
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val card = SizeContext(363, 132, 667, 260)
    private val env = RenderEnvironment(sdkInt = 34, isRtl = false, localeAutoDatePattern = "EEE, MMM d", nextAlarmText = "Mon 7:30 AM")

    private fun resolve(design: DigitalDesign) = DesignResolver.resolve(design, card, env)

    @Test
    fun blackAndWhiteIsTwentyOneToOne() {
        assertEquals(21.0, Contrast.ratio(0xFFFFFF, 0x000000), 0.01)
        assertEquals(1.0, Contrast.ratio(0x777777, 0x777777), 0.001)
    }

    @Test
    fun noBackgroundAndNoWallpaperHintIsUnknownNotOkay() {
        val findings = ContrastChecker.check(resolve(DigitalDesign()))
        assertTrue(findings.all { it.level == ContrastLevel.UNKNOWN })
        assertTrue(findings.all { it.ratio == null && it.suggestedRgb == null })
    }

    @Test
    fun opaqueOwnBackgroundGivesAKnownResult() {
        val design = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0x888888))),
            background = BackgroundElement(type = BackgroundType.SOLID, color = ColorRef.Fixed(0x999999), opacity = 1f),
        )
        val time = ContrastChecker.check(resolve(design)).first { it.element == TextElementKind.TIME }
        assertEquals(ContrastLevel.KNOWN_POOR, time.level)
        assertNotNull(time.suggestedRgb)
    }

    @Test
    fun translucentBackgroundOnlyEverProducesALikelyRisk() {
        val design = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0x888888))),
            background = BackgroundElement(type = BackgroundType.SOLID, color = ColorRef.Fixed(0x999999), opacity = 0.5f),
        )
        val time = ContrastChecker.check(resolve(design)).first { it.element == TextElementKind.TIME }
        assertEquals(ContrastLevel.LIKELY_RISK, time.level)
    }

    @Test
    fun wallpaperHintIsAnEstimateAndNeverKnown() {
        val design = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0xFFFFFF))))
        val onWhite = ContrastChecker.check(resolve(design), WallpaperHint(0xFFFFFF)).first()
        assertEquals(ContrastLevel.LIKELY_RISK, onWhite.level)
        val onBlack = ContrastChecker.check(resolve(design), WallpaperHint(0x000000)).first()
        assertEquals(ContrastLevel.OK, onBlack.level)
    }

    @Test
    fun outlineOnlyHasNoFillToMeasureAgainst() {
        val design = DigitalDesign(background = BackgroundElement(type = BackgroundType.OUTLINE))
        assertTrue(ContrastChecker.check(resolve(design)).all { it.level == ContrastLevel.UNKNOWN })
    }

    @Test
    fun smallTextNeedsMoreContrastThanTheTime() {
        // ~3.5:1 passes for the large time but not for the date or info.
        val design = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0x808080))),
            date = com.stupidsavacan.clocky.design.model.DateElement(style = TextStyle(sizeSp = 14f, color = ColorRef.Fixed(0x808080))),
            info = InfoElement(source = InfoSource.NEXT_ALARM, style = TextStyle(sizeSp = 12f, color = ColorRef.Fixed(0x808080))),
            background = BackgroundElement(type = BackgroundType.SOLID, color = ColorRef.Fixed(0x000000), opacity = 1f),
        )
        val byElement = ContrastChecker.check(resolve(design)).associateBy { it.element }
        assertEquals(ContrastLevel.OK, byElement.getValue(TextElementKind.TIME).level)
        assertEquals(ContrastLevel.OK, byElement.getValue(TextElementKind.DATE).level) // 0x808080 on black is ~5.3
        val dim = design.copy(date = design.date.copy(style = design.date.style.copy(color = ColorRef.Fixed(0x555555))))
        val dateFinding = ContrastChecker.check(resolve(dim)).first { it.element == TextElementKind.DATE }
        assertEquals(ContrastLevel.KNOWN_POOR, dateFinding.level)
    }

    @Test
    fun textOpacityLowersTheMeasuredRatio() {
        val solid = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0xFFFFFF), opacity = 0.3f)),
            background = BackgroundElement(type = BackgroundType.SOLID, color = ColorRef.Fixed(0x000000), opacity = 1f),
        )
        val finding = ContrastChecker.check(resolve(solid)).first()
        assertEquals(ContrastLevel.KNOWN_POOR, finding.level)
        assertNull(ContrastChecker.warnings(resolve(solid.copy(time = solid.time.copy(style = solid.time.style.copy(opacity = 1f))))).firstOrNull())
    }

    @Test
    fun gradientIsJudgedByItsWorstStop() {
        val design = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0xFFFFFF))),
            background = BackgroundElement(
                type = BackgroundType.GRADIENT,
                color = ColorRef.Fixed(0x000000),
                gradientEnd = ColorRef.Fixed(0xEEEEEE),
                opacity = 1f,
            ),
        )
        assertEquals(ContrastLevel.KNOWN_POOR, ContrastChecker.check(resolve(design)).first().level)
    }
}
