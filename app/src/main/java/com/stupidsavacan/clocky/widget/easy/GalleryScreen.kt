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
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.resolve.SizeContext
import com.stupidsavacan.clocky.design.storage.BuiltinFavorites
import com.stupidsavacan.clocky.design.storage.DesignRepository
import com.stupidsavacan.clocky.design.storage.SavedDesign
import com.stupidsavacan.clocky.widget.digital.DesignPreview
import kotlin.math.roundToInt

/** One Gallery card: an immutable built-in or a user-saved design (key = built-in id or saved UUID). */
class GalleryEntry(
    val key: String,
    val label: String,
    val design: DigitalDesign,
    val kitId: String?,
    val builtin: BuiltinDesign?,
    val saved: SavedDesign?,
    val favorite: Boolean,
)

/**
 * Gallery (End-State 6): the built-in designs as live cards. Every card is the production
 * RemoteViews (resolve -> fit -> compose -> apply), so its TextClocks tick on their own and the card
 * shows what the placed widget will show. Selecting a card only marks it; adding or customizing is
 * explicit, so a user who likes the preselected Clocky Default finishes with one tap.
 *
 * Phase 3C-1 adds My Designs and Favorites: saved designs are cards too, and the action row manages
 * the selected card. Built-ins stay immutable; only their favorite flag is recorded.
 */
