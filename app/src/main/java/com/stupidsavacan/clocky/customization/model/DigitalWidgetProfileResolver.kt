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

/**
 * Resolves AppWidget host dimensions to Clocky's compact (4x1) or regular (4x2) profile.
 *
 * The AOSP provider declares 59dp as its minimum resize height and 129dp as its normal minimum
 * height. The midpoint (94dp) is used as a launcher-independent boundary instead of assuming
 * exact cell pixel sizes. Larger widgets deliberately inherit the 4x2 profile until Clocky adds
 * more explicit size classes.
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

    private fun overrideFor(
        settings: WidgetSettings,
        profile: DigitalWidgetProfile,
    ): ProfileOverride? = when (profile) {
        DigitalWidgetProfile.FOUR_BY_ONE -> settings.fourByOne
        DigitalWidgetProfile.FOUR_BY_TWO -> settings.fourByTwo
    }
}
