package com.stupidsavacan.clocky.widget.easy

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.android.deskclock.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.stupidsavacan.clocky.design.library.BuiltinDesign
import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.library.Kits
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.resolve.SizeContext
import com.stupidsavacan.clocky.widget.digital.DesignPreview
import kotlin.math.roundToInt

/**
 * Gallery (End-State 6): the built-in designs as live cards. Every card is the production
 * RemoteViews (resolve -> fit -> compose -> apply), so its TextClocks tick on their own and the card
 * shows what the placed widget will show. Selecting a card only marks it; adding or customizing is
 * explicit, so a user who likes the preselected Clocky Default finishes with one tap.
 */
class GalleryScreen(
    private val root: View,
    private val sizeClass: SizeClass,
    initialSelectedId: String?,
    private val onSelect: (BuiltinDesign) -> Unit,
    private val onAdd: () -> Unit,
    private val onCustomize: () -> Unit,
) {
    private val context = root.context
    private val density = context.resources.displayMetrics.density
    private val grid: GridLayout = root.findViewById(R.id.clocky_gallery_grid)
    private val cards = LinkedHashMap<String, Card>()
    private var selectedId: String? = initialSelectedId

    private class Card(val design: BuiltinDesign, val container: View, val column: View, val ring: GradientDrawable)

    init {
        root.findViewById<TextView>(R.id.clocky_gallery_subtitle).setText(
            if (sizeClass == SizeClass.STRIP) R.string.clocky_gallery_subtitle_strip else R.string.clocky_gallery_subtitle_card,
        )
        buildMoodChips()
        buildCards()
        root.findViewById<Button>(R.id.clocky_gallery_add).setOnClickListener { onAdd() }
        root.findViewById<Button>(R.id.clocky_gallery_customize).setOnClickListener { onCustomize() }
        refreshSelection()
    }

    fun select(builtinId: String?) {
        selectedId = builtinId
        refreshSelection()
    }

    private fun buildMoodChips() {
        val group: ChipGroup = root.findViewById(R.id.clocky_gallery_moods)
        fun chip(label: String, kitId: String?, checked: Boolean) = Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
            text = label
            isCheckable = true
            isChecked = checked
            minHeight = (48 * density).roundToInt()
            id = View.generateViewId()
            tag = kitId
            setEnsureMinTouchTargetSize(true)
        }
        group.addView(chip(context.getString(R.string.clocky_mood_all), null, true))
        Kits.all.forEach { kit -> group.addView(chip(context.getString(DesignLabels.kit(kit.id)), kit.id, false)) }
        group.setOnCheckedStateChangeListener { g, ids ->
            val kitId = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? String }
            cards.values.forEach { it.container.visibility = if (kitId == null || it.design.kitId == kitId) View.VISIBLE else View.GONE }
        }
    }

    private fun buildCards() {
        val columns = context.resources.getInteger(R.integer.clocky_gallery_columns)
        grid.columnCount = columns
        val metrics = context.resources.displayMetrics
        val availablePx = metrics.widthPixels - (24 * density).roundToInt()
        val cellPx = availablePx / columns
        val cellDp = cellPx / density
        val tileWidthDp = (cellDp - 2 * CARD_MARGIN_DP - 2 * RING_DP).toInt()
        val tileHeightDp = if (sizeClass == SizeClass.STRIP) STRIP_TILE_DP else CARD_TILE_DP
        BuiltinDesigns.all.forEach { builtin ->
            val card = buildCard(builtin, cellPx, tileWidthDp, tileHeightDp)
            cards[builtin.id] = card
            grid.addView(card.container)
        }
    }

    private fun buildCard(builtin: BuiltinDesign, cellPx: Int, tileWidthDp: Int, tileHeightDp: Int): Card {
        val ring = GradientDrawable().apply {
            cornerRadius = (RING_RADIUS_DP * density)
            setColor(0)
            setStroke((RING_DP * density).roundToInt(), 0)
        }
        val tileFrame = FrameLayout(context)
        // Inset like a launcher cell so bare (background-less) designs do not touch the tile edge.
        val innerWidthDp = tileWidthDp - 2 * TILE_INSET_DP
        val innerHeightDp = tileHeightDp - TILE_INSET_DP
        val size = SizeContext(innerWidthDp, innerHeightDp, innerWidthDp, innerHeightDp)
        val spec = DesignPreview.render(tileFrame, builtin.instantiate(), size)
        val tileWidthPx = (tileWidthDp * density).roundToInt()
        val tileHeightPx = (tileHeightDp * density).roundToInt()
        val tile = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = TILE_RADIUS_DP * density
                setColor(DesignPreview.backdropColor(spec))
            }
            clipToOutline = true
            addView(tileFrame, FrameLayout.LayoutParams((innerWidthDp * density).roundToInt(), (innerHeightDp * density).roundToInt(), Gravity.CENTER))
        }
        val name = TextView(context).apply {
            text = context.getString(DesignLabels.design(builtin.id))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            gravity = Gravity.CENTER
            minHeight = (24 * density).roundToInt()
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (RING_DP * density).roundToInt() + (RING_PADDING_DP * density).roundToInt()
            setPadding(pad, pad, pad, pad)
            background = ring
            addView(tile, LinearLayout.LayoutParams(tileWidthPx, tileHeightPx))
            addView(name, LinearLayout.LayoutParams(tileWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * density).roundToInt()
            })
            isClickable = true
            isFocusable = true
            setOnClickListener { onSelect(builtin) }
        }
        val container = FrameLayout(context).apply {
            val margin = (CARD_MARGIN_DP * density).roundToInt()
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            layoutParams = GridLayout.LayoutParams().apply {
                width = cellPx
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(0, margin, 0, margin)
            }
        }
        return Card(builtin, container, column, ring)
    }

    private fun refreshSelection() {
        val ringColor = com.google.android.material.color.MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface)
        cards.forEach { (id, card) ->
            val selected = id == selectedId
            card.ring.setStroke((RING_DP * density).roundToInt(), if (selected) ringColor else 0)
            val label = context.getString(DesignLabels.design(id))
            card.column.contentDescription = if (selected) context.getString(R.string.clocky_gallery_selected, label) else label
            card.column.isSelected = selected
        }
    }

    private companion object {
        const val CARD_TILE_DP = 118
        const val STRIP_TILE_DP = 64
        const val TILE_RADIUS_DP = 14f
        const val TILE_INSET_DP = 12
        const val RING_DP = 2
        const val RING_PADDING_DP = 2
        const val RING_RADIUS_DP = 18f
        const val CARD_MARGIN_DP = 4
    }
}