class GalleryScreen(
    private val root: View,
    private val sizeClass: SizeClass,
    initialSelectedId: String?,
    private val repository: DesignRepository,
    private val builtinFavorites: BuiltinFavorites,
    private val onSelect: (GalleryEntry) -> Unit,
    private val onAdd: () -> Unit,
    private val onCustomize: () -> Unit,
) {
    private val context = root.context
    private val density = context.resources.displayMetrics.density
    private val grid: GridLayout = root.findViewById(R.id.clocky_gallery_grid)
    private val cards = LinkedHashMap<String, Card>()
    private var selectedId: String? = initialSelectedId
    private var filter: String = FILTER_ALL
    private val empty: TextView = root.findViewById(R.id.clocky_gallery_empty)
    private val actions: View = root.findViewById(R.id.clocky_gallery_actions)
    private val favoriteButton: Button = root.findViewById(R.id.clocky_gallery_favorite)
    private val duplicateButton: Button = root.findViewById(R.id.clocky_gallery_duplicate)
    private val renameButton: Button = root.findViewById(R.id.clocky_gallery_rename)
    private val deleteButton: Button = root.findViewById(R.id.clocky_gallery_delete)

    private class Card(val entry: GalleryEntry, val container: View, val column: View, val ring: GradientDrawable)

    init {
        root.findViewById<TextView>(R.id.clocky_gallery_subtitle).setText(
            when (sizeClass) {
                SizeClass.STRIP -> R.string.clocky_gallery_subtitle_strip
                SizeClass.CARD -> R.string.clocky_gallery_subtitle_card
                SizeClass.SQUARE -> R.string.clocky_gallery_subtitle_square
                SizeClass.LARGE -> R.string.clocky_gallery_subtitle_large
            },
        )
        buildMoodChips()
        reload()
        favoriteButton.setOnClickListener { toggleFavorite() }
        duplicateButton.setOnClickListener { duplicateSelected() }
        renameButton.setOnClickListener { renameSelected() }
        deleteButton.setOnClickListener { deleteSelected() }
        root.findViewById<Button>(R.id.clocky_gallery_add).setOnClickListener { onAdd() }
        root.findViewById<Button>(R.id.clocky_gallery_customize).setOnClickListener { onCustomize() }
    }

    fun select(key: String?) {
        selectedId = key
        refreshSelection()
    }

    private fun buildMoodChips() {
        val group: ChipGroup = root.findViewById(R.id.clocky_gallery_moods)
        fun chip(label: String, filterTag: String, checked: Boolean) = Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
            text = label
            isCheckable = true
            isChecked = checked
            minHeight = (48 * density).roundToInt()
            id = View.generateViewId()
            tag = filterTag
            setEnsureMinTouchTargetSize(true)
        }
        group.addView(chip(context.getString(R.string.clocky_mood_all), FILTER_ALL, true))
        group.addView(chip(context.getString(R.string.clocky_mood_favorites), FILTER_FAVORITES, false))
        group.addView(chip(context.getString(R.string.clocky_mood_my_designs), FILTER_MINE, false))
        Kits.all.forEach { kit -> group.addView(chip(context.getString(DesignLabels.kit(kit.id)), FILTER_KIT + kit.id, false)) }
        group.setOnCheckedStateChangeListener { g, ids ->
            filter = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? String } ?: FILTER_ALL
            applyFilter()
        }
    }

    private fun entries(): List<GalleryEntry> {
        val favorites = builtinFavorites.ids()
        val saved = repository.list().map { GalleryEntry(it.id, it.name, it.design, null, null, it, it.favorite) }
        val builtins = BuiltinDesigns.all.map {
            GalleryEntry(it.id, context.getString(DesignLabels.design(it.id)), it.instantiate(), it.kitId, it, null, it.id in favorites)
        }
        // Saved designs lead: they are the user's own.
        return saved + builtins
    }

    /** Rebuilds every card from the library; call after any change to saved designs or favorites. */
    private fun reload() {
        grid.removeAllViews()
        cards.clear()
        val columns = context.resources.getInteger(R.integer.clocky_gallery_columns)
        grid.columnCount = columns
        val metrics = context.resources.displayMetrics
        val availablePx = metrics.widthPixels - (24 * density).roundToInt()
        val cellPx = availablePx / columns
        val cellDp = cellPx / density
        val tileWidthDp = (cellDp - 2 * CARD_MARGIN_DP - 2 * RING_DP).toInt()
        val tileHeightDp = if (sizeClass == SizeClass.STRIP) STRIP_TILE_DP else CARD_TILE_DP
        entries().forEach { entry ->
            val card = buildCard(entry, cellPx, tileWidthDp, tileHeightDp)
            cards[entry.key] = card
            grid.addView(card.container)
        }
        if (selectedId != null && selectedId !in cards) selectedId = null
        applyFilter()
        refreshSelection()
    }

    private fun matches(entry: GalleryEntry): Boolean = when {
        filter == FILTER_FAVORITES -> entry.favorite
        filter == FILTER_MINE -> entry.saved != null
        filter.startsWith(FILTER_KIT) -> entry.kitId == filter.removePrefix(FILTER_KIT)
        else -> true
    }

    private fun applyFilter() {
        var shown = 0
        cards.values.forEach {
            val visible = matches(it.entry)
            it.container.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) shown++
        }
        empty.visibility = if (shown == 0) View.VISIBLE else View.GONE
        empty.setText(if (filter == FILTER_FAVORITES) R.string.clocky_gallery_empty_favorites else R.string.clocky_gallery_empty_my_designs)
    }

    private val selectedCard: Card? get() = selectedId?.let { cards[it] }

    private fun toggleFavorite() {
        val entry = selectedCard?.entry ?: return
        val next = !entry.favorite
        if (entry.saved != null) repository.setFavorite(entry.key, next) else builtinFavorites.set(entry.key, next)
        reload()
    }

    private fun duplicateSelected() {
        val saved = selectedCard?.entry?.saved ?: return
        val copy = repository.duplicate(saved.id, context.getString(R.string.clocky_my_designs_copy_name, saved.name)) ?: return
        selectedId = copy.id
        reload()
        cards[copy.id]?.let { onSelect(it.entry) }
    }

    private fun renameSelected() {
        val saved = selectedCard?.entry?.saved ?: return
        MyDesignsDialogs.askName(context, R.string.clocky_my_designs_rename_title, saved.name) { name ->
            repository.rename(saved.id, name)
            reload()
        }
    }

    private fun deleteSelected() {
        val saved = selectedCard?.entry?.saved ?: return
        MyDesignsDialogs.confirmDelete(context, saved.name) {
            repository.delete(saved.id)
            if (selectedId == saved.id) selectedId = BuiltinDesigns.CLOCKY_DEFAULT_ID
            reload()
            selectedCard?.let { onSelect(it.entry) }
        }
    }

    private fun buildCard(entry: GalleryEntry, cellPx: Int, tileWidthDp: Int, tileHeightDp: Int): Card {
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
        val spec = DesignPreview.render(tileFrame, entry.design, size, sizeClass)
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
            text = if (entry.favorite) "★ " + entry.label else entry.label
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
            setOnClickListener { onSelect(entry) }
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
        return Card(entry, container, column, ring)
    }

    private fun refreshSelection() {
        val ringColor = com.google.android.material.color.MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface)
        cards.forEach { (id, card) ->
            val selected = id == selectedId
            card.ring.setStroke((RING_DP * density).roundToInt(), if (selected) ringColor else 0)
            val label = if (card.entry.favorite) context.getString(R.string.clocky_gallery_favorite_label, card.entry.label) else card.entry.label
            card.column.contentDescription = if (selected) context.getString(R.string.clocky_gallery_selected, label) else label
            card.column.isSelected = selected
        }
        val entry = selectedCard?.entry
        actions.visibility = if (entry == null) View.GONE else View.VISIBLE
        favoriteButton.setText(if (entry?.favorite == true) R.string.clocky_gallery_unfavorite else R.string.clocky_gallery_favorite)
        val own = if (entry?.saved != null) View.VISIBLE else View.GONE
        duplicateButton.visibility = own
        renameButton.visibility = own
        deleteButton.visibility = own
    }

    private companion object {
        const val FILTER_ALL = "all"
        const val FILTER_FAVORITES = "favorites"
        const val FILTER_MINE = "mine"
        const val FILTER_KIT = "kit:"
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
