package com.stupidsavacan.clocky.design.exchange

import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.resolve.FontCatalog
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import com.stupidsavacan.clocky.design.storage.FileDesignRepository
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Why an import was refused. Each value maps to one user-facing message; nothing is written for any of them. */
enum class ImportError {
    EMPTY,
    WRONG_PREFIX,
    BAD_BASE64,
    TRUNCATED,
    BAD_DEFLATE,
    TOO_LARGE,
    NOT_JSON,
    WRONG_FORMAT,
    FUTURE_FORMAT_VERSION,
    FUTURE_SCHEMA,
    INVALID_DESIGN,
}

sealed interface ImportOutcome {
    /**
     * A validated design ready for [com.stupidsavacan.clocky.design.storage.DesignRepository.createFromDocument].
     * [name] is null when the file carried no usable name. [unknownFonts] lists the font ids that were replaced
     * with the default family, for disclosure.
     */
    class Success(val name: String?, val design: JSONObject, val unknownFonts: List<String>) : ImportOutcome
    data class Failure(val reason: ImportError) : ImportOutcome
}

sealed interface TextCode {
    data class Ready(val code: String) : TextCode

    /** The envelope exceeds the decoded limit; share the `.clocky` file instead. Never truncated. */
    data class TooLarge(val decodedBytes: Int) : TextCode
}

/**
 * The `.clocky` file and `CLOCKY2:` text code formats (End-State §8, Phase 3 spec §4.2, Phase 3C-2).
 *
 * Both carry the same UTF-8 JSON envelope `{"format":"clocky-design","formatVersion":1,"name":…,"design":{schema 2}}`.
 * The text code is `CLOCKY2:` + base64url(zlib-deflate(envelope)) with the *decoded* envelope capped at
 * [MAX_TEXT_CODE_DECODED_BYTES]; inflation is streamed against that cap so a decompression bomb is refused
 * after a few KB of output. Everything here is pure (no Android context) and total: malformed input yields an
 * [ImportOutcome.Failure], never an exception, and import never produces anything but a document for a *new* design.
 *
 * Privacy: the design document is the only payload. Widget ids, calibration and device details are removed on
 * export and refused on import ([PRIVATE_KEYS]); all other unknown fields round-trip untouched.
 */
object DesignExchange {
    const val FORMAT = "clocky-design"
    const val FORMAT_VERSION = 1
    const val CODE_PREFIX = "CLOCKY2:"
    const val FILE_EXTENSION = "clocky"

    const val MAX_TEXT_CODE_DECODED_BYTES = 8 * 1024

    /** Longest `CLOCKY2:` payload accepted before any decoding: an incompressible 8 KB envelope is ~11 KB of base64. */
    const val MAX_TEXT_CODE_PAYLOAD_CHARS = 12 * 1024

    /** A `.clocky` file has no 8 KB cap, but import never reads more than this. */
    const val MAX_FILE_BYTES = 1 shl 20

    const val MAX_JSON_DEPTH = 32

    /** Keys that identify a widget or a device; removed at every depth on export and on import. */
    val PRIVATE_KEYS: Set<String> = setOf(
        "appWidgetId", "appWidgetIds", "widgetId", "widgetIds", "calibration", "calibrations",
        "device", "deviceId", "deviceInfo", "deviceModel", "serial", "serialNumber",
    )

    private val BYTE_ORDER_MARK = 0xFEFF.toChar().toString()
    private const val DEFAULT_EXPORT_NAME = "Clocky design"
    private const val MAX_DISCLOSED_FONT_LENGTH = 40
    private val BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val BASE64URL_INDEX = IntArray(128) { -1 }.also { table -> BASE64URL.forEachIndexed { i, c -> table[c.code] = i } }

    // ---- export ----

    /** The shared envelope. [design] is deep-copied and sanitized; the argument is never modified. */
    fun envelope(name: String, design: JSONObject): JSONObject {
        val copy = JSONObject(design.toString())
        stripPrivate(copy)
        return JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("name", exportName(name))
            .put("design", copy)
    }

    fun toFileBytes(name: String, design: JSONObject): ByteArray =
        envelope(name, design).toString().toByteArray(Charsets.UTF_8)

    fun toTextCode(name: String, design: JSONObject): TextCode {
        val json = toFileBytes(name, design)
        if (json.size > MAX_TEXT_CODE_DECODED_BYTES) return TextCode.TooLarge(json.size)
        return TextCode.Ready(CODE_PREFIX + base64UrlEncode(deflate(json)))
    }

    /** A file name for [name]: no path or reserved characters, bounded, with the `.clocky` extension. */
    fun fileName(name: String): String {
        val base = name.map { if (it.isISOControl() || it in "\\/:*?\"<>|") '_' else it }.joinToString("")
            .replace(Regex("\\s+"), " ").trim().trim('.', '_', ' ').take(60).trim().trimEnd('.')
        return (base.ifEmpty { "clocky-design" }) + "." + FILE_EXTENSION
    }

