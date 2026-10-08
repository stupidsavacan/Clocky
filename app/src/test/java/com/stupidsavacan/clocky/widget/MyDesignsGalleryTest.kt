package com.stupidsavacan.clocky.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.android.deskclock.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.storage.BuiltinFavorites
import com.stupidsavacan.clocky.design.storage.FileDesignRepository
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import com.stupidsavacan.clocky.widget.studio.StudioActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

/** Phase 3C-1: My Designs wired into the Gallery, Quick Tune and Studio, under the manifest theme. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyDesignsGalleryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val repository get() = FileDesignRepository(context)
    private val store get() = SharedPreferencesDesignStore(context)

    @Before
    fun clean() {
        File(context.filesDir, FileDesignRepository.DIR_NAME).deleteRecursively()
        context.getSharedPreferences("clocky_design_library", 0).edit().clear().commit()
    }

    private fun bindWidget(id: Int) {
        val shadow = shadowOf(AppWidgetManager.getInstance(context))
        var created: Int
        do {
            created = shadow.createWidget(com.android.alarmclock.DigitalAppWidgetProvider::class.java, R.layout.clocky_digital_widget)
        } while (created < id)
    }

    private fun launch(widgetId: Int): ActivityController<DigitalWidgetConfigActivity> {
        bindWidget(widgetId)
        return Robolectric.buildActivity(
            DigitalWidgetConfigActivity::class.java,
            Intent(context, DigitalWidgetConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        ).setup()
    }

    private fun Activity.cards(): List<ViewGroup> {
        val grid = findViewById<GridLayout>(R.id.clocky_gallery_grid)
        return (0 until grid.childCount).map { grid.getChildAt(it) as ViewGroup }
    }

    private fun Activity.visibleCards() = cards().filter { it.visibility == View.VISIBLE }
    private fun ViewGroup.select() = getChildAt(0).performClick()
    private fun Activity.button(id: Int) = findViewById<Button>(id)

    private fun Activity.chip(tag: String): Chip {
        val group = findViewById<ChipGroup>(R.id.clocky_gallery_moods)
        return (0 until group.childCount).map { group.getChildAt(it) as Chip }.first { it.tag == tag }
    }

    private fun dialog(): AlertDialog {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun typeNameAndConfirm(name: String) {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val d = dialog()
        val input = (d.findViewById<android.view.ViewGroup>(androidx.appcompat.R.id.custom) ?: d.window!!.decorView as ViewGroup)
            .let { findEdit(it) }
        input.setText(name)
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
    }

    private fun findEdit(v: View): EditText {
        if (v is EditText) return v
        if (v is ViewGroup) (0 until v.childCount).forEach { i -> runCatching { return findEdit(v.getChildAt(i)) } }
        error("no EditText")
    }

    private fun saveFromQuickTune(activity: Activity, name: String) {
        activity.button(R.id.clocky_gallery_customize).performClick()
        activity.button(R.id.clocky_tune_save_design).performClick()
        typeNameAndConfirm(name)
    }

    @Test
    fun myDesignsAndFavoritesShowEmptyStatesWhenNothingIsSaved() {
        launch(61).use { c ->
            val a = c.get()
            assertEquals(View.GONE, a.findViewById<View>(R.id.clocky_gallery_empty).visibility)
            a.chip("mine").performClick()
            assertTrue(a.visibleCards().isEmpty())
            assertEquals(View.VISIBLE, a.findViewById<View>(R.id.clocky_gallery_empty).visibility)
            assertEquals(a.getString(R.string.clocky_gallery_empty_my_designs), a.findViewById<TextView>(R.id.clocky_gallery_empty).text.toString())
            a.chip("favorites").performClick()
            assertEquals(a.getString(R.string.clocky_gallery_empty_favorites), a.findViewById<TextView>(R.id.clocky_gallery_empty).text.toString())
            a.chip("all").performClick()
            assertEquals(8, a.visibleCards().size)
        }
    }

    @Test
    fun quickTuneSavesToMyDesignsAndTheCardAppearsInTheGallery() {
        launch(62).use { c ->
            val a = c.get()
            saveFromQuickTune(a, "Evening")
            val saved = repository.list().single()
            assertEquals("Evening", saved.name)
            assertEquals(BuiltinDesigns.default.design, saved.design)
            // Saving does not finish the config flow or write the widget.
            assertFalse(a.isFinishing)
            assertFalse(store.has(62))
            a.findViewById<View>(R.id.clocky_tune_back).performClick()
            assertEquals(9, a.cards().size)
            a.chip("mine").performClick()
            assertEquals(1, a.visibleCards().size)
        }
    }

    @Test
    fun savedDesignCanBePlacedAndTheWidgetOwnsASnapshot() {
        val tuned = BuiltinDesigns.byId("bold-poster")!!.instantiate()
        val saved = repository.create("Mine", tuned)
        launch(63).use { c ->
            val a = c.get()
            a.chip("mine").performClick()
            a.visibleCards().single().select()
            a.button(R.id.clocky_gallery_add).performClick()
            assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        }
        assertEquals(tuned, store.load(63).design)

        // Editing, duplicating or deleting the saved design never reaches the placed widget.
        repository.rename(saved.id, "Renamed")
        repository.setFavorite(saved.id, true)
        repository.delete(saved.id)
        assertEquals(tuned, store.load(63).design)
    }

    @Test
    fun deleteAsksForConfirmationAndCancelKeepsTheDesign() {
        val saved = repository.create("Keep me", BuiltinDesigns.default.design)
        launch(64).use { c ->
            val a = c.get()
            a.cards().first().select()
            a.button(R.id.clocky_gallery_delete).performClick()
            val d = dialog()
            assertTrue(d.isShowing)
            d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(repository.get(saved.id))
            assertEquals(9, a.cards().size)

            a.button(R.id.clocky_gallery_delete).performClick()
            dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(null, repository.get(saved.id))
            assertEquals(8, a.cards().size)
            // Selection falls back to Clocky Default, so Add still works.
            a.button(R.id.clocky_gallery_add).performClick()
            assertEquals(BuiltinDesigns.default.design, store.load(64).design)
        }
    }

    @Test
    fun renameDuplicateAndFavoriteWorkOnTheSelectedSavedDesign() {
        val saved = repository.create("First", BuiltinDesigns.default.design)
        launch(65).use { c ->
            val a = c.get()
            a.cards().first().select()
            a.button(R.id.clocky_gallery_rename).performClick()
            typeNameAndConfirm("Second")
            assertEquals("Second", repository.get(saved.id)!!.name)

            a.button(R.id.clocky_gallery_duplicate).performClick()
            assertEquals(2, repository.list().size)
            assertTrue(repository.list().any { it.name == a.getString(R.string.clocky_my_designs_copy_name, "Second") })

            a.button(R.id.clocky_gallery_favorite).performClick()
            assertEquals(1, repository.list().count { it.favorite })
            a.chip("favorites").performClick()
            assertEquals(1, a.visibleCards().size)
        }
    }

    @Test
    fun blankNameKeepsTheDialogOpenAndSavesNothing() {
        launch(66).use { c ->
            val a = c.get()
            saveFromQuickTune(a, "  ")
            assertTrue(dialog().isShowing)
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun builtinFavoritesAreRecordedWithoutChangingTheBuiltin() {
        val before = BuiltinDesigns.all.map { it.design }
        launch(67).use { c ->
            val a = c.get()
            a.cards()[1].select()
            assertEquals(View.GONE, a.button(R.id.clocky_gallery_rename).visibility)
            assertEquals(View.GONE, a.button(R.id.clocky_gallery_delete).visibility)
            a.button(R.id.clocky_gallery_favorite).performClick()
            assertEquals(setOf(BuiltinDesigns.all[1].id), BuiltinFavorites(context).ids())
            a.chip("favorites").performClick()
            assertEquals(1, a.visibleCards().size)
        }
        assertEquals(before, BuiltinDesigns.all.map { it.design })
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun savedDesignsSurviveRecreatingTheActivity() {
        repository.create("Persist", BuiltinDesigns.default.design)
        launch(68).use { c -> assertEquals(9, c.get().cards().size) }
        launch(69).use { c -> assertEquals(9, c.get().cards().size) }
    }

    @Test
    fun studioMenuOffersSaveToMyDesigns() {
        bindWidget(70)
        val controller = Robolectric.buildActivity(
            StudioActivity::class.java,
            Intent(context, StudioActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 70),
        ).setup()
        controller.use {
            val a = it.get()
            a.findViewById<View>(R.id.clocky_studio_more).performClick()
            val popup = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu()
            val item = (0 until popup.menu.size()).map { i -> popup.menu.getItem(i) }
                .first { m -> m.title.toString() == a.getString(R.string.clocky_my_designs_save_title) }
            popup.menu.performIdentifierAction(item.itemId, 0)
            typeNameAndConfirm("From Studio")
            val saved = repository.list().single()
            assertEquals("From Studio", saved.name)
            assertFalse(a.isFinishing)
            assertFalse(store.has(70))
        }
    }
}
