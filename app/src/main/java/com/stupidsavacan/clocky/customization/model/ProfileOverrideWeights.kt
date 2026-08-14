package com.stupidsavacan.clocky.customization.model

/**
 * Replaces only the weight portion of an optional profile override.
 *
 * Other profile-owned values (size, position, visibility) are preserved so the weight UI can be
 * shipped independently from later editors without erasing their state. If no override field
 * remains after clearing both weights, the profile collapses back to null and fully inherits base
 * settings.
 */
fun ProfileOverride?.withWeightOverrides(timeWeight: Int?, dateWeight: Int?): ProfileOverride? {
    val updated = (this ?: ProfileOverride()).copy(
        timeWeight = timeWeight?.coerceIn(100, 900),
        dateWeight = dateWeight?.coerceIn(100, 900),
    )
    return updated.takeIf { it.hasAnyOverride() }
}

internal fun ProfileOverride.hasAnyOverride(): Boolean =
    timeWeight != null ||
        dateWeight != null ||
        timeSizeSp != null ||
        dateSizeSp != null ||
        timeXDp != null ||
        timeYDp != null ||
        dateXDp != null ||
        dateYDp != null ||
        dateEnabled != null
