package com.stupidsavacan.clocky.customization.model

/** Two Clocky customization profiles for the resizable digital widget. */
enum class DigitalWidgetProfile {
    FOUR_BY_ONE,
    FOUR_BY_TWO,
}

data class ResolvedWidgetWeights(
    val profile: DigitalWidgetProfile,
    val timeWeight: Int,
    val dateWeight: Int,
    val dateEnabled: Boolean,
)

data class ResolvedWidgetSizes(
    val profile: DigitalWidgetProfile,
    val timeSizeSp: Float,
    val dateSizeSp: Float,
)

data class ResolvedWidgetOffsets(
    val profile: DigitalWidgetProfile,
    val timeXDp: Float,
    val timeYDp: Float,
    val dateXDp: Float,
    val dateYDp: Float,
)

/**
 * Resolves AppWidget host dimensions to Clocky's compact (4x1) or regular (4x2) profile.
 *
 * Callers pass the host's OPTION_APPWIDGET_MIN_HEIGHT (the landscape height), so one widget keeps
 * one profile in both orientations. The 94dp boundary is the midpoint of the 59dp / 129dp heights
 * the original AOSP metadata used when the profiles were introduced; it is kept as a pinned
 * constant so existing per-profile overrides resolve unchanged, even though the provider metadata
 * now uses Clocky's own 4x1 / 4x2 geometry (WIDGET_CUSTOMIZATION_CONTRACT.md section 11).
 * Missing (0) or invalid heights and larger widgets deliberately use the 4x2 profile until the
 * End-State size classes replace this resolver.
 */
object DigitalWidgetProfileResolver {
    const val COMPACT_MIN_HEIGHT_DP = 59
    const val REGULAR_MIN_HEIGHT_DP = 129
    const val COMPACT_MAX_HEIGHT_DP =
        (COMPACT_MIN_HEIGHT_DP + REGULAR_MIN_HEIGHT_DP) / 2

    fun profileForHeightDp(targetHeightDp: Int): DigitalWidgetProfile = when {
        targetHeightDp <= 0 -> DigitalWidgetProfile.FOUR_BY_TWO
        targetHeightDp <= COMPACT_MAX_HEIGHT_DP -> DigitalWidgetProfile.FOUR_BY_ONE
        else -> DigitalWidgetProfile.FOUR_BY_TWO
    }

    fun resolveWeights(settings: WidgetSettings, targetHeightDp: Int): ResolvedWidgetWeights {
        val profile = profileForHeightDp(targetHeightDp)
        val override = overrideFor(settings, profile)
        return ResolvedWidgetWeights(
            profile = profile,
            timeWeight = override?.timeWeight ?: settings.time.requestedWeight,
            dateWeight = override?.dateWeight ?: settings.date.requestedWeight,
            dateEnabled = override?.dateEnabled ?: settings.date.enabled,
        )
    }

    fun resolveSizes(settings: WidgetSettings, targetHeightDp: Int): ResolvedWidgetSizes {
        val profile = profileForHeightDp(targetHeightDp)
        val override = overrideFor(settings, profile)
        return ResolvedWidgetSizes(
            profile = profile,
            timeSizeSp = (override?.timeSizeSp ?: settings.time.sizeSp).coerceAtLeast(1f),
            dateSizeSp = (override?.dateSizeSp ?: settings.date.sizeSp).coerceAtLeast(1f),
        )
    }

    fun resolveOffsets(settings: WidgetSettings, targetHeightDp: Int): ResolvedWidgetOffsets {
        val profile = profileForHeightDp(targetHeightDp)
        val override = overrideFor(settings, profile)
        return ResolvedWidgetOffsets(
            profile = profile,
            timeXDp = finiteOrZero(override?.timeXDp ?: settings.time.xDp),
            timeYDp = finiteOrZero(override?.timeYDp ?: settings.time.yDp),
            dateXDp = finiteOrZero(override?.dateXDp ?: settings.date.xDp),
            dateYDp = finiteOrZero(override?.dateYDp ?: settings.date.yDp),
        )
    }

    private fun finiteOrZero(value: Float): Float = if (value.isFinite()) value else 0f

    private fun overrideFor(
        settings: WidgetSettings,
        profile: DigitalWidgetProfile,
    ): ProfileOverride? = when (profile) {
        DigitalWidgetProfile.FOUR_BY_ONE -> settings.fourByOne
        DigitalWidgetProfile.FOUR_BY_TWO -> settings.fourByTwo
    }
}
