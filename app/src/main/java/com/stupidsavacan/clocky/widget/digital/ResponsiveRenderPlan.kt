package com.stupidsavacan.clocky.widget.digital

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.SizeClassRule
import com.stupidsavacan.clocky.design.resolve.SizeContext

/** Classification and geometry are separate: one rule call, host keys only, no class anchors. */
data class ResponsiveRenderPlan(val sizeClass: SizeClass, val entries: List<SizeF>, val useMap: Boolean) {
    companion object {
        fun from(options: Bundle, sdkInt: Int): ResponsiveRenderPlan {
            val size = DigitalWidgetUpdater.sizeContextOf(options)
            val cls = SizeClassRule.resolve(size.maxWidthDp.toFloat(), size.minHeightDp.toFloat())
            @Suppress("DEPRECATION")
            val reported = if (sdkInt >= 31) runCatching {
                options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    .orEmpty().filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                    .distinct().take(16)
            }.getOrDefault(emptyList()) else emptyList()
            if (reported.isNotEmpty()) return ResponsiveRenderPlan(cls, reported, true)
            // Missing/malformed host list: the historical options pair, never synthetic map keys.
            val portrait = SizeF(size.minWidthDp.toFloat(), size.maxHeightDp.toFloat())
            val landscape = SizeF(size.maxWidthDp.toFloat(), size.minHeightDp.toFloat())
            return ResponsiveRenderPlan(cls, listOf(portrait, landscape), false)
        }

        fun geometry(entry: SizeF) = SizeContext(
            entry.width.toInt(), entry.height.toInt(), entry.width.toInt(), entry.height.toInt(),
        )
    }
}

data class RenderEntry(
    val size: SizeF,
    val spec: com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec,
    val fit: FitSizes,
    val views: android.widget.RemoteViews,
    val resolveNanos: Long,
    val fitNanos: Long,
    val composeNanos: Long,
)

data class RenderBatch(
    val views: android.widget.RemoteViews,
    val plan: ResponsiveRenderPlan,
    val entries: List<RenderEntry>,
    val generationNanos: Long,
    val remoteViewsNanos: Long,
)
