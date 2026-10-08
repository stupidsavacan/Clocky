package com.stupidsavacan.clocky.widget.studio

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.deskclock.R
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.resolve.ContrastChecker
import com.stupidsavacan.clocky.design.resolve.ContrastFinding
import com.stupidsavacan.clocky.design.resolve.ContrastLevel
import com.stupidsavacan.clocky.design.resolve.DesignResolver
import com.stupidsavacan.clocky.design.resolve.ResolvedDigitalSpec
import com.stupidsavacan.clocky.design.resolve.TextElementKind
import com.stupidsavacan.clocky.design.resolve.WallpaperHint
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import com.stupidsavacan.clocky.studio.DesignEdits
import com.stupidsavacan.clocky.studio.EditScope
import com.stupidsavacan.clocky.studio.EditSession
import com.stupidsavacan.clocky.studio.Slot
import com.stupidsavacan.clocky.studio.TextTarget
import com.stupidsavacan.clocky.widget.digital.DegradationNotices
import com.stupidsavacan.clocky.widget.digital.DesignPreview
import com.stupidsavacan.clocky.widget.digital.DigitalWidgetUpdater
import com.stupidsavacan.clocky.widget.digital.PreviewHost
import com.stupidsavacan.clocky.widget.digital.SizeClassLabels
import com.stupidsavacan.clocky.widget.studio.canvas.CanvasHost
import com.stupidsavacan.clocky.widget.studio.canvas.CanvasOverlayView
import org.json.JSONObject
import java.util.Locale

/** Survives configuration changes; the draft and its undo history live here, never in the saved widget. */
class StudioViewModel : ViewModel() {
    var session: EditSession? = null
    var slot: Slot = Slot.TIME
    var advanced: Boolean = false
    var thisSizeOnly: Boolean = false
    var previewClass: SizeClass? = null

    /** The text element the canvas and the Position controls act on; null until one is chosen. */
    var selected: TextTarget? = null
}

/**
 * Studio v1 (CLOCKY_END_STATE.md 7, docs/architecture/PHASE_2_STUDIO.md): the semantic-slot editor
 * for the Digital widget. It edits a draft through an [EditSession], previews it through the same
 * production RemoteViews path as the widget ([PreviewHost]), and writes the widget only on Save.
 * Back out (or Cancel) leaves the saved widget untouched, asking first when there are edits.
 */
class StudioActivity : AppCompatActivity(), StudioHost, CanvasHost {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var vm: StudioViewModel
    private lateinit var store: SharedPreferencesDesignStore
    private lateinit var previewHost: PreviewHost
    private lateinit var panels: StudioPanels

    private lateinit var tabs: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var checks: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var scopeSwitch: MaterialSwitch
    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var classToggle: MaterialButtonToggleGroup
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var backdrop: View
    private lateinit var canvas: CanvasOverlayView
    private val tabChips = mutableMapOf<Slot, Chip>()
    private var binding = false
    private var wallpaper: WallpaperHint? = null

    private val session: EditSession get() = vm.session!!

    // ---- StudioHost ----

    override val context: Context get() = this
    override val design: DigitalDesign get() = session.design
    override val reference: DigitalDesign get() = session.reference
    override val referenceIsPreset: Boolean get() = session.referenceIsPreset
    override val previewClass: SizeClass get() = vm.previewClass ?: SizeClass.CARD
    override val advanced: Boolean get() = vm.advanced
    override val scope: EditScope get() = if (vm.thisSizeOnly) EditScope(previewClass) else EditScope.ALL
    override var spec: ResolvedDigitalSpec? = null
        private set

    override fun edit(id: String, key: String?, rebuild: Boolean, transform: (DigitalDesign) -> DigitalDesign) {
        if (!session.apply(id, key, transform)) return
        if (rebuild) rebuildPanel()
        render()
    }

    override fun endGesture() {
        session.endGesture()
        updateChrome()
    }

    override fun refreshPanel() = rebuildPanel()

    // ---- CanvasHost: the canvas is an input layer over the preview; edits go through the session ----

