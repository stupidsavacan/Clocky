package com.stupidsavacan.clocky.design.resolve

import com.stupidsavacan.clocky.design.model.BackgroundType
import kotlin.math.pow

/** WCAG 2.x relative luminance and contrast ratio on sRGB colors. Alpha is ignored unless stated. */
object Contrast {
    /** Large text (the time) needs 3:1, small text (date, info) 4.5:1 (End-State 5.5). */
    const val LARGE_TEXT_RATIO = 3.0
    const val SMALL_TEXT_RATIO = 4.5

    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** [fgArgb] drawn over the opaque [bgRgb], flattened to opaque RGB. */
    fun flatten(fgArgb: Int, bgRgb: Int): Int {
        val a = ((fgArgb ushr 24) and 0xFF) / 255.0
        fun mix(shift: Int): Int {
            val f = (fgArgb shr shift) and 0xFF
            val b = (bgRgb shr shift) and 0xFF
            return (f * a + b * (1 - a)).toInt().coerceIn(0, 255)
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    const val BLACK = 0x000000
    const val WHITE = 0xFFFFFF
}

/**
 * How sure the editor is about a contrast result. The widget sits on a wallpaper Clocky cannot
 * read, so most results are estimates and are labelled as such instead of pretending otherwise.
 */
enum class ContrastLevel {
    OK,

    /** Measured against a fully opaque background the design draws itself. */
    KNOWN_POOR,

    /** Measured against a translucent card or a wallpaper color hint: likely, not certain. */
    LIKELY_RISK,

    /** No background of the design's own and no wallpaper hint: nothing to measure against. */
    UNKNOWN,
}

data class ContrastFinding(
    val element: TextElementKind,
    val level: ContrastLevel,
    /** Measured ratio; null for [ContrastLevel.UNKNOWN]. */
    val ratio: Double?,
    val required: Double,
    /** Black or white, whichever reads better on the measured background; null when nothing was measured. */
    val suggestedRgb: Int?,
)

/** Optional dominant wallpaper color (API 27+ `WallpaperManager.getWallpaperColors`), opaque RGB. */
data class WallpaperHint(val rgb: Int)

object ContrastChecker {
    /** Findings for every visible text element of [spec]; OK ones are included so callers can show "fine". */
    fun check(spec: ResolvedDigitalSpec, wallpaper: WallpaperHint? = null): List<ContrastFinding> {
        val out = mutableListOf<ContrastFinding>()
        out += measure(TextElementKind.TIME, spec.time, Contrast.LARGE_TEXT_RATIO, spec, wallpaper)
        if (spec.dateVisible) out += measure(TextElementKind.DATE, spec.date, Contrast.SMALL_TEXT_RATIO, spec, wallpaper)
        spec.info?.let { out += measure(TextElementKind.INFO, it.text, Contrast.SMALL_TEXT_RATIO, spec, wallpaper) }
        return out
    }

    /** Only the findings the user should see (everything that is not OK). */
    fun warnings(spec: ResolvedDigitalSpec, wallpaper: WallpaperHint? = null): List<ContrastFinding> =
        check(spec, wallpaper).filter { it.level != ContrastLevel.OK }

    private fun measure(
        kind: TextElementKind,
        text: ResolvedText,
        required: Double,
        spec: ResolvedDigitalSpec,
        wallpaper: WallpaperHint?,
    ): ContrastFinding {
        val bg = spec.background
        val own = bg.visible && bg.type != BackgroundType.OUTLINE
        val backdrops: List<Int>
        val certain: Boolean
        when {
            own -> {
                backdrops = if (bg.type == BackgroundType.GRADIENT) listOf(bg.rgb, bg.endRgb) else listOf(bg.rgb)
                // A translucent card lets the unknown wallpaper through, so the result is an estimate.
                certain = bg.alpha >= OPAQUE
            }
            wallpaper != null -> {
                backdrops = listOf(wallpaper.rgb)
                certain = false
            }
            else -> return ContrastFinding(kind, ContrastLevel.UNKNOWN, null, required, null)
        }
        // Text opacity matters: a 50% white over a light card is much weaker than the stored RGB suggests.
        val ratios = backdrops.map { Contrast.ratio(Contrast.flatten(text.argb, it), it) }
        val worst = ratios.min()
        val suggestion = backdrops.let { b ->
            val whiteWorst = b.minOf { Contrast.ratio(Contrast.WHITE, it) }
            val blackWorst = b.minOf { Contrast.ratio(Contrast.BLACK, it) }
            if (whiteWorst >= blackWorst) Contrast.WHITE else Contrast.BLACK
        }
        val level = when {
            worst >= required -> ContrastLevel.OK
            certain -> ContrastLevel.KNOWN_POOR
            else -> ContrastLevel.LIKELY_RISK
        }
        return ContrastFinding(kind, level, worst, required, suggestion.takeIf { level != ContrastLevel.OK })
    }

    private const val OPAQUE = 250
}
