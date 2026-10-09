package com.stupidsavacan.clocky.design.exchange

import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Random
import java.util.zip.Deflater

/** Phase 3C-2: the pure `.clocky` / `CLOCKY2:` core (org.json needs the Robolectric android.jar). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesignExchangeTest {
    private fun doc(design: DigitalDesign) = DigitalDesignCodec.encode(design)

    private fun success(outcome: ImportOutcome) = outcome as ImportOutcome.Success

    private fun failure(outcome: ImportOutcome): ImportError = (outcome as ImportOutcome.Failure).reason

    private fun code(name: String, design: JSONObject) = (DesignExchange.toTextCode(name, design) as TextCode.Ready).code

    /** A hand-built envelope, bypassing [DesignExchange.envelope] so tests can break each field. */
    private fun envelope(
        design: JSONObject? = doc(DigitalDesign()),
        format: Any? = DesignExchange.FORMAT,
        version: Any? = 1,
        name: Any? = "Test",
    ): JSONObject = JSONObject().apply {
        format?.let { put("format", it) }
        version?.let { put("formatVersion", it) }
        name?.let { put("name", it) }
        design?.let { put("design", it) }
    }

    private fun asCode(json: String, payload: ByteArray = DesignExchange.deflate(json.toByteArray())) =
        DesignExchange.CODE_PREFIX + DesignExchange.base64UrlEncode(payload)

    private fun file(json: JSONObject) = DesignExchange.parseFile(json.toString().toByteArray())

    /** A design exercising every section, all size classes, unknown keys at several depths and private keys. */
    private fun stressDocument(): JSONObject {
        val base = doc(BuiltinDesigns.byId("editorial-serif")!!.instantiate())
        base.put("futureTopLevel", JSONObject().put("list", JSONArray().put(1).put("two").put(JSONObject().put("deep", true))))
        base.getJSONObject("time").put("futureTimeKey", 3.5)
        base.getJSONObject("style").put("futureStyleKey", "x")
        val overrides = base.getJSONObject("layout").getJSONObject("overrides")
        listOf("strip", "card", "square", "large").forEach { overrides.put(it, JSONObject().put("time.sizeSp", 40).put("future.path", "kept")) }
        overrides.put("xlarge", JSONObject().put("time.sizeSp", 99))
        return base
    }

    // ---- round trips ----

    @Test
    fun everyBuiltinRoundTripsThroughFileAndTextCode() {
        BuiltinDesigns.all.forEach { builtin ->
            val design = builtin.instantiate()
            val source = doc(design)
            val viaFile = success(DesignExchange.parseFile(DesignExchange.toFileBytes(builtin.id, source)))
            val viaCode = success(DesignExchange.parseTextCode(code(builtin.id, source)))
            assertEquals(builtin.id, DigitalDesignCodec.decode(viaFile.design), design)
            assertEquals(builtin.id, DigitalDesignCodec.decode(viaCode.design), design)
            assertEquals(builtin.id, source.toString(), viaFile.design.toString())
            assertEquals(builtin.id, builtin.id, viaCode.name)
            assertTrue(viaFile.unknownFonts.isEmpty())
        }
    }

    @Test
    fun stressDesignRoundTripsEverythingIncludingUnknownKeys() {
        val source = stressDocument()
        val expected = JSONObject(source.toString())
        val viaFile = success(DesignExchange.parseFile(DesignExchange.toFileBytes("Stress", source)))
        val viaCode = success(DesignExchange.parseTextCode(code("Stress", source)))
        listOf(viaFile, viaCode).forEach { imported ->
            assertEquals(expected.toString(), imported.design.toString())
            assertEquals(3.5, imported.design.getJSONObject("time").getDouble("futureTimeKey"), 0.0)
            assertEquals("kept", imported.design.getJSONObject("layout").getJSONObject("overrides").getJSONObject("large").getString("future.path"))
            assertTrue(imported.design.getJSONObject("layout").getJSONObject("overrides").has("xlarge"))
        }
        // Export never mutates its input.
        assertEquals(expected.toString(), source.toString())
    }

    @Test
    fun exportedEnvelopeHasTheDocumentedShape() {
        val env = DesignExchange.envelope("  My   clock ", doc(DigitalDesign()))
        assertEquals(listOf("format", "formatVersion", "name", "design"), env.keys().asSequence().toList())
        assertEquals("clocky-design", env.getString("format"))
        assertEquals(1, env.getInt("formatVersion"))
        assertEquals("My clock", env.getString("name"))
        assertEquals(2, env.getJSONObject("design").getInt("schema"))
    }

    @Test
    fun textCodeIsPrefixedUrlSafeAndCarriesTheName() {
        val text = code("Évening ☕", doc(DigitalDesign()))
        assertTrue(text.startsWith("CLOCKY2:"))
        assertTrue(text.removePrefix("CLOCKY2:").all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals("Évening ☕", success(DesignExchange.parseTextCode(text)).name)
    }

    @Test
    fun textCodeIsExactlyDeflatedEnvelopeJson() {
        val source = doc(DigitalDesign())
        val text = code("N", source)
        val payload = text.removePrefix("CLOCKY2:")
        // Decode by hand with the platform decoder: the code is plain base64url(zlib(envelope)).
        val compressed = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE)
        val inflater = java.util.zip.Inflater()
        inflater.setInput(compressed)
        val buf = ByteArray(16 * 1024)
        val n = inflater.inflate(buf)
        assertTrue(inflater.finished())
        assertEquals(DesignExchange.toFileBytes("N", source).toList(), buf.copyOf(n).toList())
    }

    @Test
    fun whitespaceAndPaddingInAPastedCodeAreTolerated() {
        val text = code("Wrapped", doc(DigitalDesign()))
        val wrapped = "  \n" + text.chunked(40).joinToString("\r\n") + "\n "
        assertEquals("Wrapped", success(DesignExchange.parseTextCode(wrapped)).name)
        val body = text.removePrefix("CLOCKY2:")
        val padded = "CLOCKY2:" + body + "=".repeat((4 - body.length % 4) % 4)
        assertEquals("Wrapped", success(DesignExchange.parseTextCode(padded)).name)
    }

    @Test
    fun base64UrlRoundTripsEveryShortLength() {
        val random = Random(7)
        for (length in 0..40) {
            val bytes = ByteArray(length).also { random.nextBytes(it) }
            val encoded = DesignExchange.base64UrlEncode(bytes)
            assertEquals(android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP), encoded)
        }
    }

    // ---- size limits ----

    @Test
    fun oversizedEnvelopeIsNeverTruncatedAndUsesTheFileRoute() {
        val source = doc(DigitalDesign())
        val noise = ByteArray(20_000).also { Random(1).nextBytes(it) }
        source.put("blob", android.util.Base64.encodeToString(noise, android.util.Base64.NO_WRAP))
        val result = DesignExchange.toTextCode("Big", source)
        assertTrue(result is TextCode.TooLarge)
        assertTrue((result as TextCode.TooLarge).decodedBytes > DesignExchange.MAX_TEXT_CODE_DECODED_BYTES)
        val bytes = DesignExchange.toFileBytes("Big", source)
        assertEquals(source.getString("blob"), success(DesignExchange.parseFile(bytes)).design.getString("blob"))
    }

    @Test
    fun envelopeAtTheLimitIsAcceptedAndOneByteOverIsRefused() {
        val source = doc(DigitalDesign())
        source.put("pad", "")
        val baseSize = DesignExchange.toFileBytes("N", source).size
        // Incompressible padding so the size is the decoded size, not the compressed size.
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val random = Random(3)
        fun padded(extra: Int) = JSONObject(source.toString()).put("pad", String(CharArray(extra) { chars[random.nextInt(chars.length)] }))
        val fit = DesignExchange.MAX_TEXT_CODE_DECODED_BYTES - baseSize
        val atLimit = padded(fit)
        assertEquals(DesignExchange.MAX_TEXT_CODE_DECODED_BYTES, DesignExchange.toFileBytes("N", atLimit).size)
        val ready = DesignExchange.toTextCode("N", atLimit)
        assertTrue(ready is TextCode.Ready)
        assertEquals(atLimit.toString(), success(DesignExchange.parseTextCode((ready as TextCode.Ready).code)).design.toString())
        assertTrue(DesignExchange.toTextCode("N", padded(fit + 1)) is TextCode.TooLarge)
    }

    @Test
    fun decompressionBombIsRefusedWithoutInflatingIt() {
        // 40 MB of zeros deflates to ~40 KB: past the payload cap, so use a smaller bomb that fits the cap.
        val bomb = ByteArray(8 * 1024 * 1024)
        val compressed = DesignExchange.deflate(bomb)
        assertTrue("bomb must fit the code cap to reach the inflater", DesignExchange.base64UrlEncode(compressed).length <= DesignExchange.MAX_TEXT_CODE_PAYLOAD_CHARS)
        assertEquals(ImportError.TOO_LARGE, failure(DesignExchange.parseTextCode(DesignExchange.CODE_PREFIX + DesignExchange.base64UrlEncode(compressed))))
    }

    @Test
    fun overlongPayloadIsRefusedBeforeDecoding() {
        val text = DesignExchange.CODE_PREFIX + "A".repeat(DesignExchange.MAX_TEXT_CODE_PAYLOAD_CHARS + 1)
        assertEquals(ImportError.TOO_LARGE, failure(DesignExchange.parseTextCode(text)))
    }

    @Test
    fun fileOverTheReadCapIsRefused() {
        assertEquals(ImportError.TOO_LARGE, failure(DesignExchange.parseFile(ByteArray(DesignExchange.MAX_FILE_BYTES + 1) { ' '.code.toByte() })))
    }

    // ---- corrupt text codes ----

    @Test
    fun badTextCodesAreRefusedWithAReason() {
        val good = code("N", doc(DigitalDesign()))
        val body = good.removePrefix("CLOCKY2:")
        assertEquals(ImportError.EMPTY, failure(DesignExchange.parseTextCode("")))
        assertEquals(ImportError.EMPTY, failure(DesignExchange.parseTextCode("   \n")))
        assertEquals(ImportError.EMPTY, failure(DesignExchange.parseTextCode("CLOCKY2:")))
        assertEquals(ImportError.WRONG_PREFIX, failure(DesignExchange.parseTextCode(body)))
        assertEquals(ImportError.WRONG_PREFIX, failure(DesignExchange.parseTextCode("CLOCKY1:$body")))
        assertEquals(ImportError.WRONG_PREFIX, failure(DesignExchange.parseTextCode("clocky2:$body")))
        assertEquals(ImportError.BAD_BASE64, failure(DesignExchange.parseTextCode("CLOCKY2:" + body.take(20) + "+/" + body.drop(22))))
        assertEquals(ImportError.BAD_BASE64, failure(DesignExchange.parseTextCode("CLOCKY2:" + body.take(20) + "!" + body.drop(21))))
        assertEquals(ImportError.BAD_BASE64, failure(DesignExchange.parseTextCode("CLOCKY2:" + body.take(20) + "é" + body.drop(21))))
        assertEquals(ImportError.BAD_BASE64, failure(DesignExchange.parseTextCode("CLOCKY2:AB=C")))
        assertEquals(ImportError.BAD_BASE64, failure(DesignExchange.parseTextCode("CLOCKY2:AAA===")))
        // A single dangling base64 character can never be a complete byte.
        assertEquals(ImportError.TRUNCATED, failure(DesignExchange.parseTextCode("CLOCKY2:" + body.take(21))))
    }

    @Test
    fun nonCanonicalTrailingBitsAreRefused() {
        // "AB" decodes to one byte with leftover non-zero bits ('B' = 000001); the canonical form is "AA".
        assertEquals(ImportError.BAD_BASE64, failure(DesignExchange.parseTextCode("CLOCKY2:AB")))
    }

    @Test
    fun truncatedDeflateStreamIsReportedAsTruncated() {
        val compressed = DesignExchange.deflate(DesignExchange.toFileBytes("N", doc(DigitalDesign())))
        for (cut in listOf(compressed.size - 1, compressed.size / 2, 3, 1)) {
            val text = DesignExchange.CODE_PREFIX + DesignExchange.base64UrlEncode(compressed.copyOf(cut))
            assertEquals("cut=$cut", ImportError.TRUNCATED, failure(DesignExchange.parseTextCode(text)))
        }
    }

    @Test
    fun garbageAndTrailingBytesAreBadDeflate() {
        val compressed = DesignExchange.deflate(DesignExchange.toFileBytes("N", doc(DigitalDesign())))
        val garbage = DesignExchange.CODE_PREFIX + DesignExchange.base64UrlEncode(ByteArray(64) { (it * 37 + 11).toByte() })
        assertEquals(ImportError.BAD_DEFLATE, failure(DesignExchange.parseTextCode(garbage)))
        val trailing = DesignExchange.CODE_PREFIX + DesignExchange.base64UrlEncode(compressed + byteArrayOf(1, 2, 3))
        assertEquals(ImportError.BAD_DEFLATE, failure(DesignExchange.parseTextCode(trailing)))
        val flipped = compressed.copyOf().also { it[it.size - 2] = (it[it.size - 2].toInt() xor 0x55).toByte() }
        assertEquals(ImportError.BAD_DEFLATE, failure(DesignExchange.parseTextCode(DesignExchange.CODE_PREFIX + DesignExchange.base64UrlEncode(flipped))))
    }

    @Test
    fun validCompressionOfNonEnvelopeContentIsRefused() {
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseTextCode(asCode("not json at all"))))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseTextCode(asCode("[1,2,3]"))))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseTextCode(asCode("{\"a\":1} trailing"))))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseTextCode(asCode("", DesignExchange.deflate(byteArrayOf(0xC3.toByte(), 0x28))))))
        assertEquals(ImportError.WRONG_FORMAT, failure(DesignExchange.parseTextCode(asCode("{\"a\":1}"))))
    }

    // ---- envelope validation ----

    @Test
    fun wrongFormatIsRefused() {
        assertEquals(ImportError.WRONG_FORMAT, failure(file(envelope(format = "other"))))
        assertEquals(ImportError.WRONG_FORMAT, failure(file(envelope(format = null))))
        assertEquals(ImportError.WRONG_FORMAT, failure(file(envelope(format = 7))))
        assertEquals(ImportError.WRONG_FORMAT, failure(file(envelope(version = null))))
        assertEquals(ImportError.WRONG_FORMAT, failure(file(envelope(version = "1"))))
        assertEquals(ImportError.WRONG_FORMAT, failure(file(envelope(version = 0))))
        assertEquals(ImportError.WRONG_FORMAT, failure(file(envelope(version = 1.5))))
    }

    @Test
    fun futureFormatVersionAndSchemaAreRefused() {
        assertEquals(ImportError.FUTURE_FORMAT_VERSION, failure(file(envelope(version = 2))))
        assertEquals(ImportError.FUTURE_FORMAT_VERSION, failure(file(envelope(version = 99))))
        val future = doc(DigitalDesign()).put("schema", 3)
        assertEquals(ImportError.FUTURE_SCHEMA, failure(file(envelope(design = future))))
        assertEquals(ImportError.FUTURE_SCHEMA, failure(DesignExchange.parseTextCode(code("N", future))))
    }

    @Test
    fun missingOrMalformedDesignIsInvalid() {
        assertEquals(ImportError.INVALID_DESIGN, failure(file(envelope(design = null))))
        assertEquals(ImportError.INVALID_DESIGN, failure(file(envelope().put("design", "text"))))
        assertEquals(ImportError.INVALID_DESIGN, failure(file(envelope().put("design", JSONArray()))))
        assertEquals(ImportError.INVALID_DESIGN, failure(file(envelope(design = JSONObject()))))
        assertEquals(ImportError.INVALID_DESIGN, failure(file(envelope(design = doc(DigitalDesign()).put("schema", "2")))))
        assertEquals(ImportError.INVALID_DESIGN, failure(file(envelope(design = doc(DigitalDesign()).put("schema", 0)))))
    }

    @Test
    fun nonFiniteNumbersAreInvalid() {
        val design = "{\"schema\":2,\"time\":{\"sizeSp\":1e999}}"
        val raw = "{\"format\":\"clocky-design\",\"formatVersion\":1,\"name\":\"N\",\"design\":$design}"
        // org.json refuses to hold an infinite number, so the file is unreadable rather than a bad design.
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseFile(raw.toByteArray())))    }

    // ---- file parsing ----

    @Test
    fun badFilesAreRefused() {
        assertEquals(ImportError.EMPTY, failure(DesignExchange.parseFile(ByteArray(0))))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseFile("hello".toByteArray())))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseFile(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00))))
        val full = DesignExchange.toFileBytes("N", doc(DigitalDesign()))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseFile(full.copyOf(full.size / 2))))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseFile(full.copyOf(full.size - 1))))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseFile(full + "{}".toByteArray())))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseFile("[".repeat(10_000).toByteArray())))
        assertEquals(ImportError.NOT_JSON, failure(DesignExchange.parseFile((("{\"a\":".repeat(40)) + "1" + "}".repeat(40)).toByteArray())))
    }

    @Test
    fun byteOrderMarkAndSurroundingWhitespaceAreAccepted() {
        val full = DesignExchange.toFileBytes("Bom", doc(DigitalDesign()))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + " \n".toByteArray() + full + "\r\n".toByteArray()
        assertEquals("Bom", success(DesignExchange.parseFile(bom)).name)
    }

    @Test
    fun deeplyNestedUnknownDataWithinTheLimitIsKept() {
        val nested = JSONObject()
        var cursor = nested
        repeat(10) { cursor = JSONObject().also { child -> cursor.put("n", child) } }
        val source = doc(DigitalDesign()).put("future", nested)
        assertEquals(source.toString(), success(DesignExchange.parseFile(DesignExchange.toFileBytes("N", source))).design.toString())
    }

    // ---- names ----

    @Test
    fun namesAreCleanedAndMissingNamesAreNull() {
        assertEquals("A B", success(file(envelope(name = "  A \n\t B  "))).name)
        assertEquals(40, success(file(envelope(name = "x".repeat(300)))).name!!.length)
        assertNull(success(file(envelope(name = "   "))).name)
        assertNull(success(file(envelope(name = null))).name)
        assertNull(success(file(envelope(name = 12))).name)
        assertEquals("ab", success(file(envelope(name = "a\u0000b\u0007"))).name?.replace(" ", ""))
        assertEquals("Clocky design", JSONObject(String(DesignExchange.toFileBytes("   ", doc(DigitalDesign())))).getString("name"))
    }

    @Test
    fun fileNamesAreSafeAndHaveTheExtension() {
        assertEquals("My clock.clocky", DesignExchange.fileName("My clock"))
        assertEquals("a_b_c.clocky", DesignExchange.fileName("a/b\\c"))
        assertEquals("clocky-design.clocky", DesignExchange.fileName("///"))
        assertEquals("clocky-design.clocky", DesignExchange.fileName(".."))
        assertEquals("clocky-design.clocky", DesignExchange.fileName(""))
        assertFalse(DesignExchange.fileName("a:b*c?\"<>|").any { it in ":*?\"<>|" })
        assertTrue(DesignExchange.fileName("x".repeat(500)).length <= 60 + ".clocky".length)
        assertEquals("夜の時計.clocky", DesignExchange.fileName("夜の時計"))
    }

    // ---- privacy ----

    @Test
    fun widgetAndDeviceDetailsAreNeverExported() {
        val source = doc(DigitalDesign())
            .put("appWidgetId", 23)
            .put("calibration", JSONObject().put("dx", 3))
        source.getJSONObject("time").put("widgetId", 5).put("deviceModel", "moto g13")
        source.getJSONObject("layout").getJSONObject("overrides").put("strip", JSONObject().put("calibration", 1))
        source.put("list", JSONArray().put(JSONObject().put("serial", "ABC123").put("keep", 1)))
        val text = String(DesignExchange.toFileBytes("N", source))
        listOf("appWidgetId", "calibration", "widgetId", "deviceModel", "serial", "ABC123", "moto").forEach { assertFalse(it, text.contains(it)) }
        assertTrue(text.contains("\"keep\":1"))
        val code = (DesignExchange.toTextCode("N", source) as TextCode.Ready).code
        val roundTripped = success(DesignExchange.parseTextCode(code)).design.toString()
        listOf("appWidgetId", "calibration", "widgetId", "deviceModel", "ABC123").forEach { assertFalse(it, roundTripped.contains(it)) }
    }

    @Test
    fun importDropsPrivateKeysSmuggledIntoAFile() {
        val smuggled = doc(DigitalDesign()).put("appWidgetId", 23).put("calibration", JSONObject().put("x", 1))
        val imported = success(file(envelope(design = smuggled)))
        assertFalse(imported.design.has("appWidgetId"))
        assertFalse(imported.design.has("calibration"))
    }

    // ---- fonts ----

    @Test
    fun unknownFontsFallBackToTheDefaultAndAreDisclosed() {
        val source = doc(BuiltinDesigns.byId("editorial-serif")!!.instantiate())
        source.getJSONObject("time").put("font", "future-font-x")
        source.getJSONObject("date").put("font", "future-font-x\u0007")
        source.getJSONObject("style").getJSONObject("fontSecondary").put("font", "y".repeat(100))
        val imported = success(file(envelope(design = source)))
        assertEquals(listOf("future-font-x", "future-font-x", "y".repeat(40)).distinct(), imported.unknownFonts)
        assertEquals("system-sans",imported.design.getJSONObject("time").getString("font"))
        assertEquals("system-sans",imported.design.getJSONObject("date").getString("font"))
        assertEquals("system-sans",imported.design.getJSONObject("style").getJSONObject("fontSecondary").getString("font"))
        // The known primary font is untouched.
        assertEquals(source.getJSONObject("style").getJSONObject("fontPrimary").getString("font"), imported.design.getJSONObject("style").getJSONObject("fontPrimary").getString("font"))
        // Still a valid design.
        DigitalDesignCodec.decode(imported.design)
    }

    @Test
    fun nonStringFontIdIsReplacedToo() {
        val source = doc(DigitalDesign())
        source.getJSONObject("info").put("font", 12)
        val imported = success(file(envelope(design = source)))
        assertEquals(listOf("12"), imported.unknownFonts)
        assertEquals("system-sans",imported.design.getJSONObject("info").getString("font"))
    }

    @Test
    fun knownFontsProduceNoDisclosure() {
        com.stupidsavacan.clocky.design.resolve.FontCatalog.allIds.forEach { id ->
            val source = doc(DigitalDesign())
            source.getJSONObject("time").put("font", id)
            val imported = success(file(envelope(design = source)))
            assertTrue(id, imported.unknownFonts.isEmpty())
            assertEquals(id, imported.design.getJSONObject("time").getString("font"))
        }
    }

    // ---- schema 1 ----

    @Test
    fun schemaOneDocumentIsMigratedToSchemaTwo() {
        val v1 = JSONObject().put("schema", 1).put("time", JSONObject().put("sizeSp", 70).put("fontFamily", "serif"))
        val imported = success(file(envelope(design = v1)))
        assertEquals(2, imported.design.getInt("schema"))
        assertEquals("serif", imported.design.getJSONObject("time").getString("font"))
        assertEquals(70.0f, DigitalDesignCodec.decode(imported.design).time.style.sizeSp, 0f)
    }

    @Test
    fun importResultIsIndependentOfTheParsedInput() {
        val bytes = DesignExchange.toFileBytes("N", doc(DigitalDesign()))
        val first = success(DesignExchange.parseFile(bytes))
        first.design.put("mutated", true)
        assertFalse(success(DesignExchange.parseFile(bytes)).design.has("mutated"))
        assertArrayEquals(bytes, DesignExchange.toFileBytes("N", doc(DigitalDesign())))
    }
}