    private fun exportName(name: String): String =
        runCatching { FileDesignRepository.cleanName(name) }.getOrDefault(DEFAULT_EXPORT_NAME)

    // ---- import ----

    fun parseFile(bytes: ByteArray): ImportOutcome {
        if (bytes.isEmpty()) return fail(ImportError.EMPTY)
        if (bytes.size > MAX_FILE_BYTES) return fail(ImportError.TOO_LARGE)
        val text = runCatching { decodeUtf8(bytes) }.getOrNull() ?: return fail(ImportError.NOT_JSON)
        return parseEnvelope(text.removePrefix(BYTE_ORDER_MARK))
    }

    fun parseTextCode(raw: String): ImportOutcome {
        val text = raw.trim()
        if (text.isEmpty()) return fail(ImportError.EMPTY)
        if (!text.startsWith(CODE_PREFIX)) return fail(ImportError.WRONG_PREFIX)
        // Messengers and clipboards wrap long lines: whitespace inside the payload is not significant.
        val payload = text.substring(CODE_PREFIX.length).filterNot { it.isWhitespace() }
        if (payload.isEmpty()) return fail(ImportError.EMPTY)
        if (payload.length > MAX_TEXT_CODE_PAYLOAD_CHARS) return fail(ImportError.TOO_LARGE)
        val compressed = when (val decoded = base64UrlDecode(payload)) {
            is B64.Ok -> decoded.bytes
            B64.Truncated -> return fail(ImportError.TRUNCATED)
            B64.Invalid -> return fail(ImportError.BAD_BASE64)
        }
        val json = when (val inflated = inflateBounded(compressed, MAX_TEXT_CODE_DECODED_BYTES)) {
            is Inflated.Ok -> inflated.bytes
            Inflated.TooLarge -> return fail(ImportError.TOO_LARGE)
            Inflated.Truncated -> return fail(ImportError.TRUNCATED)
            Inflated.Corrupt -> return fail(ImportError.BAD_DEFLATE)
        }
        val body = runCatching { decodeUtf8(json) }.getOrNull() ?: return fail(ImportError.NOT_JSON)
        return parseEnvelope(body)
    }

    private fun parseEnvelope(text: String): ImportOutcome {
        val root = parseStrictObject(text) ?: return fail(ImportError.NOT_JSON)
        if (root.opt("format") != FORMAT) return fail(ImportError.WRONG_FORMAT)
        val version = integral(root.opt("formatVersion")) ?: return fail(ImportError.WRONG_FORMAT)
        if (version > FORMAT_VERSION) return fail(ImportError.FUTURE_FORMAT_VERSION)
        if (version < 1) return fail(ImportError.WRONG_FORMAT)
        val design = root.opt("design") as? JSONObject ?: return fail(ImportError.INVALID_DESIGN)
        val schema = integral(design.opt("schema")) ?: return fail(ImportError.INVALID_DESIGN)
        if (schema > DigitalDesignCodec.SCHEMA_V2) return fail(ImportError.FUTURE_SCHEMA)
        if (schema < DigitalDesignCodec.SCHEMA_V1) return fail(ImportError.INVALID_DESIGN)

        val document = runCatching {
            // Re-serialization is the finiteness check: org.json refuses to write NaN/Infinity.
            var doc = JSONObject(design.toString())
            if (schema == DigitalDesignCodec.SCHEMA_V1) doc = DigitalDesignCodec.encode(DigitalDesignCodec.decode(doc))
            stripPrivate(doc)
            val unknown = replaceUnknownFonts(doc)
            DigitalDesignCodec.decode(doc)
            JSONObject(doc.toString()) to unknown
        }.getOrNull() ?: return fail(ImportError.INVALID_DESIGN)
        val name = (root.opt("name") as? String)?.let { runCatching { FileDesignRepository.cleanName(stripControl(it)) }.getOrNull() }
        return ImportOutcome.Success(name, document.first, document.second)
    }

    private fun fail(reason: ImportError) = ImportOutcome.Failure(reason)

    private fun integral(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Double -> value.takeIf { it.isFinite() && it == Math.rint(it) && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
        else -> null
    }

    /** Whole-document JSON object parse: bounded nesting, no trailing content. Null when malformed. */
    private fun parseStrictObject(text: String): JSONObject? {
        if (!withinDepth(text)) return null
        return runCatching {
            val tokener = JSONTokener(text)
            val value = tokener.nextValue() as? JSONObject
            if (value == null || tokener.nextClean() != '\u0000') null else value
        }.getOrNull()
    }

    /** True when `{`/`[` nesting never exceeds [MAX_JSON_DEPTH]; guards the recursive parser and walkers. */
    private fun withinDepth(text: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else when (c) {
                '"' -> inString = true
                '{', '[' -> if (++depth > MAX_JSON_DEPTH) return false
                '}', ']' -> depth--
            }
        }
        return true
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }

