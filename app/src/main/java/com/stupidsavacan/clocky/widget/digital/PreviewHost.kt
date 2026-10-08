package com.stupidsavacan.clocky.widget.digital

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.SizeClassRule
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.SizeContext
import kotlin.math.min

/** The one way any screen shows a design: resolve, fit, compose, then apply the RemoteViews locally. */
object DesignPreview {
    fun render(frame: FrameLayout, design: DigitalDesign, size: SizeContext,
        sizeClass: SizeClass = SizeClassRule.resolve(size.maxWidthDp.toFloat(), size.minHeightDp.toFloat()),
        entrySize: SizeF = SizeF(size.minWidthDp.toFloat(), size.maxHeightDp.toFloat()),
    ): ResolvedDigitalSpec {
        val context = frame.context
        val density = context.resources.displayMetrics.density
        val widthPx = (entrySize.width * density).toInt()
        val heightPx = (entrySize.height * density).toInt()
        val entry = DigitalWidgetUpdater.renderEntry(context.applicationContext, design, entrySize, sizeClass,
            DigitalWidgetUpdater.environment(context.applicationContext, forEditor = true))
        val spec = entry.spec
        val remoteViews = entry.views
        frame.removeAllViews()
        // Inflate like a launcher would: an Activity context brings AppCompat's view inflater, whose
        // AppCompatImageView/TextView overrides are not RemoteViews-callable.
        frame.addView(remoteViews.apply(context.applicationContext, frame), FrameLayout.LayoutParams(widthPx, heightPx))
        return spec
    }

    /**
     * A stand-in wallpaper that keeps the design legible: dark behind light text, light behind dark
     * text, and a neutral mid tone when the design draws its own background card.
     */
    fun backdropColor(spec: ResolvedDigitalSpec): Int {
        if (spec.background.visible) return BACKDROP_NEUTRAL
        val rgb = spec.time.argb
        val luma = (0.299 * ((rgb shr 16) and 0xFF) + 0.587 * ((rgb shr 8) and 0xFF) + 0.114 * (rgb and 0xFF)) / 255.0
        return if (luma > 0.5) BACKDROP_DARK else BACKDROP_LIGHT
    }

    private const val BACKDROP_DARK = 0xFF2A2A31.toInt()
    private const val BACKDROP_LIGHT = 0xFFD9D5CC.toInt()
    private const val BACKDROP_NEUTRAL = 0xFF55555E.toInt()
}

/**
 * Editor preview that applies the exact RemoteViews the widget receives (End-State principle 7).
 * It runs the same resolve → fit → compose path as [DigitalWidgetUpdater] and differs only in its
 * input size, so preview and widget cannot drift structurally.
 *
 * Matching host classes use the entry nearest the editor's current orientation; other classes
 * use measured portrait representative entries.
 */
