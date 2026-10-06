package com.stupidsavacan.clocky.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextClock
import org.robolectric.RuntimeEnvironment
import com.android.deskclock.R
import com.google.android.material.materialswitch.MaterialSwitch
import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.library.Palettes
import com.stupidsavacan.clocky.design.library.QuickTune
import com.stupidsavacan.clocky.design.library.TypefaceCategory
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.Template
import com.stupidsavacan.clocky.design.model.ThemeMode
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Phase 1B Easy Creation host: Gallery -> optional Quick Tune -> finished widget, under the manifest
 * theme, with the AppWidget result contract. Robolectric is not launcher verification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 34])
class DigitalWidgetConfigActivityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val store get() = SharedPreferencesDesignStore(context)

    /** The launcher binds the widget id before it starts the configure Activity; mimic that. */
    private fun bindWidget(id: Int) {
        val shadow = shadowOf(AppWidgetManager.getInstance(context))
        var created: Int
        do {
            created = shadow.createWidget(com.android.alarmclock.DigitalAppWidgetProvider::class.java, R.layout.clocky_digital_widget)
        } while (created < id)
    }

    private fun launch(widgetId: Int?): ActivityController<DigitalWidgetConfigActivity> {
        if (widgetId != null) bindWidget(widgetId)
        return Robolectric.buildActivity(
            DigitalWidgetConfigActivity::class.java,
            Intent(context, DigitalWidgetConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .apply { if (widgetId != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) },
        ).setup()
    }

    private fun Activity.gallery(): View? = findViewById(R.id.clocky_gallery_root)
    private fun Activity.quickTune(): View? = findViewById(R.id.clocky_quick_tune_root)

    private fun Activity.galleryCards(): List<View> {
        val grid = findViewById<GridLayout>(R.id.clocky_gallery_grid)
        return (0 until grid.childCount).map { grid.getChildAt(it) }
    }

    private fun Activity.clickGalleryCard(index: Int) {
        val cell = galleryCards()[index] as ViewGroup
        cell.getChildAt(0).performClick()
    }

    private fun Activity.add() = findViewById<Button>(R.id.clocky_gallery_add).performClick()
    private fun Activity.customize() = findViewById<Button>(R.id.clocky_gallery_customize).performClick()
    private fun Activity.done() = findViewById<Button>(R.id.clocky_tune_done).performClick()

    @Test
    fun missingWidgetIdFinishesCanceled() {
        launch(null).use { controller ->
            val activity = controller.get()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        }
    }

    @Test
    fun freshAddOpensGalleryWithEightLiveCardsAndNothingPersisted() {
        launch(51).use { controller ->
            val activity = controller.get()
            assertNotNull(activity.gallery())
            assertNull(activity.quickTune())
            assertEquals(BuiltinDesigns.all.size, activity.galleryCards().size)
            assertEquals(8, activity.galleryCards().size)
            // Cards are the production RemoteViews: live TextClocks, not screenshots.
            val clocks = mutableListOf<TextClock>()
            fun collect(v: View) {
                if (v is TextClock) clocks += v
                if (v is ViewGroup) (0 until v.childCount).forEach { collect(v.getChildAt(it)) }
            }
            collect(activity.findViewById(R.id.clocky_gallery_grid))
            assertTrue("expected TextClocks inside the cards", clocks.size >= 8)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertFalse(store.has(51))
        }
    }

    @Test
    fun addWithoutTouchingAnythingSavesClockyDefault() {
        launch(52).use { controller ->
            val activity = controller.get()
            activity.add()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
            assertEquals(52, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
            val saved = store.load(52).design
            assertEquals(BuiltinDesigns.clockyDefault.design, saved)
            assertEquals(BuiltinDesigns.CLOCKY_DEFAULT_ID, saved.source?.builtinId)
        }
    }

    @Test
    fun selectingEachCardThenAddingSavesThatDesign() {
        BuiltinDesigns.all.forEachIndexed { index, builtin ->
            val id = 100 + index
            launch(id).use { controller ->
                val activity = controller.get()
                activity.clickGalleryCard(index)
                activity.add()
                assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
                assertEquals(builtin.design, store.load(id).design)
            }
        }
    }

    @Test
    fun backFromGalleryCancelsAndLeavesNothingBehind() {
        launch(53).use { controller ->
            val activity = controller.get()
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertFalse(store.has(53))
        }
    }

    @Test
    fun customizePreservesTheSelectedDesignAndDoneSavesWhatThePreviewShowed() {
        launch(54).use { controller ->
            val activity = controller.get()
            activity.clickGalleryCard(BuiltinDesigns.all.indexOf(BuiltinDesigns.boldPoster))
            activity.customize()
            assertNotNull(activity.quickTune())
            assertNull(activity.gallery())
            assertFalse("opening Quick Tune must not persist", store.has(54))

            // Pick the Mono palette (second palette item after the kit default is not Mono: find by description).
            val palettes = activity.findViewById<LinearLayout>(R.id.clocky_tune_palettes)
            val mono = (0 until palettes.childCount).map { palettes.getChildAt(it) }
                .first { it.contentDescription == context.getString(R.string.clocky_palette_mono) }
            mono.performClick()
            val serif = activity.findViewById<LinearLayout>(R.id.clocky_tune_typefaces).let { row ->
                (0 until row.childCount).map { row.getChildAt(it) }
                    .first { it.contentDescription == context.getString(R.string.clocky_typeface_serif) }
            }
            serif.performClick()
            activity.done()

            val saved = store.load(54).design
            assertEquals("bold-poster", saved.source?.builtinId)
            assertEquals(Palettes.MONO, saved.style?.palette)
            assertEquals(TypefaceCategory.SERIF, QuickTune.typefaceOf(saved))
            // Everything Quick Tune did not touch is the Poster snapshot.
            assertEquals(BuiltinDesigns.boldPoster.design.time, saved.time)
            assertEquals(BuiltinDesigns.boldPoster.design.background, saved.background)
            assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        }
    }

    @Test
    fun quickTuneDateSwitchAndTextSizeEditTheDraft() {
        launch(55).use { controller ->
            val activity = controller.get()
            activity.customize()
            val date = activity.findViewById<MaterialSwitch>(R.id.clocky_tune_date)
            assertTrue(date.isChecked)
            date.performClick()
            activity.findViewById<Button>(R.id.clocky_tune_size_l).performClick()
            activity.done()
            val saved = store.load(55).design
            assertFalse(saved.date.visible)
            assertEquals(com.stupidsavacan.clocky.design.model.TextSizeStep.LARGE, saved.style?.textSize)
        }
    }

    @Test
    fun materialYouIsRequestedAndStoredEvenWhereItCannotRender() {
        launch(56).use { controller ->
            val activity = controller.get()
            activity.customize()
            val palettes = activity.findViewById<LinearLayout>(R.id.clocky_tune_palettes)
            val my = (0 until palettes.childCount).map { palettes.getChildAt(it) }
                .first { it.contentDescription == context.getString(R.string.clocky_palette_material_you) }
            my.performClick()
            activity.done()
            assertEquals(ThemeMode.MATERIAL_YOU, store.load(56).design.style?.themeMode)
        }
    }

    @Test
    fun surpriseMeStaysInsideTheKitTable() {
        launch(57).use { controller ->
            val activity = controller.get()
            activity.customize()
            repeat(5) { activity.findViewById<Button>(R.id.clocky_tune_surprise).performClick() }
            activity.done()
            val saved = store.load(57).design
            val kit = QuickTune.kitOf(saved)!!
            val combos = kit.combos.map { Triple(it.paletteId, it.typeface, it.cardTemplate to it.stripTemplate) }
            assertTrue(
                Triple(
                    saved.style!!.palette.id,
                    QuickTune.typefaceOf(saved),
                    QuickTune.templateOf(saved, SizeClass.CARD) to QuickTune.templateOf(saved, SizeClass.STRIP),
                ) in combos,
            )
        }
    }

    @Test
    fun backFromQuickTuneReturnsToTheGalleryWithoutSaving() {
        launch(58).use { controller ->
            val activity = controller.get()
            activity.customize()
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            assertNotNull(activity.gallery())
            assertFalse(activity.isFinishing)
            assertFalse(store.has(58))
        }
    }

    @Test
    fun reconfigureOpensQuickTuneOnTheSavedDesignAndCancelKeepsIt() {
        val tuned = QuickTune.selectTypeface(BuiltinDesigns.editorialPaper.design, TypefaceCategory.MONO)
        store.save(WidgetInstance(59, tuned))
        launch(59).use { controller ->
            val activity = controller.get()
            assertNotNull(activity.quickTune())
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertEquals(tuned, store.load(59).design)
        }
    }

    @Test
    fun phase1aDesignWithoutTokensOpensTheDetailedEditor() {
        store.save(WidgetInstance(60, DigitalDesign()))
        launch(60).use { controller ->
            val activity = controller.get()
            val next = shadowOf(activity).nextStartedActivity
            assertEquals(DigitalWidgetAdvancedActivity::class.java.name, next.component?.className)
            assertTrue(next.getBooleanExtra(DigitalWidgetAdvancedActivity.EXTRA_OFFER_GALLERY, false))
        }
    }

    @Test
    fun galleryAndQuickTuneSurviveRecreation() {
        launch(61).use { controller ->
            controller.get().customize()
            controller.get().findViewById<Button>(R.id.clocky_tune_size_s).performClick()
            controller.recreate()
            val activity = controller.get()
            assertNotNull(activity.quickTune())
            activity.done()
            assertEquals(com.stupidsavacan.clocky.design.model.TextSizeStep.SMALL, store.load(61).design.style?.textSize)
        }
    }

    @Test
    fun stripHostTemplateChipsAreTheStripSet() {
        launch(62).use { controller ->
            val activity = controller.get()
            activity.customize()
            activity.findViewById<View>(R.id.clocky_preview_strip).performClick()
            val chips = activity.findViewById<LinearLayout>(R.id.clocky_tune_templates)
            val labels = (0 until chips.childCount).map { (chips.getChildAt(it) as android.widget.TextView).text.toString() }
            assertEquals(
                listOf(Template.INLINE, Template.SPLIT, Template.TIME_FIRST, Template.MINIMAL)
                    .map { context.getString(com.stupidsavacan.clocky.widget.easy.DesignLabels.template(it)) },
                labels,
            )
        }
    }

    @Test
    fun colorTokensAreNeverFlattenedByTheGalleryPath() {
        launch(63).use { controller ->
            val activity = controller.get()
            activity.add()
            val saved = store.load(63).design
            assertEquals(ColorRef.Token(com.stupidsavacan.clocky.design.model.ColorRole.PRIMARY), saved.time.style.color)
        }
    }
}
