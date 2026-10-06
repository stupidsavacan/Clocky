package com.stupidsavacan.clocky.widget.digital

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.SizeClassResolver
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.SizeContext
import kotlin.math.min

/**
 * Editor preview that applies the exact RemoteViews the widget receives (End-State principle 7).
 * It runs the same resolve → fit → compose path as [DigitalWidgetUpdater] and differs only in its
 * input size, so preview and widget cannot drift structurally.
 *
 * The preview shows the portrait variant (min width × max height), which is what a phone in
 * portrait displays.
 */
class PreviewHost(
    private val frame: FrameLayout,
    private val appWidgetId: Int,
    private val onRendered: (ResolvedDigitalSpec) -> Unit = {},
) {
    private val context: Context = frame.context
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Pair<DigitalDesign, SizeClass>? = null
    private val renderPending = Runnable { pending?.let { (d, c) -> render(d, c) } }

    /** Size class the placed widget currently has, or Card when the host has not reported one. */
    fun hostSizeClass(): SizeClass {
        val size = hostSize() ?: return SizeClass.CARD
        return SizeClassResolver.resolve(size.minHeightDp)
    }

    /** Coalesces rapid edits (e.g. slider drags) into one render per frame. */
    fun schedule(design: DigitalDesign, sizeClass: SizeClass) {
        pending = design to sizeClass
        handler.removeCallbacks(renderPending)
        handler.post(renderPending)
    }

    fun render(design: DigitalDesign, sizeClass: SizeClass): ResolvedDigitalSpec {
        val size = previewSize(sizeClass)
        val density = context.resources.displayMetrics.density
        val widthPx = (size.minWidthDp * density).toInt()
        val heightPx = (size.maxHeightDp * density).toInt()
        val (spec, remoteViews) = DigitalWidgetUpdater.buildPortrait(context, design, size)

        frame.removeAllViews()
        // Inflate like a launcher would: an Activity context brings AppCompat's view inflater, whose
        // AppCompatImageView/TextView overrides are not RemoteViews-callable.
        frame.addView(remoteViews.apply(context.applicationContext, frame), FrameLayout.LayoutParams(widthPx, heightPx))
        frame.layoutParams = frame.layoutParams.apply {
            width = widthPx
            height = heightPx
        }
        fitIntoParent(widthPx)
        onRendered(spec)
        return spec
    }

    /**
     * The host's real size when it matches [sizeClass]; otherwise a representative size measured on
     * Phase 0 hosts (moto g13: 4×1 ≈ 363×58..122dp, 4×2 ≈ 363×132..260dp) using the host's widths
     * when known.
     */
    fun previewSize(sizeClass: SizeClass): SizeContext {
        val host = hostSize()
        if (host != null && SizeClassResolver.resolve(host.minHeightDp) == sizeClass) return host
        val minWidth = host?.minWidthDp?.takeIf { it > 0 } ?: DEFAULT_MIN_WIDTH_DP
        val maxWidth = host?.maxWidthDp?.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH_DP
        return when (sizeClass) {
            SizeClass.STRIP -> SizeContext(minWidth, STRIP_MIN_HEIGHT_DP, maxWidth, STRIP_MAX_HEIGHT_DP)
            SizeClass.CARD -> SizeContext(minWidth, CARD_MIN_HEIGHT_DP, maxWidth, CARD_MAX_HEIGHT_DP)
        }
    }

    private fun hostSize(): SizeContext? {
        val wm = AppWidgetManager.getInstance(context) ?: return null
        val size = DigitalWidgetUpdater.sizeContextOf(wm.getAppWidgetOptions(appWidgetId))
        return size.takeIf { it.minHeightDp > 0 && it.minWidthDp > 0 && it.maxHeightDp > 0 }
    }

    /** Scales the frame down (never up) when the widget is wider than the editor column. */
    private fun fitIntoParent(widthPx: Int) {
        frame.post {
            val available = (frame.parent as? android.view.View)?.let {
                it.width - it.paddingLeft - it.paddingRight
            } ?: return@post
            if (available <= 0) return@post
            val scale = min(1f, available.toFloat() / widthPx)
            frame.pivotX = widthPx / 2f
            frame.pivotY = 0f
            frame.scaleX = scale
            frame.scaleY = scale
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