    // ---- sanitizing ----

    internal fun stripPrivate(value: Any?) {
        when (value) {
            is JSONObject -> {
                PRIVATE_KEYS.forEach { value.remove(it) }
                val keys = value.keys().asSequence().toList()
                keys.forEach { stripPrivate(value.opt(it)) }
            }
            is JSONArray -> for (i in 0 until value.length()) stripPrivate(value.opt(i))
        }
    }

    /**
     * Font ids are the only external reference in a design. An id this build does not ship is replaced by the
     * default family (the codec default, i.e. the MODERN category default; the id carries no category of its
     * own). Returns the replaced ids, sanitized for display.
     */
    private fun replaceUnknownFonts(doc: JSONObject): List<String> {
        val replaced = LinkedHashSet<String>()
        fun fix(holder: JSONObject?) {
            if (holder == null || !holder.has("font")) return
            val id = holder.opt("font")
            // Style-token references ("token:fontPrimary" / "token:fontSecondary") resolve through the design's own style block.
            if (id is String && (id in FontCatalog.allIds || FontIds.isToken(id))) return
            replaced += displayId(id)
            holder.put("font", FontIds.SYSTEM_SANS)
        }
        fix(doc.optJSONObject("time"))
        fix(doc.optJSONObject("date"))
        fix(doc.optJSONObject("info"))
        val style = doc.optJSONObject("style")
        fix(style?.optJSONObject("fontPrimary"))
        fix(style?.optJSONObject("fontSecondary"))
        return replaced.toList()
    }

    private fun displayId(id: Any?): String {
        val text = stripControl(if (id == null || id == JSONObject.NULL) "?" else id.toString())
        return text.take(MAX_DISCLOSED_FONT_LENGTH).ifEmpty { "?" }
    }

    private fun stripControl(text: String) = text.filterNot { it.isISOControl() }

    // ---- base64url (RFC 4648 §5), strict ----

    private sealed interface B64 {
        class Ok(val bytes: ByteArray) : B64
        data object Truncated : B64
        data object Invalid : B64
    }

    internal fun base64UrlEncode(data: ByteArray): String {
        val out = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8) or (data[i + 2].toInt() and 0xFF)
            out.append(BASE64URL[n shr 18 and 63]).append(BASE64URL[n shr 12 and 63])
                .append(BASE64URL[n shr 6 and 63]).append(BASE64URL[n and 63])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val n = data[i].toInt() and 0xFF shl 16
                out.append(BASE64URL[n shr 18 and 63]).append(BASE64URL[n shr 12 and 63])
            }
            2 -> {
                val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8)
                out.append(BASE64URL[n shr 18 and 63]).append(BASE64URL[n shr 12 and 63]).append(BASE64URL[n shr 6 and 63])
            }
        }
        return out.toString() // unpadded
    }

    /** Alphabet `A-Za-z0-9-_` only; `=` padding is optional but, when present, must complete the quantum; no stray bits. */
    private fun base64UrlDecode(text: String): B64 {
        val body = text.trimEnd('=')
        val padding = text.length - body.length
        if (padding > 2 || (padding > 0 && text.length % 4 != 0)) return B64.Invalid
        if (body.any { it.code >= 128 || BASE64URL_INDEX[it.code] < 0 }) return B64.Invalid
        if (body.length % 4 == 1) return B64.Truncated
        val out = ByteArrayOutputStream(body.length * 3 / 4)
        var acc = 0
        var bits = 0
        for (c in body) {
            acc = (acc shl 6) or BASE64URL_INDEX[c.code]
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write(acc shr bits and 0xFF)
            }
        }
        if (acc and ((1 shl bits) - 1) != 0) return B64.Invalid // non-canonical trailing bits
        return B64.Ok(out.toByteArray())
    }

    // ---- deflate ----

    internal fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private sealed interface Inflated {
        class Ok(val bytes: ByteArray) : Inflated
        data object TooLarge : Inflated
        data object Truncated : Inflated
        data object Corrupt : Inflated
    }

    /** Inflates in 1 KB steps and stops as soon as the output would pass [limit], so a bomb costs a few KB. */
    private fun inflateBounded(data: ByteArray, limit: Int): Inflated {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val n = try {
                    inflater.inflate(buffer)
                } catch (_: DataFormatException) {
                    return Inflated.Corrupt
                }
                if (n > 0) {
                    if (out.size() + n > limit) return Inflated.TooLarge
                    out.write(buffer, 0, n)
                }
                when {
                    inflater.finished() -> break
                    inflater.needsDictionary() -> return Inflated.Corrupt
                    n == 0 -> return if (inflater.needsInput()) Inflated.Truncated else Inflated.Corrupt
                }
            }
            return if (inflater.remaining > 0) Inflated.Corrupt else Inflated.Ok(out.toByteArray())
        } finally {
            inflater.end()
        }
    }
}
