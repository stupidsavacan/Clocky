package com.stupidsavacan.clocky.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.library.QuickTune
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import com.stupidsavacan.clocky.design.storage.BuiltinFavorites
import com.stupidsavacan.clocky.design.storage.FileDesignRepository
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import com.stupidsavacan.clocky.widget.digital.DigitalWidgetUpdater
import com.stupidsavacan.clocky.widget.easy.DesignExchangeController
import com.stupidsavacan.clocky.widget.easy.DesignLabels
import com.stupidsavacan.clocky.widget.easy.ExportSource
import com.stupidsavacan.clocky.widget.easy.GalleryEntry
import com.stupidsavacan.clocky.widget.easy.GalleryScreen
import com.stupidsavacan.clocky.widget.easy.MyDesignsDialogs
import com.stupidsavacan.clocky.widget.studio.StudioActivity
import com.stupidsavacan.clocky.widget.easy.QuickTuneScreen
import org.json.JSONObject

/**
 * Easy Creation host (End-State 6): Gallery -> optional Quick Tune -> finished widget.
 *
 * - A fresh add opens the Gallery with Clocky Default selected, so "Add this clock" finishes in one tap.
 * - Reconfigure of a design that carries Quick Tune tokens opens Quick Tune on the saved design.
 *   A design from before Phase 1B (no tokens) opens the detailed editor instead, unchanged.
 * - Nothing is persisted until the user finishes. Back out of the Gallery cancels with
 *   RESULT_CANCELED, which makes the launcher drop a freshly added widget (AppWidget result contract);
 *   cancelling a reconfigure leaves the placed widget exactly as it was.
 * - Selecting, tuning and previewing all use the production RemoteViews path (DesignPreview).
 */
class DigitalWidgetConfigActivity : AppCompatActivity() {
    private enum class Screen { GALLERY, QUICK_TUNE }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var store: SharedPreferencesDesignStore
    private lateinit var repository: FileDesignRepository
    private lateinit var host: FrameLayout

    private var screen = Screen.GALLERY
    private var selectedId: String? = BuiltinDesigns.CLOCKY_DEFAULT_ID
    private lateinit var draft: DigitalDesign
    private var previewClass: SizeClass? = null
    private var cameFromGallery = true
    private var tune: QuickTuneScreen? = null
    private lateinit var exchange: DesignExchangeController

