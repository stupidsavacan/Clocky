package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.DateElement
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.InfoElement
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Requested font + host capability -> effective font (+ degradation). Pure resolver, no Android host. */
class HostFontResolverTest {
    private val card = SizeContext(363, 132, 667, 260)

    private fun env(sdk: Int = 34, bundled: Boolean) = RenderEnvironment(
        sdk, isRtl = false, localeAutoDatePattern = "EEE, MMM d", nextAlarmText = "Mon 7:30 AM", supportsBundledFonts = bundled,
    )

    private fun designWith(font: String, weight: Int = 400): DigitalDesign {
        val style = TextStyle(fontId = font, weight = weight, sizeSp = 40f)
        return DigitalDesign(
            time = TimeElement(style),
            date = DateElement(style = style),
            info = InfoElement(source = InfoSource.NEXT_ALARM, style = style),
        )
    }

    private fun faces(spec: ResolvedDigitalSpec) = listOf(spec.time.face, spec.date.face, spec.info!!.text.face)

    @Test fun supportedHostShowsTheRequestedBundledFace() {
        for (id in FontCatalog.bundledIds) {
            val spec = DesignResolver.resolve(designWith(id), card, env(bundled = true))
            faces(spec).forEach { assertEquals(id, it.fontId) }
            assertTrue(spec.degradations.none { it is Degradation.FontFallback })
        }
    }

    @Test fun unsupportedHostFallsBackToSystemSansDisclosesAndKeepsTheRequest() {
        for (id in FontCatalog.bundledIds) {
            val design = designWith(id)
            val before = design.copy()
            val spec = DesignResolver.resolve(design, card, env(bundled = false))
            faces(spec).forEach { assertEquals(FontIds.SYSTEM_SANS, it.fontId) }
            val notes = spec.degradations.filterIsInstance<Degradation.FontFallback>()
            // Time, Date and Info all go through the same rule.
            assertEquals(
                setOf(TextElementKind.TIME, TextElementKind.DATE, TextElementKind.INFO),
                notes.map { it.element }.toSet(),
            )
            notes.forEach {
                assertEquals(id, it.requestedFontId)
                assertEquals(FontCatalog.REASON_HOST_BUNDLED_UNSUPPORTED, it.reason)
            }
            assertEquals("resolution must not rewrite the requested font", before, design)
        }
    }

    @Test fun unknownHostIsTheSafeDefault() {
        val unknown = RenderEnvironment(34, false, "EEE")
        assertFalse(unknown.supportsBundledFonts)
        val spec = DesignResolver.resolve(designWith("clocky-poppins"), card, unknown)
        assertEquals(FontIds.SYSTEM_SANS, spec.time.face.fontId)
    }

    @Test fun systemFontsAreUnaffectedByTheHost() {
        for (id in FontCatalog.allIds - FontCatalog.bundledIds.toSet()) for (bundled in listOf(true, false)) {
            val spec = DesignResolver.resolve(designWith(id), card, env(bundled = bundled))
            assertTrue("$id/$bundled", spec.degradations.none {
                it is Degradation.FontFallback && it.reason.startsWith("bundled-font")
            })
            assertEquals(spec.time.face, DesignResolver.resolve(designWith(id), card, env(bundled = !bundled)).time.face)
        }
    }

    @Test fun apiBelow26KeepsItsOwnReasonRegardlessOfTheHost() {
        for (bundled in listOf(true, false)) for (sdk in listOf(23, 25)) {
            val reasons = DesignResolver.resolve(designWith("clocky-poppins"), card, env(sdk, bundled))
                .degradations.filterIsInstance<Degradation.FontFallback>().map { it.reason }.toSet()
            assertEquals(setOf(FontCatalog.REASON_NEEDS_API_26), reasons)
        }
    }

    @Test fun hostFallbackUsesTheSystemSansWeightLikeTheOtherFallbacks() {
        val spec = DesignResolver.resolve(designWith("clocky-poppins", 600), card, env(34, bundled = false))
        assertEquals(ResolvedFace(FontIds.SYSTEM_SANS, 600), spec.time.face)
        val direct = DesignResolver.resolve(designWith(FontIds.SYSTEM_SANS, 600), card, env(34, bundled = true))
        assertEquals(direct.time.face, spec.time.face)
    }
}
