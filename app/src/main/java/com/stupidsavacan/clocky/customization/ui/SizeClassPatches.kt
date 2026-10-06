package com.stupidsavacan.clocky.customization.ui

import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.LayoutPatch
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.normalized

/** The stored patch for [sizeClass], or null when that class inherits everything. */
internal fun DigitalDesign.patchOrNull(sizeClass: SizeClass): LayoutPatch? = layout.overrides[sizeClass]

/** Replaces both size-class patches; null or empty patches inherit (and are not stored). */
internal fun DigitalDesign.withPatches(strip: LayoutPatch?, card: LayoutPatch?): DigitalDesign = copy(
    layout = layout
        .withPatch(SizeClass.STRIP, strip ?: LayoutPatch.EMPTY)
        .withPatch(SizeClass.CARD, card ?: LayoutPatch.EMPTY),
).normalized()
