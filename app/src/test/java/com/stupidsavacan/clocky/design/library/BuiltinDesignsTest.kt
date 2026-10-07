package com.stupidsavacan.clocky.design.library

import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.ColorRole
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.RenderEnvironment
import com.stupidsavacan.clocky.design.resolve.SizeContext
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Codec round trips need org.json, which is a stub on the plain JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuiltinDesignsTest {
    private val all = BuiltinDesigns.all

    @Test
    fun libraryHasEightUniqueDesignsTwoPerKit() {
        assertEquals(8, all.size)
        assertEquals(8, all.map { it.id }.toSet().size)
        listOf(Kits.DEFAULT, Kits.MINIMAL, Kits.BOLD, Kits.EDITORIAL).forEach { kit ->
            assertEquals("kit $kit", 2, all.count { it.kitId == kit })
        }
        assertEquals(BuiltinDesigns.CLOCKY_DEFAULT_ID, all.first().id)
        assertEquals("clocky-default", BuiltinDesigns.default.id)
    }

    @Test
    fun everyDesignCarriesProvenanceAndTokens() {
        all.forEach { b ->
            val d = b.design
            val src = d.source
            assertNotNull(b.id, src)
            assertEquals(b.id, src!!.builtinId)
            assertEquals(BuiltinDesigns.LIBRARY_VERSION, src.version)
            assertEquals(BuiltinDesigns.LIBRARY_VERSION, b.version)
            assertEquals(b.kitId, src.kitId)
            assertNotNull("${b.id} kit exists", Kits.byId(b.kitId))
            assertNotNull("${b.id} style", d.style)
            assertTrue("${b.id} time font token", FontIds.isToken(d.time.style.fontId))
            assertTrue("${b.id} date font token", FontIds.isToken(d.date.style.fontId))
            assertTrue("${b.id} tunable", QuickTune.isTunable(d))
            assertEquals(d, b.instantiate())
        }
    }

    @Test
    fun everyColorIsATokenAndResolvesToThePaletteRole() {
        val size = SizeContext(363, 132, 363, 260)
        val env = RenderEnvironment(34, isRtl = false, localeAutoDatePattern = "EEE, MMM d", isNight = false)
        all.forEach { b ->
            val d = b.design
            val tokens = d.style!!
            val colors = tokens.palette.variant(tokens.palette.fixedVariant)
            val refs = listOf(d.time.style.color, d.date.style.color) +
                if (d.background.type == BackgroundType.SOLID) listOf(d.background.color) else emptyList()
            refs.forEach { ref ->
                assertTrue("${b.id} color is Token", ref is ColorRef.Token)
            }
            val spec = DesignResolver.resolve(d, size, env)
            val timeRole = (d.time.style.color as ColorRef.Token).role
            val dateRole = (d.date.style.color as ColorRef.Token).role
            assertEquals(b.id, colors.of(timeRole), spec.time.argb and 0xFFFFFF)
            assertEquals(b.id, colors.of(dateRole), spec.date.argb and 0xFFFFFF)
            if (d.background.type == BackgroundType.SOLID) {
                assertEquals(ColorRole.SURFACE, (d.background.color as ColorRef.Token).role)
                assertEquals(b.id, colors.surface, spec.background.rgb)
            }
        }
    }

    @Test
    fun everyDesignPaletteIsOfferedByItsKitAndSurfaceRulesHold() {
        all.forEach { b ->
            val kit = Kits.byId(b.kitId)!!
            val palette = b.design.style!!.palette
            val option = kit.palettes.firstOrNull { it.palette.id == palette.id }
            assertNotNull("${b.id} palette ${palette.id} in kit ${kit.id}", option)
            assertEquals("${b.id} stores a full snapshot", option!!.palette, palette)
            if (option.needsSurface) {
                assertEquals("${b.id} needs surface", BackgroundType.SOLID, b.design.background.type)
            }
            assertTrue("${b.id} own palette selectable", QuickTune.paletteOptions(b.design).any { it.id == palette.id })
        }
    }

    @Test
    fun clockyDefaultIsCenterStackOnCardInlineOnStripAndBare() {
        val d = BuiltinDesigns.clockyDefault.design
        assertEquals(Template.CENTER_STACK, d.layout.template)
        assertEquals(Template.INLINE, d.layout.patchFor(SizeClass.STRIP).template)
        assertNull(d.layout.patchFor(SizeClass.CARD).template)
        assertEquals(BackgroundType.NONE, d.background.type)
        assertEquals(Kits.DEFAULT, BuiltinDesigns.clockyDefault.kitId)
    }

    @Test
    fun everyDesignSurvivesCodecRoundTripUnchanged() {
        all.forEach { b ->
            val json = DigitalDesignCodec.encode(b.design)
            val back = DigitalDesignCodec.decode(JSONObject(json.toString()))
            assertEquals(b.id, b.design, back)
        }
    }
}
