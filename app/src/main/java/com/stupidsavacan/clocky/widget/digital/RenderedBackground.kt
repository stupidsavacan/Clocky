package com.stupidsavacan.clocky.widget.digital

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.ResolvedBackground
import com.stupidsavacan.clocky.design.resolve.ResolvedRadius
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the "rendered" background kinds (End-State 5.7): linear gradient and outline-only. The
 * bitmap is opaque-colored; the element opacity rides on `setImageAlpha`, like the native solid
 * path. It is built at the widget's real size, so it exists only when the provider or the editor
 * builds RemoteViews (size, theme or settings changed), never per tick.
 */
object RenderedBackground {
    /** RemoteViews bitmaps count against the binder transaction; larger widgets are drawn smaller and stretched. */
    private const val MAX_PIXELS = 1_200_000

    fun create(context: Context, bg: ResolvedBackground, widthPx: Int, heightPx: Int): Bitmap? {
        if (!bg.isRendered || widthPx <= 0 || heightPx <= 0) return null
        val density = context.resources.displayMetrics.density
        val shrink = sqrt(MAX_PIXELS.toDouble() / (widthPx.toDouble() * heightPx)).coerceAtMost(1.0).toFloat()
        val w = (widthPx * shrink).toInt().coerceAtLeast(1)
        val h = (heightPx * shrink).toInt().coerceAtLeast(1)
        val scale = density * shrink
        val radius = radiusPx(context, bg.radius) * shrink

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (bg.type) {
            BackgroundType.GRADIENT -> {
                paint.shader = gradient(bg, w, h)
                canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
            }
            BackgroundType.OUTLINE -> {
                val stroke = (bg.borderWidthDp * scale).coerceAtLeast(1f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                paint.color = OPAQUE or bg.rgb
                val inset = stroke / 2f
                val inner = (radius - inset).coerceAtLeast(0f)
                canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), inner, inner, paint)
            }
            else -> Unit
        }
        return bitmap
    }

    /** Angle 0 runs the start color at the top to the end color at the bottom, 90 left to right. */
    internal fun gradient(bg: ResolvedBackground, w: Int, h: Int): LinearGradient {
        val rad = Math.toRadians(bg.gradientAngleDeg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = cos(rad).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val cx = w / 2f
        val cy = h / 2f
        return LinearGradient(
            cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
            OPAQUE or bg.rgb, OPAQUE or bg.endRgb, Shader.TileMode.CLAMP,
        )
    }

    private fun radiusPx(context: Context, radius: ResolvedRadius): Float {
        val density = context.resources.displayMetrics.density
        return when (radius) {
            is ResolvedRadius.Dp -> radius.value * density
            ResolvedRadius.System ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
                } else {
                    DesignResolver.LEGACY_SYSTEM_RADIUS_DP * density
                }
        }
    }

    private const val OPAQUE = 0xFF000000.toInt()
}
