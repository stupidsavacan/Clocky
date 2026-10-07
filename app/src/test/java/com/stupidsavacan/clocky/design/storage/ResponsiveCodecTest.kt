package com.stupidsavacan.clocky.design.storage

import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.library.QuickTune
import com.stupidsavacan.clocky.design.model.*
import com.stupidsavacan.clocky.design.resolve.*
import com.stupidsavacan.clocky.studio.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResponsiveCodecTest {
    private fun roundTrip(d: DigitalDesign): DigitalDesign = DigitalDesignCodec.decode(JSONObject(DigitalDesignCodec.encode(d).toString()))
    private fun fixture(name: String) = JSONObject(javaClass.getResourceAsStream("/phase3a1/$name.json")!!.bufferedReader().use { it.readText() })

    @Test fun allBuiltinsAndPhase1a1b2FixturesRoundTripWithSchemaTwoAndEqualSpecs() {
        val designs = BuiltinDesigns.all.map { it.design } + listOf("phase1a", "phase1b", "phase2").map { DigitalDesignCodec.decode(fixture(it)) }
        designs.forEach { d ->
            val restored = roundTrip(d)
            assertEquals(d, restored)
            assertEquals(2, DigitalDesignCodec.encode(restored).getInt("schema"))
            SizeClass.entries.forEach { c ->
                val size = SizeContext(363, 132, 667, 260)
                val env = RenderEnvironment(34, false, "EEE")
                assertEquals(DesignResolver.resolve(d, size, env, c), DesignResolver.resolve(restored, size, env, c))
            }
        }
    }

    @Test fun squareLargeAllKnownPathsRoundTripAndPreserveExplicitFalseAndZero() {
        val patch = LayoutPatch(timeSizeSp = 80f, dateSizeSp = 20f, timeXDp = 0f, timeYDp = -2f,
            dateXDp = 3f, dateYDp = 4f, dateVisible = false, template = Template.INLINE,
            timeAlignment = Alignment.START, dateAlignment = Alignment.END,
            infoSizeSp = 18f, infoXDp = 5f, infoYDp = 6f, infoVisible = false, infoAlignment = Alignment.CENTER,
            paddingDp = 0f, dateGapDp = -4f)
        val d = DigitalDesign(date = DateElement(gapDp = 5f), info = InfoElement(visible = false),
            layout = DesignLayout(overrides = mapOf(SizeClass.SQUARE to patch, SizeClass.LARGE to patch)))
        assertEquals(d, roundTrip(d))
        val overrides = DigitalDesignCodec.encode(d).getJSONObject("layout").getJSONObject("overrides")
        listOf("square", "large").forEach { key ->
            val obj = overrides.getJSONObject(key)
            assertEquals(17, obj.length())
            assertFalse(obj.getBoolean("info.visible"))
            assertEquals(0.0, obj.getDouble("time.xDp"), 0.0)
            assertEquals(-4.0, obj.getDouble("date.gapDp"), 0.0)
        }
    }

    @Test fun unknownClassesPathsAndNestedJsonSurviveEditingKnownFields() {
        val root = JSONObject("""{"schema":2,"layout":{"overrides":{
            "poster":{"unknown":[true,null,{"label":"α"}]},
            "square":{"time.sizeSp":80,"future.path":{"array":[1,2.5,null]},"future.null":null},
            "large":{"future.scalar":"keep"}}}}""")
        val decoded = DigitalDesignCodec.decode(root)
        val changed = DesignEdits.setSize(decoded, TextTarget.TIME, 90f, EditScope(SizeClass.SQUARE))
        val json = DigitalDesignCodec.encode(changed).getJSONObject("layout").getJSONObject("overrides")
        assertEquals(root.getJSONObject("layout").getJSONObject("overrides").getJSONObject("poster").toString(), json.getJSONObject("poster").toString())
        val square = json.getJSONObject("square")
        assertTrue(square.has("future.path"))
        assertTrue(square.has("future.null"))
        assertTrue(square.isNull("future.null"))
        assertEquals("[1,2.5,null]", square.getJSONObject("future.path").getJSONArray("array").toString())
        assertEquals(90.0, square.getDouble("time.sizeSp"), 0.0)
        assertEquals("keep", json.getJSONObject("large").getString("future.scalar"))
        assertEquals(changed, roundTrip(changed))
    }

    @Test fun futureValuesOfKnownPathsAndNonObjectUnknownClassesSurviveUntilExplicitlyEdited() {
        val d = DigitalDesignCodec.decode(JSONObject("""{"schema":2,"layout":{"overrides":{
            "poster":[true,null,"future"],"square":{"info.alignment":"JUSTIFY","future.flag":true}}}}"""))
        assertEquals(d, roundTrip(d))
        val edited = DesignEdits.setAlignment(d,TextTarget.INFO,Alignment.END,EditScope(SizeClass.SQUARE))
        val json = DigitalDesignCodec.encode(edited).getJSONObject("layout").getJSONObject("overrides")
        assertEquals("[true,null,\"future\"]",json.getJSONArray("poster").toString())
        assertEquals("END",json.getJSONObject("square").getString("info.alignment"))
        assertTrue(json.getJSONObject("square").getBoolean("future.flag"))
        assertEquals(edited,roundTrip(edited))
        val malformed = DigitalDesignCodec.decode(JSONObject("""{"schema":2,"layout":{"overrides":{"square":[1],"poster":[2]}}}"""))
        val fixed = DesignEdits.setSize(malformed,TextTarget.TIME,80f,EditScope(SizeClass.SQUARE))
        assertEquals(fixed,roundTrip(fixed))
        assertEquals("[2]",DigitalDesignCodec.encode(fixed).getJSONObject("layout").getJSONObject("overrides").getJSONArray("poster").toString())
    }

    @Test fun unknownBaseAndPatchTemplatesAreRequestedValuesWithSafeDisclosedFallback() {
        val root = JSONObject("""{"schema":2,"layout":{"template":"FUTURE_BASE","overrides":{"card":{"layout.template":"FUTURE_CARD"},"square":{"date.visible":false}}}}""")
        val d = DigitalDesignCodec.decode(root)
        assertEquals(d, roundTrip(d))
        val json = DigitalDesignCodec.encode(d).getJSONObject("layout")
        assertEquals("FUTURE_BASE", json.getString("template"))
        assertEquals("FUTURE_CARD", json.getJSONObject("overrides").getJSONObject("card").getString("layout.template"))
        val spec = DesignResolver.resolve(d, SizeContext(363, 281, 667, 537), RenderEnvironment(34, false, "EEE"))
        assertEquals(Template.TIME_FIRST, spec.template)
        assertTrue(Degradation.TemplateFallback("FUTURE_CARD") in spec.degradations)
        val known = DesignEdits.setTemplate(d, Template.MINIMAL, EditScope(SizeClass.SQUARE))
        assertEquals(Template.MINIMAL, DesignEdits.templateOf(known, EditScope(SizeClass.SQUARE)))
        assertEquals("FUTURE_CARD", known.layout.patchFor(SizeClass.CARD).requestedTemplate)
        assertEquals(d, DesignEdits.revertOverride(known, SizeClass.SQUARE, DesignEdits.OverrideField.TEMPLATE))
        assertNull(DesignEdits.setTemplate(d, Template.INLINE, EditScope.ALL).layout.requestedTemplate)
    }

    @Test fun forbiddenIdentityOverridesAndSquareLargeWeightsAreInertButNotLost() {
        val d = DigitalDesignCodec.decode(JSONObject("""{"schema":2,"layout":{"overrides":{"square":{
            "time.weight":900,"date.weight":100,"time.fontId":"serif","time.color":"#FF0000",
            "time.opacity":0.1,"time.letterSpacingEm":0.4,"background.type":"GRADIENT",
            "background.radius":40,"effects.shadow":"OFF","behavior.showSeconds":true},
            "card":{"time.weight":700}}}}"""))
        val spec = DesignResolver.resolve(d, SizeContext(363,281,667,537), RenderEnvironment(34,false,"EEE"))
        assertEquals(700, spec.time.face.weight)
        assertEquals(400, spec.date.face.weight)
        assertEquals(FontIds.SYSTEM_SANS, spec.time.face.fontId)
        assertEquals(-1, spec.time.argb)
        assertEquals(0f, spec.time.letterSpacingEm)
        assertFalse(spec.background.visible)
        assertEquals(ShadowVariant.CLASSIC, spec.time.shadow)
        assertFalse(spec.timeFormats.format12Hour.contains("ss"))
        val o = DigitalDesignCodec.encode(d).getJSONObject("layout").getJSONObject("overrides").getJSONObject("square")
        assertEquals(900, o.getInt("time.weight"))
        assertTrue(o.has("behavior.showSeconds"))
        assertTrue(o.has("time.color"))
        assertEquals(d, roundTrip(d))
    }

    @Test fun quickTuneAndPreparationKeepFutureDataAndAllClassSizes() {
        val builtin = BuiltinDesigns.all.first().design
        val layout = builtin.layout.copy(preservedOverrides = mapOf("poster" to "{\"size\":999}"))
            .withPatch(SizeClass.SQUARE, LayoutPatch(timeSizeSp = 50f, infoSizeSp = 10f, dateVisible = true,
                preserved = mapOf("future.path" to "[1,true]")))
            .withPatch(SizeClass.LARGE, LayoutPatch(timeSizeSp = 60f, requestedTemplate = "FUTURE_LARGE"))
        val d = builtin.copy(layout = layout, style = builtin.style!!.copy(textSize = TextSizeStep.LARGE))
        val operations = listOf(QuickTune.detach(d), DesignEdits.prepare(d), QuickTune.setDateVisible(d,false),
            QuickTune.selectTemplate(d,SizeClass.CARD,Template.MINIMAL), QuickTune.surprise(d,kotlin.random.Random(7)))
        operations.forEach { edited ->
            assertEquals(layout.preservedOverrides, edited.layout.preservedOverrides)
            assertEquals(layout.patchFor(SizeClass.SQUARE).preserved, edited.layout.patchFor(SizeClass.SQUARE).preserved)
            assertEquals("FUTURE_LARGE", edited.layout.patchFor(SizeClass.LARGE).requestedTemplate)
            assertEquals(edited, roundTrip(edited))
        }
        assertEquals(10f * d.style!!.textSize.scale, DesignEdits.prepare(d).layout.patchFor(SizeClass.SQUARE).infoSizeSp)
        assertEquals(Template.MINIMAL, QuickTune.templateOf(QuickTune.selectTemplate(builtin,SizeClass.CARD,Template.MINIMAL),SizeClass.SQUARE))
        val reset = DesignEdits.resetSlot(d,Slot.LAYOUT,builtin)
        assertEquals(layout.preservedOverrides,reset.layout.preservedOverrides)
        assertEquals(layout.patchFor(SizeClass.SQUARE).preserved,reset.layout.patchFor(SizeClass.SQUARE).preserved)
        assertEquals(reset,roundTrip(reset))
    }
}