    private val detailedEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            RESULT_OK -> finishOk()
            StudioActivity.RESULT_BROWSE_DESIGNS -> {
                selectedId = BuiltinDesigns.CLOCKY_DEFAULT_ID
                draft = BuiltinDesigns.default.instantiate()
                showGallery()
            }
            else -> if (screen == Screen.QUICK_TUNE) showQuickTune(cameFromGallery) else finishCanceled()
        }
    }

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
        repository = FileDesignRepository(this)
        exchange = DesignExchangeController(this, repository, onImported = { galleryScreen?.showImported(it) }).also {
            it.sourceFor = { key -> exportSourceFor(key) }
            savedInstanceState?.let { state -> it.restoreState(state) }
        }
        host =FrameLayout(this).also {
            setContentView(it)
            ViewCompat.setOnApplyWindowInsetsListener(it) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })

        draft = BuiltinDesigns.default.instantiate()
        if (savedInstanceState != null) {
            restore(savedInstanceState)
            return
        }
        val saved = if (store.has(appWidgetId)) store.load(appWidgetId).design else null
        when {
            saved == null -> showGallery()
            QuickTune.isTunable(saved) -> {
                draft = saved
                selectedId = saved.source?.builtinId
                showQuickTune(fromGallery = false)
            }
            else -> {
                // A Phase 1A design has no tokens to tune; keep its editor, with a way to the Gallery.
                selectedId = null
                launchDetailedEditor(saved, offerGallery = true)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        outState.putString(STATE_SCREEN, screen.name)
        outState.putString(STATE_SELECTED, selectedId)
        outState.putString(STATE_DRAFT, DigitalDesignCodec.encode(tune?.draft ?: draft).toString())
        outState.putBoolean(STATE_FROM_GALLERY, cameFromGallery)
        exchange.saveState(outState)
        (tune?.previewClass ?: previewClass)?.let { outState.putString(STATE_CLASS, it.name) }
    }

    private fun restore(state: Bundle) {
        selectedId = state.getString(STATE_SELECTED)
        state.getString(STATE_DRAFT)?.let { json ->
            runCatching { DigitalDesignCodec.decode(JSONObject(json)) }.getOrNull()?.let { draft = it }
        }
        previewClass = state.getString(STATE_CLASS)?.let { name -> SizeClass.entries.firstOrNull { it.name == name } }
        cameFromGallery = state.getBoolean(STATE_FROM_GALLERY, true)
        when (state.getString(STATE_SCREEN)) {
            Screen.QUICK_TUNE.name -> showQuickTune(cameFromGallery)
            else -> showGallery()
        }
    }

    // ---- screens ----

    private fun inflate(layout: Int): View {
        host.removeAllViews()
        return LayoutInflater.from(this).inflate(layout, host as ViewGroup, false).also { host.addView(it) }
    }

    private fun showGallery() {
        screen = Screen.GALLERY
        tune = null
        val hostClass = previewClass ?: DigitalWidgetUpdater.hostSizeClass(this, appWidgetId)
        GalleryScreen(
            root = inflate(R.layout.clocky_gallery),
            sizeClass = hostClass,
            initialSelectedId = selectedId,
            repository = repository,
            builtinFavorites = BuiltinFavorites(this),
            onSelect = { entry -> select(entry) },
            onAdd = { finishWith(selectedDesign()) },
            onCustomize = {
                draft = selectedDesign()
                // A saved pre-1B design has no tokens to tune; its editor is the detailed one.
                if (QuickTune.isTunable(draft)) showQuickTune(fromGallery = true) else launchDetailedEditor(draft, offerGallery = true)
            },
            onImport = { exchange.showImportChoices() },
            onExport = { key -> exchange.exportToFile(key) },
            onShare = { key -> exchange.share(key) },
        ).also { gallery -> galleryScreen = gallery }
    }

    /** What the Gallery card [key] exports (a built-in or a My Design); null when the card is gone. */
    private fun exportSourceFor(key: String): ExportSource? {
        val builtin = BuiltinDesigns.byId(key)
        return when {
            builtin != null -> ExportSource(getString(DesignLabels.design(builtin.id)), DigitalDesignCodec.encode(builtin.instantiate()))
            else -> repository.get(key)?.let { ExportSource(it.name, repository.rawDesign(it.id) ?: DigitalDesignCodec.encode(it.design)) }
        }
    }

    private var galleryScreen: GalleryScreen? = null

    private fun select(entry: GalleryEntry) {
        selectedId = entry.key
        draft = entry.design
        galleryScreen?.select(entry.key)
    }

    /** The selected built-in or saved design (a snapshot); Clocky Default when the selection is gone. */
    private fun selectedDesign(): DigitalDesign =
        BuiltinDesigns.byId(selectedId)?.instantiate()
            ?: selectedId?.let { repository.get(it)?.design }
            ?: BuiltinDesigns.default.instantiate()

    private fun showQuickTune(fromGallery: Boolean) {
        screen = Screen.QUICK_TUNE
        cameFromGallery = fromGallery
        galleryScreen = null
        tune = QuickTuneScreen(
            root = inflate(R.layout.clocky_quick_tune),
            appWidgetId = appWidgetId,
            initialDraft = draft,
            initialClass = previewClass,
            onDraftChanged = { draft = it },
            onDone = { finishWith(tune?.draft ?: draft) },
            onBack = { onBack() },
            onDetail = { launchDetailedEditor(tune?.draft ?: draft, offerGallery = false) },
            onSaveDesign = { MyDesignsDialogs.saveDesign(this, repository, tune?.draft ?: draft) },
        )
    }

    private fun launchDetailedEditor(design: DigitalDesign, offerGallery: Boolean) {
        previewClass = tune?.previewClass ?: previewClass
        detailedEditor.launch(
            Intent(this, StudioActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(StudioActivity.EXTRA_DRAFT_JSON, DigitalDesignCodec.encode(design).toString())
                .putExtra(StudioActivity.EXTRA_OFFER_GALLERY, offerGallery),
        )
    }

    private fun onBack() {
        when {
            screen == Screen.QUICK_TUNE && cameFromGallery -> showGallery()
            else -> finishCanceled()
        }
    }

    // ---- results ----

    private fun finishWith(design: DigitalDesign) {
        store.save(WidgetInstance(appWidgetId, design))
        finishOk()
    }

    private fun finishOk() {
        DigitalWidgetUpdater.update(this, AppWidgetManager.getInstance(this), appWidgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun finishCanceled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private companion object {
        const val STATE_SCREEN = "screen"
        const val STATE_SELECTED = "selected"
        const val STATE_DRAFT = "draft"
        const val STATE_CLASS = "class"
        const val STATE_FROM_GALLERY = "fromGallery"
    }
}
