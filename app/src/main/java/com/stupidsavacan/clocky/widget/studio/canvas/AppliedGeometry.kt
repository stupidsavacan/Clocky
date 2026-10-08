package com.stupidsavacan.clocky.widget.studio.canvas

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.deskclock.R
import com.stupidsavacan.clocky.studio.TextTarget
import com.stupidsavacan.clocky.studio.canvas.Box
import com.stupidsavacan.clocky.studio.canvas.ElementGeometry

/**
 * Maps between the canvas overlay's pixels and the widget's dp space. [pxPerDp] already includes
 * the preview's down-scale, so one screen dp may cover more than one widget dp.
 */
class ScreenTransform(val originX: Float, val originY: Float, val pxPerDp: Float) {
    fun toDpX(px: Float) = (px - originX) / pxPerDp
    fun toDpY(px: Float) = (px - originY) / pxPerDp
    fun toPxX(dp: Float) = originX + dp * pxPerDp
    fun toPxY(dp: Float) = originY + dp * pxPerDp
}

/** What the applied RemoteViews actually look like right now, in widget dp (see [AppliedGeometryReader]). */
class AppliedGeometry(
    val widthDp: Float,
    val heightDp: Float,
    val transform: ScreenTransform,
    val elements: List<ElementGeometry>,
) {
    fun element(target: TextTarget): ElementGeometry? = elements.firstOrNull { it.target == target }
}

/**
 * Reads element bounds and baselines from the production RemoteViews tree that [com.stupidsavacan.clocky.widget.digital.PreviewHost]
 * applied. There is no second geometry model: the slot views and their visible [TextView]s are
 * measured where they really are, including the translation the offsets produced and the preview's
 * scale. Returns null until the tree has been laid out.
 */
object AppliedGeometryReader {
    private val SLOTS = listOf(
        TextTarget.TIME to R.id.clocky_time_slot,
        TextTarget.DATE to R.id.clocky_date_slot,
        TextTarget.INFO to R.id.clocky_info_slot,
    )

    /**
     * [root] is the applied RemoteViews root; [ancestor] is a common ancestor of it and the overlay,
     * and [shiftX]/[shiftY] is the overlay's own position inside [ancestor] (so results are overlay pixels).
     */
    fun read(root: View, ancestor: ViewGroup, density: Float, shiftX: Float = 0f, shiftY: Float = 0f): AppliedGeometry? {
        fun map(view: View, rect: RectF): Boolean = mapToAncestor(view, ancestor, rect).also { if (it) rect.offset(-shiftX, -shiftY) }
        if (root.width <= 0 || root.height <= 0 || density <= 0f) return null
        val widthDp = root.width / density
        val heightDp = root.height / density

        val whole = RectF(0f, 0f, root.width.toFloat(), root.height.toFloat())
        if (!map(root, whole)) return null
        val pxPerDp = whole.width() / widthDp
        if (!(pxPerDp > 0f)) return null
        val transform = ScreenTransform(whole.left, whole.top, pxPerDp)

        val elements = SLOTS.mapNotNull { (target, id) ->
            val slot = root.findViewById<View>(id) ?: return@mapNotNull null
            if (slot.visibility != View.VISIBLE) return@mapNotNull null
            val texts = visibleTexts(slot)
            if (texts.isEmpty()) return@mapNotNull null
            var union: RectF? = null
            var baseline: Float? = null
            texts.forEach { tv ->
                val r = RectF(0f, 0f, tv.width.toFloat(), tv.height.toFloat())
                if (!map(tv, r)) return@forEach
                union = union?.apply { union(r) } ?: RectF(r)
                if (baseline == null && tv.baseline >= 0) {
                    val p = RectF(0f, tv.baseline.toFloat(), 0f, tv.baseline.toFloat())
                    if (map(tv, p)) baseline = transform.toDpY(p.top)
                }
            }
            val u = union ?: return@mapNotNull null
            ElementGeometry(
                target,
                Box(transform.toDpX(u.left), transform.toDpY(u.top), transform.toDpX(u.right), transform.toDpY(u.bottom)),
                baseline,
            )
        }
        return AppliedGeometry(widthDp, heightDp, transform, elements)
    }

    private fun visibleTexts(view: View): List<TextView> {
        if (view.visibility != View.VISIBLE) return emptyList()
        if (view is TextView) return if (view.width > 0 && view.height > 0) listOf(view) else emptyList()
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { visibleTexts(view.getChildAt(it)) }
    }

    /** Walks view → ancestor applying each view's matrix (scale, translation) and position. False if detached. */
    private fun mapToAncestor(view: View, ancestor: ViewGroup, rect: RectF): Boolean {
        var v: View = view
        while (v !== ancestor) {
            v.matrix.mapRect(rect)
            rect.offset(v.left.toFloat(), v.top.toFloat())
            val parent = v.parent as? View ?: return false
            rect.offset(-parent.scrollX.toFloat(), -parent.scrollY.toFloat())
            v = parent
        }
        return true
    }
}