    override val canDrag: Boolean get() = Build.VERSION.SDK_INT >= DesignResolver.MIN_TRANSLATION_SDK
    override val rtl: Boolean get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    override val selected: TextTarget? get() = vm.selected

    override fun select(target: TextTarget) {
        vm.selected = target
        val slot = slotOf(target)
        if (vm.slot in TEXT_SLOTS && vm.slot != slot) {
            vm.slot = slot
            updateTabs()
            rebuildPanel()
            scroll.scrollTo(0, 0)
        }
        canvas.invalidate()
    }

    override fun offsetOf(target: TextTarget): Pair<Float, Float> = DesignEdits.offsetOf(design, target, scope)

    override fun entrySizeDp(): Pair<Float, Float>? = previewHost.lastEntrySize?.let { it.width to it.height }

    override fun paddingDp(): Float = spec?.paddingDp ?: 0f

    override fun moveTo(target: TextTarget, xDp: Float, yDp: Float) {
        val key = "canvas.move.${target.name}"
        edit(key, key, rebuild = false) { DesignEdits.setOffset(it, target, xDp, yDp, scope) }
    }

    override fun endMove() {
        session.endGesture()
        // The Position sliders (Layout panel) show the new values.
        rebuildPanel()
        updateChrome()
    }

    override fun cancelMove() {
        session.endGesture()
        if (session.undo()) {
            rebuildPanel()
            render()
        }
    }

    // ---- lifecycle ----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AppWidget hosts require configuration activities to opt in to success explicitly.
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        store = SharedPreferencesDesignStore(this)
        vm = ViewModelProvider(this)[StudioViewModel::class.java]
        if (vm.session == null) initSession(savedInstanceState)

