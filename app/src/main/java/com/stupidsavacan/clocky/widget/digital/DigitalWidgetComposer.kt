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
import com.stupidsavacan.clocky.design.model.ColorRole
import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.resolve.ColorBinding
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.ResolvedFace
import com.stupidsavacan.clocky.design.resolve.ResolvedRadius
import com.stupidsavacan.clocky.design.resolve.ResolvedText
import com.stupidsavacan.clocky.design.resolve.FontCatalog
import com.stupidsavacan.clocky.design.resolve.ShadowVariant
import kotlin.math.roundToInt

/** Final text sizes in px after fitting the host bounds. The AM/PM suffix is derived from [timePx]. */
data class FitSizes(val timePx: Float, val datePx: Float, val infoPx: Float = 0f)

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
        /** Extra per-zone click targets (Behavior > Tap); [onClick] stays the whole-widget target. */
        zones: ZoneClicks? = null,
        /** Real widget size in px; needed only to draw a rendered (gradient / outline) background. */
        boundsPx: Pair<Int, Int>? = null,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rv = RemoteViews(context.packageName, templateLayout(spec.template, spec.sizeClass))

        applyBackground(context, rv, spec, sdkInt, boundsPx)
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
        spec.amPm?.let { amPm ->
            rv.addView(
                R.id.clocky_time_slot,
                fragment(context, amPm.text, caps = false, sizes.timePx * amPm.scale, amPm.format12Hour,
                    amPm.format24Hour, density, sdkInt, startPaddingDp = AM_PM_GAP_DP,
                    markerFont = amPm.useLatinMarkerFont, decorative = true),
            )
        }
        rv.setInt(R.id.clocky_time_slot, "setGravity", gravityOf(spec.time.alignment))

        rv.removeAllViews(R.id.clocky_date_slot)
        if (spec.dateVisible) {
            rv.addView(
                R.id.clocky_date_slot,
                fragment(context, spec.date, caps = spec.dateUppercase, sizes.datePx, spec.datePattern,
                    spec.datePattern, density, sdkInt),
            )
            rv.setInt(R.id.clocky_date_slot, "setGravity", gravityOf(spec.date.alignment))
            rv.setViewVisibility(R.id.clocky_date_slot, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.clocky_date_slot, View.GONE)
        }
        applyInfo(context, rv, spec, sizes, density, sdkInt)
        arrangeTemplate(rv, spec, sizes, density, isRtl)

        onClick?.let { rv.setOnClickPendingIntent(R.id.clocky_widget_root, it) }
        zones?.let {
            it.time?.let { pi -> rv.setOnClickPendingIntent(R.id.clocky_time_slot, pi) }
            it.date?.let { pi -> rv.setOnClickPendingIntent(R.id.clocky_date_slot, pi) }
            if (spec.info != null) it.info?.let { pi -> rv.setOnClickPendingIntent(R.id.clocky_info_slot, pi) }
        }
        return rv
    }

    /** The Info line: one more TextClock fragment, whose pattern carries either a literal or a time zone. */
    private fun applyInfo(
        context: Context,
        rv: RemoteViews,
        spec: ResolvedDigitalSpec,
        sizes: FitSizes,
        density: Float,
        sdkInt: Int,
    ) {
        rv.removeAllViews(R.id.clocky_info_slot)
        val info = spec.info
        if (info == null) {
            rv.setViewVisibility(R.id.clocky_info_slot, View.GONE)
            return
        }
        rv.addView(
            R.id.clocky_info_slot,
            fragment(context, info.text, caps = false, sizes.infoPx, info.format12Hour, info.format24Hour,
                density, sdkInt, timeZoneId = info.timeZoneId),
        )
        rv.setInt(R.id.clocky_info_slot, "setGravity", gravityOf(info.text.alignment))
        rv.setViewVisibility(R.id.clocky_info_slot, View.VISIBLE)
    }

    /** One layout per composition; every layout carries the same ids (root, background, content, slots). */
    @LayoutRes
    fun templateLayout(template: Template, sizeClass: SizeClass): Int = when (template) {
        Template.TIME_FIRST, Template.MINIMAL -> R.layout.clocky_digital_widget
        Template.CENTER_STACK -> R.layout.clocky_digital_widget_date_first
        Template.INLINE -> R.layout.clocky_digital_widget_inline
        Template.SPLIT ->
            if (sizeClass == SizeClass.STRIP) R.layout.clocky_digital_widget_split_row else R.layout.clocky_digital_widget_date_first
    }

    /**
     * Spacing and alignment that belong to the composition rather than to either element: the
     * time/date gap, and for Inline the baseline lift that lets the smaller date sit on the time's
     * baseline (slots bottom-align, so the date is raised by the difference in font descent).
     */
    private fun arrangeTemplate(rv: RemoteViews, spec: ResolvedDigitalSpec, sizes: FitSizes, density: Float, isRtl: Boolean) {
        val gapPx = (spec.gapDp * density).roundToInt()
        when (spec.template) {
            Template.TIME_FIRST -> rv.setViewPadding(R.id.clocky_date_slot, 0, gapPx, 0, 0)
            Template.CENTER_STACK -> rv.setViewPadding(R.id.clocky_date_slot, 0, 0, 0, gapPx)
            Template.SPLIT -> if (spec.sizeClass == SizeClass.CARD) {
                rv.setViewPadding(R.id.clocky_date_slot, 0, 0, 0, gapPx)
            }
            Template.INLINE -> {
                rv.setInt(R.id.clocky_row, "setGravity", gravityOf(spec.time.alignment, vertical = false))
                val lift = ((sizes.timePx - sizes.datePx).coerceAtLeast(0f) * DESCENT_RATIO).roundToInt()
                val inlineGap = (INLINE_GAP_DP * density).roundToInt()
                if (isRtl) rv.setViewPadding(R.id.clocky_date_slot, 0, 0, inlineGap, lift)
                else rv.setViewPadding(R.id.clocky_date_slot, inlineGap, 0, 0, lift)
            }
            Template.MINIMAL -> Unit
        }
        if (spec.info != null) {
            rv.setViewPadding(R.id.clocky_info_slot, 0, gapPx.coerceAtLeast((INFO_GAP_DP * density).roundToInt()), 0, 0)
        }
    }

    fun gravityOf(alignment: Alignment, vertical: Boolean): Int = when (alignment) {
        Alignment.START -> Gravity.START
        Alignment.CENTER -> Gravity.CENTER_HORIZONTAL
        Alignment.END -> Gravity.END
    } or (if (vertical) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY)

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
        startPaddingDp: Float = 0f,
        timeZoneId: String? = null,
        markerFont: Boolean = false,
        decorative: Boolean = false,
    ): RemoteViews {
        val layout = when {
            markerFont -> markerLayout(text.shadow)
            decorative -> AmPmFallbackFragmentTable.layoutFor(text.face.fontId, text.shadow)
            else -> FontFragments.layoutFor(text.face.fontId, caps, text.shadow)
        }
        val child = RemoteViews(context.packageName, layout)
        // Actions on the child apply inside the child's own tree, so time and date can share
        // fragment layouts (and therefore view ids) without ambiguity.
        val id = if (markerFont) R.id.clocky_ampm_marker else FontFragments.faceViewId(text.face)
        child.setViewVisibility(id, View.VISIBLE)
        child.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, sizePx)
        applyTextColor(child, id, text, sdkInt)
        child.setFloat(id, "setLetterSpacing", text.letterSpacingEm)
        child.setCharSequence(id, "setFormat12Hour", format12)
        child.setCharSequence(id, "setFormat24Hour", format24)
        timeZoneId?.let { child.setString(id, "setTimeZone", it) }
        if (startPaddingDp > 0f) child.setViewPadding(id, (startPaddingDp * density).roundToInt(), 0, 0, 0)
        if (sdkInt >= DesignResolver.MIN_TRANSLATION_SDK && (text.xDp != 0f || text.yDp != 0f)) {
            child.setFloat(id, "setTranslationX", text.xDp * density)
            child.setFloat(id, "setTranslationY", text.yDp * density)
        }
        return child
    }

    @LayoutRes
    private fun markerLayout(shadow: ShadowVariant): Int = when (shadow) {
        ShadowVariant.CLASSIC -> R.layout.clocky_face_ampm_marker
        ShadowVariant.OFF -> R.layout.clocky_face_ampm_marker_off
        ShadowVariant.SOFT_DARK -> R.layout.clocky_face_ampm_marker_soft_dark
        ShadowVariant.SOFT_LIGHT -> R.layout.clocky_face_ampm_marker_soft_light
        ShadowVariant.STRONG_DARK -> R.layout.clocky_face_ampm_marker_strong_dark
        ShadowVariant.STRONG_LIGHT -> R.layout.clocky_face_ampm_marker_strong_light
    }

    /**
     * Static colors set the final ARGB. Theme-bound colors (API 31+) let the launcher choose by its
     * own night mode or system palette, so the widget follows theme and wallpaper changes with no
     * app update (End-State 5.5). [ResolvedText.argb] is the value for unsupported hosts.
     */
    private fun applyTextColor(child: RemoteViews, id: Int, text: ResolvedText, sdkInt: Int) {
        val binding = text.binding
        if (binding == null || sdkInt < DesignResolver.MIN_THEME_BINDING_SDK || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            child.setTextColor(id, text.argb)
            return
        }
        when (binding) {
            is ColorBinding.DayNight -> child.setColorInt(id, "setTextColor", binding.notNightArgb, binding.nightArgb)
            is ColorBinding.SystemRole -> {
                child.setColor(id, "setTextColor", colorResOf(binding.role))
                // A color resource cannot carry alpha; the element opacity rides on the view instead.
                if (text.opacity < 1f) child.setFloat(id, "setAlpha", text.opacity)
            }
        }
    }

    @androidx.annotation.ColorRes
    fun colorResOf(role: ColorRole): Int = when (role) {
        ColorRole.PRIMARY -> R.color.clocky_dyn_primary
        ColorRole.SECONDARY -> R.color.clocky_dyn_secondary
        ColorRole.ACCENT -> R.color.clocky_dyn_accent
        ColorRole.SURFACE -> R.color.clocky_dyn_surface
    }

    private fun applyBackground(
        context: Context,
        rv: RemoteViews,
        spec: ResolvedDigitalSpec,
        sdkInt: Int,
        boundsPx: Pair<Int, Int>?,
    ) {
        val bg = spec.background
        if (!bg.visible) {
            rv.setViewVisibility(R.id.clocky_widget_background, View.GONE)
            return
        }
        if (bg.isRendered) {
            // Gradient / outline are a bitmap drawn at the real size. Without a size (fit measurement)
            // there is nothing to draw and nothing to measure, so the view just stays hidden.
            val bitmap = boundsPx?.let { (w, h) -> RenderedBackground.create(context, bg, w, h) }
            if (bitmap == null) {
                rv.setViewVisibility(R.id.clocky_widget_background, View.GONE)
                return
            }
            rv.setViewVisibility(R.id.clocky_widget_background, View.VISIBLE)
            // A previous solid design may have left a tint on a reused view; a transparent filter clears it.
            rv.setInt(R.id.clocky_widget_background, "setColorFilter", 0)
            rv.setImageViewBitmap(R.id.clocky_widget_background, bitmap)
            rv.setInt(R.id.clocky_widget_background, "setImageAlpha", bg.alpha)
            return
        }
        rv.setViewVisibility(R.id.clocky_widget_background, View.VISIBLE)
        val binding = bg.binding
        if (binding == null || sdkInt < DesignResolver.MIN_THEME_BINDING_SDK || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            rv.setInt(R.id.clocky_widget_background, "setColorFilter", OPAQUE or bg.rgb)
        } else when (binding) {
            is ColorBinding.DayNight ->
                rv.setColorInt(R.id.clocky_widget_background, "setColorFilter", binding.notNightArgb or OPAQUE, binding.nightArgb or OPAQUE)
            is ColorBinding.SystemRole ->
                rv.setColor(R.id.clocky_widget_background, "setColorFilter", colorResOf(binding.role))
        }
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
    private const val INLINE_GAP_DP = 10f
    private const val AM_PM_GAP_DP = 2f
    private const val INFO_GAP_DP = 4f

    /** Typical descent as a fraction of font size; lifts the inline date onto the time's baseline. */
    private const val DESCENT_RATIO = 0.21f
}

/** Maps resolved faces to fragment layouts and view ids (FontFragmentTable is generated; see tools/fonts). */
object FontFragments {
    @LayoutRes
    fun layoutFor(fontId: String, caps: Boolean, shadow: ShadowVariant = ShadowVariant.CLASSIC): Int =
        FontFragmentTable.layoutFor(fontId, caps, shadow)
            ?: FontFragmentTable.layoutFor(FontIds.SYSTEM_SANS, caps, shadow)
            ?: error("No font fragment for $fontId / $shadow")

    /**
     * The view that shows [face] inside its fragment: platform alias families have one face; the
     * system sans and the bundled families have one view per weight, named by weight.
     */
    fun faceViewId(face: ResolvedFace): Int {
        val family = FontCatalog.family(face.fontId)
        if (family != null && family.source == FontCatalog.Source.SYSTEM && face.fontId != FontIds.SYSTEM_SANS) {
            return R.id.clocky_face_single
        }
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