class PreviewHost(
    private val frame: FrameLayout,
    private val appWidgetId: Int,
    /** Upper bound for the displayed height; the render is scaled down uniformly to honor it. */
    private val maxDisplayHeightDp: Int = 100_000,
    private val onRendered: (ResolvedDigitalSpec) -> Unit = {},
) {
    private val context: Context = frame.context
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Pair<DigitalDesign, SizeClass>? = null

    /** The entry size (dp) of the latest render: what the resolver clamps offsets against. */
    var lastEntrySize: SizeF? = null
        private set
    private val renderPending = Runnable { pending?.let { (d, c) -> render(d, c) } }

    /** Size class the placed widget currently has, or Card when the host has not reported one. */
    fun hostSizeClass(): SizeClass {
        val size = hostSize() ?: return SizeClass.CARD
        return SizeClassRule.resolve(size.maxWidthDp.toFloat(), size.minHeightDp.toFloat())
    }

    /** Coalesces rapid edits (e.g. slider drags) into one render per frame. */
    fun schedule(design: DigitalDesign, sizeClass: SizeClass) {
        pending = design to sizeClass
        handler.removeCallbacks(renderPending)
        handler.post(renderPending)
    }

    fun render(design: DigitalDesign, sizeClass: SizeClass): ResolvedDigitalSpec {
        val size = previewSize(sizeClass)
        val entry = previewEntry(sizeClass, size)
        lastEntrySize = entry
        val density = context.resources.displayMetrics.density
        val widthPx = (entry.width * density).toInt()
        val heightPx = (entry.height * density).toInt()
        val spec = DesignPreview.render(frame, design, size, sizeClass, entry)
        fitIntoParent(widthPx, heightPx)
        onRendered(spec)
        return spec
    }

    /**
     * The host's real size when it matches [sizeClass]; otherwise a representative size measured on
     * Phase 3A-0 hosts (moto g13: 4×1 Strip, 4×2 Card, 2×2 Square, 4×3 Large).
     */
    fun previewSize(sizeClass: SizeClass): SizeContext {
        val host = hostSize()
        if (host != null && SizeClassRule.resolve(host.maxWidthDp.toFloat(), host.minHeightDp.toFloat()) == sizeClass) return host
        // Complete measured pairs, so a host's wide Card bounds cannot silently turn a Square sample into Large.
        return when (sizeClass) {
            SizeClass.STRIP -> SizeContext(363, 58, 667, 122)
            SizeClass.CARD -> SizeContext(363, 132, 667, 260)
            SizeClass.SQUARE -> SizeContext(173, 132, 325, 260)
            SizeClass.LARGE -> SizeContext(363, 206, 667, 398)
        }
    }

    fun previewEntry(sizeClass: SizeClass, size: SizeContext = previewSize(sizeClass)): SizeF {
        if (hostSizeClass() == sizeClass) {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            val plan = ResponsiveRenderPlan.from(options, Build.VERSION.SDK_INT)
            val landscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val width = if (landscape) size.maxWidthDp else size.minWidthDp
            val height = if (landscape) size.minHeightDp else size.maxHeightDp
            if (plan.useMap) return plan.entries.minBy {
                kotlin.math.abs(it.width - width) + kotlin.math.abs(it.height - height)
            }
            return SizeF(width.toFloat(), height.toFloat())
        }
        return SizeF(size.minWidthDp.toFloat(), size.maxHeightDp.toFloat())
    }

    private fun hostSize(): SizeContext? {
        val wm = AppWidgetManager.getInstance(context) ?: return null
        val size = DigitalWidgetUpdater.sizeContextOf(wm.getAppWidgetOptions(appWidgetId))
        return size.takeIf { it.minHeightDp > 0 && it.minWidthDp > 0 && it.maxHeightDp > 0 }
    }

    /**
     * Shows the render at its real size, scaled down (never up) to the editor column and to
     * [maxDisplayHeightDp]. The rendered child keeps its real pixel size and is scaled about its
     * top-left corner; the frame takes the scaled size so the surrounding layout wraps it exactly.
     */
    private fun fitIntoParent(widthPx: Int, heightPx: Int) {
        frame.clipChildren = false
        (frame.parent as? android.view.ViewGroup)?.clipChildren = false
        val child = frame.getChildAt(0)
        frame.post {
            val parent = frame.parent as? View
            val available = parent?.let { it.width - it.paddingLeft - it.paddingRight } ?: 0
            val maxHeightPx = maxDisplayHeightDp * context.resources.displayMetrics.density
            var scale = 1f
            if (available > 0) scale = min(scale, available.toFloat() / widthPx)
            scale = min(scale, maxHeightPx / heightPx)
            child.pivotX = 0f
            child.pivotY = 0f
            child.scaleX = scale
            child.scaleY = scale
            frame.layoutParams = frame.layoutParams.apply {
                width = (widthPx * scale).toInt()
                height = (heightPx * scale).toInt()
            }
        }
    }

    companion object {
        const val DEFAULT_MIN_WIDTH_DP = 363
        const val DEFAULT_MAX_WIDTH_DP = 667
        const val STRIP_MIN_HEIGHT_DP = 58
        const val STRIP_MAX_HEIGHT_DP = 122
        const val CARD_MIN_HEIGHT_DP = 132
        const val CARD_MAX_HEIGHT_DP = 260
    }
}