        setContentView(R.layout.clocky_studio)
        val root = findViewById<View>(R.id.clocky_studio_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        bindViews()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestExit()
        })
        wallpaper = readWallpaperHint()
        rebuildTabs()
        rebuildPanel()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val s = vm.session ?: return
        outState.putString(STATE_BASELINE, DigitalDesignCodec.encode(s.baseline).toString())
        outState.putString(STATE_DRAFT, DigitalDesignCodec.encode(s.design).toString())
        outState.putString(STATE_SLOT, vm.slot.name)
        outState.putBoolean(STATE_ADVANCED, vm.advanced)
        outState.putBoolean(STATE_THIS_SIZE, vm.thisSizeOnly)
        vm.previewClass?.let { outState.putString(STATE_CLASS, it.name) }
        vm.selected?.let { outState.putString(STATE_SELECTED, it.name) }
    }

    /**
     * A fresh start opens the draft the host passed (or the stored design). After process death the
     * ViewModel is gone, so the baseline and draft come back from saved state; undo history does not.
     */
    private fun initSession(saved: Bundle?) {
        val restoredBaseline = saved?.getString(STATE_BASELINE)?.let(::decode)
        val restoredDraft = saved?.getString(STATE_DRAFT)?.let(::decode)
        val opened = restoredBaseline ?: DesignEdits.prepare(
            intent.getStringExtra(EXTRA_DRAFT_JSON)?.let(::decode) ?: store.load(appWidgetId).design,
        )
        val preset = DesignEdits.presetReference(opened)
        val s = EditSession(opened, preset ?: opened, referenceIsPreset = preset != null)
        restoredDraft?.let { s.adopt(it) }
        vm.session = s
        saved?.getString(STATE_SLOT)?.let { name -> Slot.entries.firstOrNull { it.name == name }?.let { vm.slot = it } }
        vm.advanced = saved?.getBoolean(STATE_ADVANCED) ?: false
        vm.thisSizeOnly = saved?.getBoolean(STATE_THIS_SIZE) ?: false
        vm.previewClass = saved?.getString(STATE_CLASS)?.let { name -> SizeClass.entries.firstOrNull { it.name == name } }
        vm.selected = saved?.getString(STATE_SELECTED)?.let { name -> TextTarget.entries.firstOrNull { it.name == name } }
    }

    private fun decode(json: String): DigitalDesign? =
        runCatching { DigitalDesignCodec.decode(JSONObject(json)) }.getOrNull()

    // ---- views ----

    private fun bindViews() {
        tabs = findViewById(R.id.clocky_studio_tabs)
        panel = findViewById(R.id.clocky_studio_panel)
        checks = findViewById(R.id.clocky_studio_checks)
        scroll = findViewById(R.id.clocky_studio_scroll)
        scopeSwitch = findViewById(R.id.clocky_studio_scope)
        modeToggle = findViewById(R.id.clocky_studio_mode)
        classToggle = findViewById(R.id.clocky_preview_size_class)
        undoButton = findViewById(R.id.clocky_studio_undo)
        redoButton = findViewById(R.id.clocky_studio_redo)
        backdrop = findViewById(R.id.clocky_preview_panel)
        // Studio shows its own notices (degradations and contrast) inside the scrolling panel;
        // the strip under the preview carries the canvas hint instead (set once the canvas exists).

        previewHost = PreviewHost(
            frame = findViewById<FrameLayout>(R.id.clocky_preview_frame),
            appWidgetId = appWidgetId,
            maxDisplayHeightDp = resources.getInteger(R.integer.clocky_tune_preview_max_height_dp),
        ) { resolved ->
            spec = resolved
            // The Info panel's "sample" note depends on the resolved spec, which only exists after a render.
            val sample = resolved.info?.isSample == true
            if (vm.slot == Slot.INFO && sample != panels.showsSampleNote) rebuildPanel()
            // The weight note defers to the host-fallback notice, which is only known after a render.
            else if (hostFallbackFonts(resolved) != panels.builtHostFallback) rebuildPanel()
            (backdrop.background?.mutate() as? GradientDrawable)?.setColor(DesignPreview.backdropColor(resolved))
            renderChecks(resolved)
            if (::canvas.isInitialized) canvas.refresh()
        }
        if (vm.previewClass == null) vm.previewClass = previewHost.hostSizeClass()
        if (vm.selected == null) vm.selected = targetOf(vm.slot)
        canvas = CanvasOverlayView(this, this) { findViewById<FrameLayout>(R.id.clocky_preview_frame).getChildAt(0) }
        findViewById<FrameLayout>(R.id.clocky_preview_panel).addView(
            canvas, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        findViewById<TextView>(R.id.clocky_preview_notice).apply {
            visibility = View.VISIBLE
            setText(if (canDrag) R.string.clocky_studio_canvas_hint else R.string.clocky_studio_canvas_unavailable)
        }
        panels = StudioPanels(this, panel)

        binding = true
        classToggle.check(SizeClassLabels.button(previewClass))
        modeToggle.check(if (vm.advanced) R.id.clocky_studio_mode_advanced else R.id.clocky_studio_mode_basic)
        binding = false
        classToggle.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !binding) {
                vm.previewClass = SizeClassLabels.fromButton(id)
                updateScopeSwitch()
                rebuildPanel()
                render()
            }
        }
        modeToggle.addOnButtonCheckedListener { _, id, checked ->
            if (checked && !binding) {
                vm.advanced = id == R.id.clocky_studio_mode_advanced
                rebuildPanel()
            }
        }
        scopeSwitch.setOnCheckedChangeListener { _, on ->
            if (!binding) {
                vm.thisSizeOnly = on
                rebuildPanel()
            }
        }
        undoButton.setOnClickListener {
            if (session.undo()) {
                rebuildPanel(); render(); it.announceForAccessibility(getString(R.string.clocky_studio_undone))
            }
        }
        redoButton.setOnClickListener {
            if (session.redo()) {
                rebuildPanel(); render(); it.announceForAccessibility(getString(R.string.clocky_studio_redone))
            }
        }
        findViewById<View>(R.id.clocky_studio_back).setOnClickListener { requestExit() }
        findViewById<Button>(R.id.clocky_studio_save).setOnClickListener { save() }
        findViewById<View>(R.id.clocky_studio_more).setOnClickListener { showMenu(it) }
    }

    private fun rebuildTabs() {
        tabs.removeAllViews()
        tabChips.clear()
        Slot.entries.forEach { slot ->
            val chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                isCheckable = true
                setEnsureMinTouchTargetSize(true)
                setOnClickListener {
                    if (vm.slot != slot) {
                        vm.slot = slot
                        targetOf(slot)?.let { vm.selected = it }
                        canvas.invalidate()
                        updateTabs()
                        rebuildPanel()
                        scroll.scrollTo(0, 0)
                    } else {
                        isChecked = true
                    }
                }
            }
            tabChips[slot] = chip
            tabs.addView(chip, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, (48 * resources.displayMetrics.density).toInt()).apply {
                marginEnd = (6 * resources.displayMetrics.density).toInt()
            })
        }
        updateTabs()
    }

    /** Marks the slots that differ from the design they started from; the dot is also spoken. */
    private fun updateTabs() {
        tabChips.forEach { (slot, chip) ->
            val name = getString(StudioPanels.slotLabel(slot))
            val changed = DesignEdits.isSlotModified(design, slot, reference)
            chip.text = if (changed) "$name •" else name
            chip.contentDescription = if (changed) getString(R.string.clocky_studio_tab_changed, name) else name
            chip.isChecked = slot == vm.slot
        }
    }

    private fun updateScopeSwitch() {
        val relevant = vm.slot in listOf(Slot.TIME, Slot.DATE, Slot.INFO, Slot.BACKGROUND, Slot.LAYOUT)
        scopeSwitch.visibility = if (relevant) View.VISIBLE else View.GONE
        val sizeName = getString(SizeClassLabels.label(previewClass))
        binding = true
        scopeSwitch.text = getString(R.string.clocky_studio_only_size, sizeName)
        scopeSwitch.isChecked = vm.thisSizeOnly
        binding = false
    }

    private fun rebuildPanel() {
        val y = scroll.scrollY
        updateScopeSwitch()
        panels.build(vm.slot)
        updateTabs()
        updateChrome()
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun render() {
        previewHost.schedule(design, previewClass)
        updateTabs()
        updateChrome()
    }

    private fun updateChrome() {
        panels.refreshReset()
        undoButton.isEnabled = session.canUndo
        undoButton.alpha = if (session.canUndo) 1f else 0.38f
        redoButton.isEnabled = session.canRedo
        redoButton.alpha = if (session.canRedo) 1f else 0.38f
    }

    // ---- checks: degradation disclosure and contrast ----

    private fun renderChecks(resolved: ResolvedDigitalSpec) {
        checks.removeAllViews()
        val rows = StudioRows(this, checks)
        val notes = DegradationNotices.describe(this, resolved.degradations)
        val findings = ContrastChecker.check(resolved, wallpaper)
        val warnings = findings.filter { it.level == ContrastLevel.KNOWN_POOR || it.level == ContrastLevel.LIKELY_RISK }
        val unknown = findings.any { it.level == ContrastLevel.UNKNOWN }
        if (notes.isEmpty() && warnings.isEmpty() && !unknown) return

        rows.header(getString(R.string.clocky_studio_checks_title))
        notes.forEach { rows.note(it, emphasized = true) }
        warnings.forEach { finding ->
            rows.note(contrastText(finding), emphasized = true)
            finding.suggestedRgb?.let { rgb ->
                val target = when (finding.element) {
                    TextElementKind.TIME -> TextTarget.TIME
                    TextElementKind.DATE -> TextTarget.DATE
                    TextElementKind.INFO -> TextTarget.INFO
                }
                rows.button(getString(R.string.clocky_studio_contrast_fix, elementName(finding.element))) {
                    edit("contrast.fix.${target.name}") { DesignEdits.setColor(it, target, ColorRef.Fixed(rgb)) }
                }
            }
        }
        if (unknown && warnings.isEmpty()) rows.note(getString(R.string.clocky_studio_contrast_unknown))
    }

    private fun contrastText(f: ContrastFinding): String {
        val ratio = String.format(Locale.ROOT, "%.1f", f.ratio ?: 0.0)
        val required = String.format(Locale.ROOT, "%.1f", f.required)
        val res = if (f.level == ContrastLevel.KNOWN_POOR) R.string.clocky_studio_contrast_known else R.string.clocky_studio_contrast_likely
        return getString(res, elementName(f.element), ratio, required)
    }

    private fun elementName(kind: TextElementKind) = getString(
        when (kind) {
            TextElementKind.TIME -> R.string.clocky_element_time
            TextElementKind.DATE -> R.string.clocky_element_date
            TextElementKind.INFO -> R.string.clocky_element_info
        },
    )

    /** The wallpaper's dominant color when the platform will tell us (API 27+); never a pixel read. */
    private fun readWallpaperHint(): WallpaperHint? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return runCatching {
            WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor?.toArgb()?.let { WallpaperHint(it and 0xFFFFFF) }
        }.getOrNull()
    }

    // ---- menu, save, exit ----

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_RESET_ALL, 0, R.string.clocky_studio_reset_all)
            if (intent.getBooleanExtra(EXTRA_OFFER_GALLERY, false)) menu.add(0, MENU_BROWSE, 1, R.string.clocky_browse_designs)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RESET_ALL -> confirmResetAll()
                    MENU_BROWSE -> exitWith(RESULT_BROWSE_DESIGNS)
                }
                true
            }
        }.show()
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clocky_studio_reset_all_title)
            .setMessage(R.string.clocky_studio_reset_all_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clocky_studio_reset_confirm) { _, _ ->
                edit("reset.all") { DesignEdits.resetAll(reference) }
            }
            .show()
    }

    private fun save() {
        store.save(WidgetInstance(appWidgetId, design))
        DigitalWidgetUpdater.update(this, AppWidgetManager.getInstance(this), appWidgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    /** Back and Cancel never write; with unsaved edits they ask first. */
    private fun requestExit() = confirmDiscardThen { exitWith(RESULT_CANCELED) }

    private fun exitWith(result: Int) {
        if (result == RESULT_BROWSE_DESIGNS && session.isDirty) {
            confirmDiscardThen { setResult(result); finish() }
        } else {
            setResult(result)
            finish()
        }
    }

    private fun confirmDiscardThen(action: () -> Unit) {
        if (!session.isDirty) {
            action()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.clocky_studio_discard_title)
            .setMessage(R.string.clocky_studio_discard_message)
            .setNegativeButton(R.string.clocky_studio_keep_editing, null)
            .setPositiveButton(R.string.clocky_studio_discard) { _, _ -> action() }
            .show()
    }

    companion object {
        /** Design JSON (schema 2) to edit instead of the stored design; never written until Save. */
        const val EXTRA_DRAFT_JSON = "com.stupidsavacan.clocky.extra.DRAFT_JSON"

        /** Offer "Browse designs"; it returns [RESULT_BROWSE_DESIGNS] without saving. */
        const val EXTRA_OFFER_GALLERY = "com.stupidsavacan.clocky.extra.OFFER_GALLERY"
        const val RESULT_BROWSE_DESIGNS = RESULT_FIRST_USER

        private const val STATE_BASELINE = "studio.baseline"
        private const val STATE_DRAFT = "studio.draft"
        private const val STATE_SLOT = "studio.slot"
        private const val STATE_ADVANCED = "studio.advanced"
        private const val STATE_THIS_SIZE = "studio.thisSize"
        private const val STATE_CLASS = "studio.class"
        private const val STATE_SELECTED = "studio.selected"
        private val TEXT_SLOTS = listOf(Slot.TIME, Slot.DATE, Slot.INFO)

        private fun targetOf(slot: Slot): TextTarget? = when (slot) {
            Slot.TIME -> TextTarget.TIME
            Slot.DATE -> TextTarget.DATE
            Slot.INFO -> TextTarget.INFO
            else -> null
        }

        private fun slotOf(target: TextTarget): Slot = when (target) {
            TextTarget.TIME -> Slot.TIME
            TextTarget.DATE -> Slot.DATE
            TextTarget.INFO -> Slot.INFO
        }
        private const val MENU_RESET_ALL = 1
        private const val MENU_BROWSE = 2
    }
}
