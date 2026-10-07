package com.stupidsavacan.clocky.widget.studio

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.android.alarmclock.DigitalAppWidgetProvider
import com.android.deskclock.R
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.BackgroundElement
import com.stupidsavacan.clocky.design.model.BackgroundType
import com.stupidsavacan.clocky.design.model.ColorRef
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.InfoSource
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import com.stupidsavacan.clocky.studio.Slot
import com.stupidsavacan.clocky.widget.digital.DigitalWidgetFit
import com.stupidsavacan.clocky.widget.digital.HostFontCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Studio through its real views: slot chips, panels, numeric entry, undo/redo, draft semantics and
 * recreation. Robolectric resolves the real resources and theme; launcher behavior is not covered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class StudioActivityTest {
    private val app = RuntimeEnvironment.getApplication()
    private val store = SharedPreferencesDesignStore(app)

    /** Robolectric has no launcher; model a host that renders bundled fonts unless a test says otherwise. */
    @Before fun capableHost() {
        HostFontCapability.probeOverride = { HostFontCapability.Support(bundledFonts = true, latinAmPmMarker = true) }
    }

    @After fun resetHost() {
        HostFontCapability.probeOverride = null
    }

    private fun widget(): Int = shadowOf(AppWidgetManager.getInstance(app))
        .createWidget(DigitalAppWidgetProvider::class.java, R.layout.clocky_digital_widget)

    private fun intent(id: Int?, draft: DigitalDesign? = null, offerGallery: Boolean = false) =
        Intent(app, StudioActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .apply {
                if (id != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                draft?.let { putExtra(StudioActivity.EXTRA_DRAFT_JSON, DigitalDesignCodec.encode(it).toString()) }
                putExtra(StudioActivity.EXTRA_OFFER_GALLERY, offerGallery)
            }

    private fun open(id: Int, draft: DigitalDesign? = null): ActivityController<StudioActivity> =
        Robolectric.buildActivity(StudioActivity::class.java, intent(id, draft)).setup().also { idle() }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun StudioActivity.panel(): ViewGroup = findViewById(R.id.clocky_studio_panel)

    private fun <T : View> ViewGroup.findAll(type: Class<T>): List<T> = (0 until childCount).flatMap { i ->
        val child = getChildAt(i)
        val self = if (type.isInstance(child)) listOf(type.cast(child)!!) else emptyList()
        self + ((child as? ViewGroup)?.findAll(type) ?: emptyList())
    }

    private fun ViewGroup.texts(): List<String> = findAll(TextView::class.java).map { it.text.toString() }

    private fun StudioActivity.tab(slot: Slot): Chip =
        (findViewById<ViewGroup>(R.id.clocky_studio_tabs).findAll(Chip::class.java))[slot.ordinal]

    private fun StudioActivity.selectTab(slot: Slot) {
        tab(slot).performClick()
        idle()
    }

    private fun StudioActivity.chip(text: String): Chip =
        panel().findAll(Chip::class.java).firstOrNull { it.text.toString() == text }
            ?: error("no chip '$text' in ${panel().findAll(Chip::class.java).map { it.text }}")

    private fun StudioActivity.click(text: String) {
        chip(text).performClick()
        idle()
    }

    private fun StudioActivity.save() {
        findViewById<Button>(R.id.clocky_studio_save).performClick()
    }

    private fun StudioActivity.advanced() {
        findViewById<View>(R.id.clocky_studio_mode_advanced).performClick()
        idle()
    }

    private fun StudioActivity.timeFaceId(): Int {
        val frame = findViewById<FrameLayout>(R.id.clocky_preview_frame)
        return DigitalWidgetFit.visibleTextIn(frame, R.id.clocky_time_slot)!!.id
    }

    /** Opens the numeric entry of the slider row whose label is [label] and commits [value]. */
    private fun StudioActivity.type(label: String, value: String) {
        val valueView = panel().findAll(TextView::class.java).first { it.contentDescription?.startsWith("$label,") == true }
        valueView.performClick()
        idle() // the dialog's show listener (which wires the OK button) is dispatched through the looper
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog
        val input = dialog.window!!.decorView.let { (it as ViewGroup).findAll(TextInputEditText::class.java).first() }
        input.setText(value)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
    }

    private fun resultOf(activity: Activity) = shadowOf(activity).resultCode

    // ---- lifecycle and result contract ----

    @Test
    fun inflatesSixSlotChipsAndStartsCanceled() {
        open(widget()).use { controller ->
            val activity = controller.get()
            val chips = activity.findViewById<ViewGroup>(R.id.clocky_studio_tabs).findAll(Chip::class.java)
            assertEquals(Slot.entries.size, chips.size)
            assertEquals(Activity.RESULT_CANCELED, resultOf(activity))
            assertFalse(activity.isFinishing)
        }
    }

    @Test
    fun missingWidgetIdFinishesCanceled() {
        Robolectric.buildActivity(StudioActivity::class.java, intent(null)).setup().use { controller ->
            assertTrue(controller.get().isFinishing)
            assertEquals(Activity.RESULT_CANCELED, resultOf(controller.get()))
        }
    }

    @Test
    fun backWithoutEditsLeavesImmediatelyAndWritesNothing() {
        val id = widget()
        open(id).use { controller ->
            val activity = controller.get()
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, resultOf(activity))
            assertFalse("a cancelled fresh add must leave no settings", store.has(id))
        }
    }

    @Test
    fun backWithEditsAsksAndKeepEditingStays() {
        val id = widget()
        store.save(WidgetInstance(id, DigitalDesign()))
        open(id).use { controller ->
            val activity = controller.get()
            activity.type("Size", "90")
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            val dialog = (org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog)
            assertTrue(dialog.isShowing)
            assertFalse(activity.isFinishing)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            assertFalse(activity.isFinishing)
            assertEquals("the saved widget is untouched", DigitalDesign().time.style.sizeSp, store.load(id).design.time.style.sizeSp, 0f)
        }
    }

    @Test
    fun discardLeavesCanceledAndTheSavedWidgetUnchanged() {
        val id = widget()
        store.save(WidgetInstance(id, DigitalDesign()))
        open(id).use { controller ->
            val activity = controller.get()
            activity.type("Size", "90")
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            (org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idle()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_CANCELED, resultOf(activity))
            assertEquals(64f, store.load(id).design.time.style.sizeSp, 0f)
        }
    }

    @Test
    fun saveWritesTheDraftAndReturnsOkForTheWidget() {
        val id = widget()
        open(id).use { controller ->
            val activity = controller.get()
            activity.type("Size", "90")
            activity.save()
            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_OK, resultOf(activity))
            assertEquals(id, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
            assertEquals(90f, store.load(id).design.time.style.sizeSp, 0f)
        }
    }

    // ---- preview is the production path ----

    @Test
    fun previewIsTheWidgetsRemoteViewsAndFollowsEdits() {
        open(widget()).use { controller ->
            val activity = controller.get()
            assertEquals(R.id.clocky_face_w400, activity.timeFaceId())
            activity.type("Weight", "700")
            assertEquals(R.id.clocky_face_w700, activity.timeFaceId())
        }
    }

    // ---- numeric entry ----

    @Test
    fun typedValuesAreClampedToTheRangeAndNonNumbersAreRejected() {
        open(widget()).use { controller ->
            val activity = controller.get()
            activity.type("Size", "9999")
            activity.save()
            assertEquals(256f, store.load(activity.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)).design.time.style.sizeSp, 0f)
        }
        open(widget()).use { controller ->
            val activity = controller.get()
            val valueView = activity.panel().findAll(TextView::class.java).first { it.contentDescription?.startsWith("Size,") == true }
            valueView.performClick()
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog
            val input = (dialog.window!!.decorView as ViewGroup).findAll(TextInputEditText::class.java).first()
            input.setText("abc")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue("the dialog stays open on invalid input", dialog.isShowing)
        }
    }

    // ---- undo / redo / reset ----

    @Test
    fun undoAndRedoRestoreTheDraftAndTheButtonsReflectAvailability() {
        open(widget()).use { controller ->
            val activity = controller.get()
            val undo = activity.findViewById<View>(R.id.clocky_studio_undo)
            val redo = activity.findViewById<View>(R.id.clocky_studio_redo)
            assertFalse(undo.isEnabled)
            assertFalse(redo.isEnabled)

            activity.type("Weight", "700")
            assertTrue(undo.isEnabled)
            undo.performClick()
            idle()
            assertEquals(R.id.clocky_face_w400, activity.timeFaceId())
            assertTrue(redo.isEnabled)
            redo.performClick()
            idle()
            assertEquals(R.id.clocky_face_w700, activity.timeFaceId())
        }
    }

    @Test
    fun resetSlotRestoresItAndClearsTheChangedMarker() {
        val design = BuiltinDesigns.default.instantiate()
        val id = widget()
        store.save(WidgetInstance(id, design))
        open(id).use { controller ->
            val activity = controller.get()
            val name = activity.getString(R.string.clocky_element_time)
            assertFalse(activity.tab(Slot.TIME).text.toString().contains("•"))
            activity.type("Size", "100")
            assertTrue("the Time tab is marked as changed", activity.tab(Slot.TIME).text.toString().contains("•"))

            val reset = activity.panel().findAll(Button::class.java).last { it.text.contains(name) }
            assertTrue(reset.isEnabled)
            reset.performClick()
            idle()
            assertFalse(activity.tab(Slot.TIME).text.toString().contains("•"))
            activity.save()
            val tokens = DesignEditsDefaults.sizeOfDefaultTime()
            assertEquals(tokens, store.load(id).design.time.style.sizeSp, 0.01f)
        }
    }

    private object DesignEditsDefaults {
        fun sizeOfDefaultTime(): Float = BuiltinDesigns.default.instantiate().time.style.sizeSp
    }

    // ---- basic / advanced, tabs, scope ----

    @Test
    fun advancedShowsMoreControlsThanBasic() {
        open(widget()).use { controller ->
            val activity = controller.get()
            val basic = activity.panel().findAll(Slider::class.java).size
            activity.advanced()
            assertTrue("advanced adds precision controls", activity.panel().findAll(Slider::class.java).size > basic)
        }
    }

    @Test
    fun everyInteractiveControlInEveryPanelIsAtLeast48dp() {
        val density = app.resources.displayMetrics.density
        val min = (48 * density).toInt() - 1
        open(widget()).use { controller ->
            val activity = controller.get()
            for (advanced in listOf(false, true)) {
                if (advanced) activity.advanced()
                for (slot in Slot.entries) {
                    activity.selectTab(slot)
                    val tooSmall = activity.panel().findAll(View::class.java).filter { v ->
                        (v is Chip || v is Button || v is Slider || (v is TextView && v.isClickable)) && v.isShown && v.height in 1 until min
                    }
                    assertTrue("$slot advanced=$advanced has small targets: ${tooSmall.map { it.javaClass.simpleName }}", tooSmall.isEmpty())
                }
            }
        }
    }

    @Test
    fun thisSizeOnlyWritesAnOverrideForTheSelectedSizeClassOnly() {
        val id = widget()
        open(id).use { controller ->
            val activity = controller.get()
            activity.findViewById<View>(R.id.clocky_preview_strip).performClick()
            idle()
            activity.findViewById<View>(R.id.clocky_studio_scope).performClick()
            idle()
            activity.type("Size", "30")
            activity.save()
            val saved = store.load(id).design
            assertEquals(64f, saved.time.style.sizeSp, 0f)
            assertEquals(30f, saved.layout.patchFor(SizeClass.STRIP).timeSizeSp)
            assertNull(saved.layout.patchFor(SizeClass.CARD).timeSizeSp)
        }
    }

    // ---- slots ----

    @Test
    fun infoSlotAddsTheRowToThePreviewAndSaysWhenItIsASample() {
        open(widget()).use { controller ->
            val activity = controller.get()
            activity.selectTab(Slot.INFO)
            activity.click(activity.getString(R.string.clocky_studio_info_alarm))
            val frame = activity.findViewById<FrameLayout>(R.id.clocky_preview_frame)
            idle()
            assertEquals(1, frame.findViewById<ViewGroup>(R.id.clocky_info_slot).childCount)
            assertTrue(activity.panel().texts().any { it == activity.getString(R.string.clocky_studio_info_alarm_sample) })
        }
    }

    @Test
    fun backgroundTypesShowRenderedGradientInThePreview() {
        open(widget()).use { controller ->
            val activity = controller.get()
            activity.selectTab(Slot.BACKGROUND)
            activity.advanced()
            activity.click(activity.getString(R.string.clocky_studio_bg_gradient))
            idle()
            val bg = activity.findViewById<FrameLayout>(R.id.clocky_preview_frame).findViewById<ImageView>(R.id.clocky_widget_background)
            assertEquals(View.VISIBLE, bg.visibility)
            assertNotNull((bg.drawable as android.graphics.drawable.BitmapDrawable).bitmap)
        }
    }

    @Test
    fun lowContrastAgainstTheDesignsOwnCardIsWarnedAndOneTapFixes() {
        val id = widget()
        val poor = DigitalDesign(
            time = TimeElement(TextStyle(sizeSp = 64f, color = ColorRef.Fixed(0x888888))),
            background = BackgroundElement(type = BackgroundType.SOLID, color = ColorRef.Fixed(0x999999), opacity = 1f),
        )
        store.save(WidgetInstance(id, poor))
        open(id).use { controller ->
            val activity = controller.get()
            val checks = activity.findViewById<ViewGroup>(R.id.clocky_studio_checks)
            val fix = checks.findAll(Button::class.java).firstOrNull { it.text.startsWith("Fix") }
            assertNotNull("a Fix action is offered", fix)
            fix!!.performClick()
            idle()
            assertTrue(
                "after the fix there is no known-poor warning left",
                checks.findAll(Button::class.java).none { it.text.startsWith("Fix") && it.text.contains("Time") },
            )
        }
    }

    @Test
    fun weightApproximationOfABundledFontIsDisclosed() {
        val id = widget()
        open(id).use { controller ->
            val activity = controller.get()
            activity.click("Poppins")
            activity.type("Weight", "900")
            val text = activity.findViewById<ViewGroup>(R.id.clocky_studio_checks).texts() + activity.panel().texts()
            assertTrue("Poppins has no 900 face: ${text}", text.any { it.contains("900") && it.contains("700") })
        }
    }

    @Test
    fun unsupportedHostShowsTheSystemFontKeepsTheRequestAndDisclosesItOnce() {
        HostFontCapability.probeOverride = { HostFontCapability.Support.NONE }
        open(widget()).use { controller ->
            val activity = controller.get()
            activity.click("Poppins")
            activity.selectTab(Slot.DATE)
            activity.click("Poppins")
            val notes = activity.findViewById<ViewGroup>(R.id.clocky_studio_checks).texts()
            val host = notes.filter { it.contains("Poppins") && it.contains("system font") }
            assertEquals("one line for Time and Date together: $notes", 1, host.size)
            assertEquals("clocky-poppins", activity.design.time.style.fontId)
            assertEquals("clocky-poppins", activity.design.date.style.fontId)
            assertFalse("not the API-level message: $notes", notes.any { it.contains("Android 8") })
        }
    }

    @Test
    @Config(sdk = [23])
    fun bundledFontBelowApi26IsDisclosedAndStillRendersTheSystemFont() {
        open(widget()).use { controller ->
            val activity = controller.get()
            activity.click("Poppins")
            val notes = activity.findViewById<ViewGroup>(R.id.clocky_studio_checks).texts()
            assertTrue("needs-Android-8 notice shown: $notes", notes.any { it.contains("Android 8") })
            assertEquals("the draft still requests the bundled font", "clocky-poppins", activity.design.time.style.fontId)
            assertEquals(R.id.clocky_face_w400, activity.timeFaceId())
        }
    }

    // ---- accessibility / locale ----

    @Test
    @Config(sdk = [34], qualifiers = "ar-ldrtl")
    fun rtlInflatesEverySlotWithRtlLayoutAndStillSavesRequestedValues() {
        val id = widget()
        open(id).use { controller ->
            val activity = controller.get()
            assertEquals(View.LAYOUT_DIRECTION_RTL, activity.resources.configuration.layoutDirection)
            for (slot in Slot.entries) activity.selectTab(slot)
            activity.selectTab(Slot.TIME)
            activity.type("Size", "70")
            activity.save()
            assertEquals(70f, store.load(id).design.time.style.sizeSp, 0f)
        }
    }

    @Test
    @Config(sdk = [34])
    fun twoHundredPercentFontScaleKeepsControlsAtLeast48dpAndReachable() {
        RuntimeEnvironment.setFontScale(2.0f)
        val density = app.resources.displayMetrics.density
        val min = (48 * density).toInt() - 1
        open(widget()).use { controller ->
            val activity = controller.get()
            activity.advanced()
            for (slot in Slot.entries) {
                activity.selectTab(slot)
                val small = activity.panel().findAll(View::class.java).filter { v ->
                    (v is Chip || v is Button || v is Slider) && v.isShown && v.height in 1 until min
                }
                assertTrue("$slot at 200% font scale has small targets", small.isEmpty())
                assertTrue("$slot panel still has its reset action", activity.panel().findAll(Button::class.java).isNotEmpty())
            }
        }
    }

    // ---- recreation ----

    @Test
    fun configurationChangeKeepsTheDraftAndTheUndoHistory() {
        open(widget()).use { controller ->
            val activity = controller.get()
            activity.selectTab(Slot.DATE)
            activity.type("Size", "30")
            controller.recreate()
            idle()
            val recreated = controller.get()
            assertEquals("the active tab survives", Slot.DATE.ordinal, recreated.findViewById<ViewGroup>(R.id.clocky_studio_tabs)
                .findAll(Chip::class.java).indexOfFirst { it.isChecked })
            assertTrue("undo history survives a configuration change", recreated.findViewById<View>(R.id.clocky_studio_undo).isEnabled)
            recreated.findViewById<View>(R.id.clocky_studio_undo).performClick()
            idle()
            assertEquals(14f, recreated.design.date.style.sizeSp, 0f)
        }
    }

    @Test
    fun processDeathRestoresTheDraftFromSavedStateWithoutHistory() {
        val id = widget()
        store.save(WidgetInstance(id, DigitalDesign()))
        val state = Bundle()
        Robolectric.buildActivity(StudioActivity::class.java, intent(id)).setup().use { controller ->
            val activity = controller.get()
            activity.type("Size", "88")
            controller.saveInstanceState(state)
        }
        // A new process: no ViewModel, no draft extra; everything comes from the bundle.
        Robolectric.buildActivity(StudioActivity::class.java, intent(id)).create(state).start().resume().visible().use { controller ->
            idle()
            val activity = controller.get()
            assertEquals(88f, activity.design.time.style.sizeSp, 0f)
            assertFalse(activity.findViewById<View>(R.id.clocky_studio_undo).isEnabled)
            activity.type("Size", "88.0")
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            assertTrue("still dirty relative to the saved widget, so it asks", (org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog).isShowing)
        }
    }

    // ---- hosts ----

    @Test
    fun draftExtraIsEditedInsteadOfTheStoredDesign() {
        val id = widget()
        store.save(WidgetInstance(id, DigitalDesign()))
        val draft = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 111f)))
        open(id, draft).use { controller ->
            assertEquals(111f, controller.get().design.time.style.sizeSp, 0f)
            assertEquals("nothing is written until Save", 64f, store.load(id).design.time.style.sizeSp, 0f)
        }
    }

    @Test
    fun browseDesignsIsOfferedOnlyWhenTheHostAsks() {
        val id = widget()
        Robolectric.buildActivity(StudioActivity::class.java, intent(id, offerGallery = true)).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<View>(R.id.clocky_studio_more).performClick()
            val menu = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu()
            val titles = (0 until menu.menu.size()).map { menu.menu.getItem(it).title.toString() }
            assertTrue(titles.contains(activity.getString(R.string.clocky_browse_designs)))
        }
    }

    @Test
    fun quickTuneTokensSurviveAStudioSaveSoMaterialYouStillWorks() {
        val id = widget()
        val design = BuiltinDesigns.default.instantiate()
        open(id, design).use { controller ->
            val activity = controller.get()
            activity.type("Size", "70")
            activity.save()
            val saved = store.load(id).design
            assertNotNull("palette tokens are kept", saved.style)
            assertEquals(design.style!!.palette, saved.style!!.palette)
            assertEquals(InfoSource.NONE, saved.info.source)
        }
    }
}
