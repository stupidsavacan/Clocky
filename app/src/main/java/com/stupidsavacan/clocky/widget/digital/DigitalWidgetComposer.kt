package com.stupidsavacan.clocky.widget.digital

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.Alignment
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.ResolvedFace
import com.stupidsavacan.clocky.design.resolve.ResolvedRadius
import com.stupidsavacan.clocky.design.resolve.ResolvedText
import kotlin.math.roundToInt

/** Final text sizes in px after fitting the host bounds. */
data class FitSizes(val timePx: Float, val datePx: Float)

/**
 * Builds the one RemoteViews that both the launcher and the editor's PreviewHost apply
 * (End-State principle 7). Composition: template + one font fragment per visible element.
 */
object DigitalWidgetComposer {
    fun compose(
        context: Context,
        spec: ResolvedDigitalSpec,
        sizes: FitSizes,
        onClick: PendingIntent? = null,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val rv = RemoteViews(context.packageName, R.layout.clocky_digital_widget)

        applyBackground(rv, spec, sdkInt)
        val padPx = (spec.paddingDp * density).roundToInt()
        rv.setViewPadding(R.id.clocky_widget_content, padPx, padPx, padPx, padPx)

        // Hosts reapply() updates whose root layout matches the views on screen, which replays the
        // addView actions on the live tree; clearing first keeps exactly one fragment per slot.
        rv.removeAllViews(R.id.clocky_time_slot)
        rv.addView(
            R.id.clocky_time_slot,
            fragment(context, spec.time, caps = false, sizes.timePx, spec.timeFormats.format12Hour,
                spec.timeFormats.format24Hour, density, sdkInt),
        )
        rv.setInt(R.id.clocky_time_slot, "setGravity", gravityOf(spec.time.alignment))

        rv.removeAllViews(R.id.clocky_date_slot)
        if (spec.dateVisible) {
            rv.addView(
                R.id.clocky_date_slot,
                fragment(context, spec.date, caps = true, sizes.datePx, spec.datePattern,
                    spec.datePattern, density, sdkInt),
            )
            rv.setInt(R.id.clocky_date_slot, "setGravity", gravityOf(spec.date.alignment))
            rv.setViewVisibility(R.id.clocky_date_slot, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.clocky_date_slot, View.GONE)
        }

        onClick?.let { rv.setOnClickPendingIntent(R.id.clocky_widget_root, it) }
        return rv
    }

    fun gravityOf(alignment: Alignment): Int = when (alignment) {
        Alignment.START -> Gravity.START
        Alignment.CENTER -> Gravity.CENTER_HORIZONTAL
        Alignment.END -> Gravity.END
    } or Gravity.CENTER_VERTICAL

    private fun fragment(
        context: Context,
        text: ResolvedText,
        caps: Boolean,
        sizePx: Float,
        format12: String,
        format24: String,
        density: Float,
        sdkInt: Int,
    ): RemoteViews {
        val child = RemoteViews(context.packageName, FontFragments.layoutFor(text.face.fontId, caps))
        // Actions on the child apply inside the child's own tree, so time and date can share
        // fragment layouts (and therefore view ids) without ambiguity.
        val id = FontFragments.faceViewId(text.face)
        child.setViewVisibility(id, View.VISIBLE)
        child.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sizePx)
        child.setTextColor(id, text.argb)
        child.setFloat(id, "setLetterSpacing", text.letterSpacingEm)
        child.setCharSequence(id, "setFormat12Hour", format12)
        child.setCharSequence(id, "setFormat24Hour", format24)
        if (sdkInt >= DesignResolver.MIN_TRANSLATION_SDK && (text.xDp != 0f || text.yDp != 0f)) {
            child.setFloat(id, "setTranslationX", text.xDp * density)
            child.setFloat(id, "setTranslationY", text.yDp * density)
        }
        return child
    }

    private fun applyBackground(rv: RemoteViews, spec: ResolvedDigitalSpec, sdkInt: Int) {
        val bg = spec.background
        if (!bg.visible) {
            rv.setViewVisibility(R.id.clocky_widget_background, View.GONE)
            return
        }
        rv.setViewVisibility(R.id.clocky_widget_background, View.VISIBLE)
        rv.setInt(R.id.clocky_widget_background, "setColorFilter", OPAQUE or bg.rgb)
        rv.setInt(R.id.clocky_widget_background, "setImageAlpha", bg.alpha)
        if (sdkInt >= DesignResolver.MIN_OUTLINE_RADIUS_SDK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (val r = bg.radius) {
                ResolvedRadius.System -> rv.setViewOutlinePreferredRadiusDimen(
                    R.id.clocky_widget_background,
                    android.R.dimen.system_app_widget_background_radius,
                )
                is ResolvedRadius.Dp -> rv.setViewOutlinePreferredRadius(
                    R.id.clocky_widget_background,
                    r.value,
                    TypedValue.COMPLEX_UNIT_DIP,
                )
            }
        } else {
            val dp = (bg.radius as? ResolvedRadius.Dp)?.value ?: DesignResolver.LEGACY_SYSTEM_RADIUS_DP
            rv.setImageViewResource(R.id.clocky_widget_background, FontFragments.backgroundFor(dp))
        }
    }

    private const val OPAQUE = 0xFF000000.toInt()
}

/** Maps resolved faces to fragment layouts and view ids (Phase 1A font set). */
object FontFragments {
    @LayoutRes
    fun layoutFor(fontId: String, caps: Boolean): Int = when (fontId) {
        "sans-serif-light" -> if (caps) R.layout.clocky_face_sans_serif_light_caps else R.layout.clocky_face_sans_serif_light
        "sans-serif-rounded" -> if (caps) R.layout.clocky_face_sans_serif_rounded_caps else R.layout.clocky_face_sans_serif_rounded
        "serif" -> if (caps) R.layout.clocky_face_serif_caps else R.layout.clocky_face_serif
        "sans-serif-condensed" -> if (caps) R.layout.clocky_face_sans_serif_condensed_caps else R.layout.clocky_face_sans_serif_condensed
        "monospace" -> if (caps) R.layout.clocky_face_monospace_caps else R.layout.clocky_face_monospace
        else -> if (caps) R.layout.clocky_face_system_sans_caps else R.layout.clocky_face_system_sans
    }

    fun faceViewId(face: ResolvedFace): Int {
        if (face.fontId != FontIds.SYSTEM_SANS) return R.id.clocky_face_single
        return when (face.weight) {
            100 -> R.id.clocky_face_w100
            200 -> R.id.clocky_face_w200
            300 -> R.id.clocky_face_w300
            400 -> R.id.clocky_face_w400
            500 -> R.id.clocky_face_w500
            600 -> R.id.clocky_face_w600
            700 -> R.id.clocky_face_w700
            800 -> R.id.clocky_face_w800
            900 -> R.id.clocky_face_w900
            else -> error("Resolver produced a non-canonical weight: ${face.weight}")
        }
    }

    fun backgroundFor(radiusDp: Float): Int = when (DesignResolver.nearestLegacyVariant(radiusDp)) {
        0f -> R.drawable.clocky_widget_bg_r0
        8f -> R.drawable.clocky_widget_bg_r8
        16f -> R.drawable.clocky_widget_bg_r16
        24f -> R.drawable.clocky_widget_bg_r24
        32f -> R.drawable.clocky_widget_bg_r32
        else -> R.drawable.clocky_widget_bg_r48
    }
}
