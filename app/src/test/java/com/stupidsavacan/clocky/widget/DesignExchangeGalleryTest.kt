package com.stupidsavacan.clocky.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.android.deskclock.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.stupidsavacan.clocky.design.exchange.DesignExchange
import com.stupidsavacan.clocky.design.exchange.DesignSharing
import com.stupidsavacan.clocky.design.exchange.ImportOutcome
import com.stupidsavacan.clocky.design.exchange.TextCode
import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.WidgetInstance
import com.stupidsavacan.clocky.design.storage.DigitalDesignCodec
import com.stupidsavacan.clocky.design.storage.FileDesignRepository
import com.stupidsavacan.clocky.design.storage.SharedPreferencesDesignStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
import org.robolectric.shadows.ShadowToast
import java.io.File

/**
 * Phase 3C-2: import / export / share wired into the Gallery. Pickers and the share sheet are driven through
 * the real activity-result plumbing (Robolectric records the started intents and delivers results); nothing
 * leaves the process.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesignExchangeGalleryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val repository get() = FileDesignRepository(context)
    private val store get() = SharedPreferencesDesignStore(context)
    private val ioDir get() = File(context.filesDir, "test-io")

    @Before
    fun clean() {
        // FileProvider caches its path roots per authority in a static map, but Robolectric gives every test its
        // own data dir; drop the stale roots so each test resolves against its own cache dir.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { field ->
            (field.get(null) as MutableMap<*, *>).clear()
        }
        if (WINDOWS_HOST) {
            DesignSharing.uriFor = { _, file ->
                Uri.Builder().scheme("content").authority(DesignSharing.authority(context))
                    .appendPath(DesignSharing.SHARE_DIR).appendPath(file.name).build()
            }
        }
        File(context.filesDir, FileDesignRepository.DIR_NAME).deleteRecursively()
        File(context.cacheDir, DesignSharing.SHARE_DIR).deleteRecursively()
        ioDir.deleteRecursively()
        ioDir.mkdirs()
        context.getSharedPreferences("clocky_design_library", 0).edit().clear().commit()
        context.getSharedPreferences(SharedPreferencesDesignStore.PREFS_NAME, 0).edit().clear().commit()
    }

    @org.junit.After
    fun restoreUriFactory() {
        DesignSharing.uriFor = { c, f -> FileProvider.getUriForFile(c, DesignSharing.authority(c), f) }
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

    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

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
        idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun chooseItem(which: Int) {
        val d = dialog()
        d.listView.performItemClick(null, which, which.toLong())
        idle()
    }

    private fun findEdit(v: View): EditText? {
        if (v is EditText) return v
        if (v is ViewGroup) (0 until v.childCount).forEach { i -> findEdit(v.getChildAt(i))?.let { return it } }
        return null
    }

    private fun dialogMessage(d: AlertDialog): String =
        d.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()

    private fun fileUri(name: String, bytes: ByteArray? = null): Uri {
        val file = File(ioDir, name)
        if (bytes != null) file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun deliver(activity: Activity, started: org.robolectric.shadows.ShadowActivity.IntentForResult, uri: Uri?) {
        if (uri == null) {
            shadowOf(activity).receiveResult(started.intent, Activity.RESULT_CANCELED, null)
        } else {
            shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))
        }
        idle()
    }

    private fun importFileFlow(activity: Activity, uri: Uri?) {
        activity.button(R.id.clocky_gallery_import).performClick()
        chooseItem(0)
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE))
        deliver(activity, started, uri)
    }

    private fun importCodeFlow(activity: Activity, text: String) {
        activity.button(R.id.clocky_gallery_import).performClick()
        chooseItem(1)
        val d = dialog()
        findEdit(d.window!!.decorView)!!.setText(text)
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
    }

    private fun envelopeBytes(name: String, design: JSONObject) = DesignExchange.toFileBytes(name, design)

    private fun designJson(builtinId: String) = DigitalDesignCodec.encode(BuiltinDesigns.byId(builtinId)!!.instantiate())

    private fun widgetPrefs() = context.getSharedPreferences(SharedPreferencesDesignStore.PREFS_NAME, 0).all.toMap()

    // ---- manifest / provider ----

    @Test
    fun fileProviderHasItsOwnConstrainedNonExportedAuthority() {
        val authority = DesignSharing.authority(context)
        assertEquals("com.stupidsavacan.clocky.files", authority)
        val provider = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)!!
        assertEquals(FileProvider::class.java.name, provider.name)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        // The AOSP provider keeps the bare applicationId authority.
        val aosp = context.packageManager.resolveContentProvider("com.stupidsavacan.clocky", 0)!!
        assertTrue(aosp.name, aosp.name.endsWith("ClockProvider"))
        assertFalse(aosp.exported)
    }

    @Test
    fun providerPathsDeclareOnlyTheShareDirectory() {
        // Host-independent: the provider's declared roots are exactly one cache sub-directory.
        val info = context.packageManager.resolveContentProvider(DesignSharing.authority(context), PackageManager.GET_META_DATA)!!
        val parser = info.loadXmlMetaData(context.packageManager, "android.support.FILE_PROVIDER_PATHS")!!
        val roots = mutableListOf<String>()
        var event = parser.next()
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name != "paths") {
                roots += parser.name + ":" + parser.getAttributeValue(null, "path")
            }
            event = parser.next()
        }
        assertEquals(listOf("cache-path:shared_designs/"), roots)
    }

    @Test
    fun providerOnlyServesTheShareDirectory() {
        // androidx FileProvider's root matching rejects backslash paths, so this runs on POSIX hosts (CI, device).
        org.junit.Assume.assumeFalse(WINDOWS_HOST)
        val authority = DesignSharing.authority(context)
        val staged = DesignSharing.stageFile(context, "Only this", byteArrayOf(1))
        assertEquals(authority, staged.authority)
        assertEquals("content", staged.scheme)
        assertTrue(staged.path!!.endsWith("/Only this.clocky"))
        val outside = listOf(
            File(context.filesDir, "designs/x.json"),
            File(context.cacheDir, "other.txt"),
            File(context.cacheDir.parentFile, "databases/alarms.db"),
            File(context.filesDir, "../shared_prefs/clocky_widget_settings.xml"),
        )
        outside.forEach { file ->
            try {
                FileProvider.getUriForFile(context, authority, file)
                fail("served $file")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- sharing helpers ----

    @Test
    fun stagedFilesAreSafelyNamedAndStaleOnesPruned() {
        val dir = File(context.cacheDir, DesignSharing.SHARE_DIR)
        dir.mkdirs()
        val stale = File(dir, "old.clocky").also { it.writeText("x"); it.setLastModified(1_000L) }
        val fresh = File(dir, "fresh.clocky").also { it.writeText("x") }
        DesignSharing.stageFile(context, "../../evil", byteArrayOf(9))
        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        assertEquals(listOf("evil.clocky", "fresh.clocky"), dir.list()!!.sorted())
        assertTrue(File(dir, "evil.clocky").readBytes().contentEquals(byteArrayOf(9)))
        assertFalse(File(context.cacheDir, "evil.clocky").exists())
    }

    @Test
    fun shareIntentsCarryOnlyTheDesignAndAOneUriGrant() {
        val text = DesignSharing.textIntent("Name", "CLOCKY2:abc")
        assertEquals(Intent.ACTION_SEND, text.action)
        assertEquals("text/plain", text.type)
        assertEquals("CLOCKY2:abc", text.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(text.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(0, text.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertNull(text.component)
        assertNull(text.`package`)

        val uri = DesignSharing.stageFile(context, "Name", byteArrayOf(1))
        val file = DesignSharing.fileIntent(uri, "Name")
        assertEquals(Intent.ACTION_SEND, file.action)
        assertEquals(uri, file.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, file.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, file.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(uri, file.clipData!!.getItemAt(0).uri)

        val chooser = DesignSharing.chooser(file, "Share")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertNotNull(chooser.clipData)
    }

    // ---- UI presence ----

    @Test
    fun importIsAlwaysThereAndExportShareAppearWithASelection() {
        launch(70).use { c ->
            val a = c.get()
            assertEquals(View.VISIBLE, a.button(R.id.clocky_gallery_import).visibility)
            assertEquals(View.VISIBLE, a.button(R.id.clocky_gallery_export).visibility)
            assertEquals(View.VISIBLE, a.button(R.id.clocky_gallery_share).visibility)
            // Clocky Default is preselected on a fresh add, so the selection actions are on from the start.
        }
    }

    // ---- import: file ----

    @Test
    fun importingAFileCreatesANewMyDesignAndSelectsIt() {
        val source = designJson("bold-poster").also { it.getJSONObject("time").put("futureKey", "kept") }
        launch(71).use { c ->
            val a = c.get()
            importFileFlow(a, fileUri("poster.clocky", envelopeBytes("Poster ★", source)))
            val saved = repository.list().single()
            assertEquals("Poster ★", saved.name)
            assertFalse(saved.favorite)
            assertEquals("kept", repository.rawDesign(saved.id)!!.getJSONObject("time").getString("futureKey"))
            assertEquals(BuiltinDesigns.byId("bold-poster")!!.instantiate(), saved.design)
            // The card is shown under My Designs and selected.
            assertTrue(a.chip("mine").isChecked)
            assertEquals(1, a.visibleCards().size)
            assertTrue(ShadowToast.getTextOfLatestToast().contains("Poster ★"))
            // Nothing was written to any widget.
            assertFalse(store.has(71))
        }
    }

    @Test
    fun importNeverTouchesExistingWidgetsAndAlwaysMakesANewDesign() {
        store.save(WidgetInstance(5, BuiltinDesigns.byId("minimal-hairline")!!.instantiate()))
        store.save(WidgetInstance(23, BuiltinDesigns.byId("editorial-paper")!!.instantiate()))
        val before = widgetPrefs()
        val existing = repository.create("Existing", BuiltinDesigns.default.design)
        val existingRaw = repository.rawDesign(existing.id).toString()
        val bytes = envelopeBytes("Same", designJson("bold-block"))
        launch(72).use { c ->
            val a = c.get()
            importFileFlow(a, fileUri("a.clocky", bytes))
            importFileFlow(a, fileUri("b.clocky", bytes))
        }
        val all = repository.list()
        assertEquals(3, all.size)
        assertEquals(3, all.map { it.id }.toSet().size)
        assertEquals(2, all.count { it.name == "Same" })
        assertEquals(existingRaw, repository.rawDesign(existing.id).toString())
        assertEquals(existing.updatedAt, repository.get(existing.id)!!.updatedAt)
        assertEquals(before, widgetPrefs())
        assertFalse(store.has(72))
    }

    @Test
    fun cancellingThePickerChangesNothing() {
        launch(73).use { c ->
            val a = c.get()
            importFileFlow(a, null)
            assertTrue(repository.list().isEmpty())
            assertEquals(View.VISIBLE, a.button(R.id.clocky_gallery_add).visibility)
        }
    }

    @Test
    fun unreadableFileShowsAMessageAndSavesNothing() {
        launch(74).use { c ->
            val a = c.get()
            importFileFlow(a, Uri.fromFile(File(ioDir, "missing.clocky")))
            assertEquals(context.getString(R.string.clocky_exchange_open_failed), ShadowToast.getTextOfLatestToast())
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun corruptFileExplainsWhyAndSavesNothing() {
        launch(75).use { c ->
            val a = c.get()
            val full = envelopeBytes("X", designJson("bold-block"))
            importFileFlow(a, fileUri("cut.clocky", full.copyOf(full.size / 2)))
            val d = dialog()
            assertTrue(d.isShowing)
            assertEquals(context.getString(R.string.clocky_exchange_error_not_json), dialogMessage(d))
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun futureVersionFileIsRefusedWithUpdateAdvice() {
        val future = JSONObject(String(envelopeBytes("X", designJson("bold-block")))).put("formatVersion", 2)
        launch(76).use { c ->
            val a = c.get()
            importFileFlow(a, fileUri("future.clocky", future.toString().toByteArray()))
            assertEquals(context.getString(R.string.clocky_exchange_error_future_format), dialogMessage(dialog()))
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun oversizedFileIsRefusedWithoutBeingBuffered() {
        launch(77).use { c ->
            val a = c.get()
            importFileFlow(a, fileUri("huge.clocky", ByteArray(DesignExchange.MAX_FILE_BYTES + 5000) { ' '.code.toByte() }))
            assertEquals(context.getString(R.string.clocky_exchange_error_too_large), dialogMessage(dialog()))
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun unknownFontsAreReplacedAndDisclosedAfterImport() {
        val source = designJson("editorial-serif")
        source.getJSONObject("time").put("font", "from-the-future")
        launch(78).use { c ->
            val a = c.get()
            importFileFlow(a, fileUri("font.clocky", envelopeBytes("Fonty", source)))
            val saved = repository.list().single()
            assertEquals("system-sans", repository.rawDesign(saved.id)!!.getJSONObject("time").getString("font"))
            assertEquals("system-sans", saved.design.time.style.fontId)
            val d = dialog()
            assertEquals(context.getString(R.string.clocky_exchange_fonts_title), (d.window!!.decorView.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)).text.toString())
            assertTrue(dialogMessage(d).contains("from-the-future"))
        }
    }

    // ---- import: text code ----

    @Test
    fun pastedTextCodeImportsAsANewDesign() {
        val code = (DesignExchange.toTextCode("Coded", designJson("minimal-quiet-split")) as TextCode.Ready).code
        launch(79).use { c ->
            val a = c.get()
            importCodeFlow(a, "  $code \n")
            val saved = repository.list().single()
            assertEquals("Coded", saved.name)
            assertEquals(BuiltinDesigns.byId("minimal-quiet-split")!!.instantiate(), saved.design)
            assertFalse(store.has(79))
        }
    }

    @Test
    fun badPastedCodesShowAMessageAndSaveNothing() {
        val good = (DesignExchange.toTextCode("Coded", designJson("minimal-quiet-split")) as TextCode.Ready).code
        launch(80).use { c ->
            val a = c.get()
            importCodeFlow(a, "hello world")
            assertEquals(context.getString(R.string.clocky_exchange_error_wrong_prefix), dialogMessage(dialog()))
            importCodeFlow(a, good.dropLast(12))
            assertTrue(dialog().isShowing)
            importCodeFlow(a, good.take(30) + "!!" + good.drop(32))
            assertEquals(context.getString(R.string.clocky_exchange_error_bad_base64), dialogMessage(dialog()))
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun blankPasteKeepsTheDialogOpen() {
        launch(81).use { c ->
            val a = c.get()
            a.button(R.id.clocky_gallery_import).performClick()
            chooseItem(1)
            val d = dialog()
            findEdit(d.window!!.decorView)!!.setText("   ")
            d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(d.isShowing)
        }
    }

    @Test
    fun pasteButtonFillsTheFieldFromTheClipboard() {
        val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("code", "CLOCKY2:clip"))
        launch(82).use { c ->
            val a = c.get()
            a.button(R.id.clocky_gallery_import).performClick()
            chooseItem(1)
            val d = dialog()
            d.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            assertEquals("CLOCKY2:clip", findEdit(d.window!!.decorView)!!.text.toString())
            assertTrue(d.isShowing)
        }
    }

    // ---- export ----

    @Test
    fun exportUsesCreateDocumentAndWritesAReadableFile() {
        val saved = repository.createFromDocument("Mine", designJson("bold-poster").also { it.put("future", JSONObject().put("a", 1)) })
        launch(83).use { c ->
            val a = c.get()
            a.chip("mine").performClick()
            a.visibleCards().single().select()
            a.button(R.id.clocky_gallery_export).performClick()
            val started = shadowOf(a).nextStartedActivityForResult
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
            assertEquals(DesignSharing.MIME_FILE, started.intent.type)
            assertEquals("Mine.clocky", started.intent.getStringExtra(Intent.EXTRA_TITLE))
            val target = fileUri("out.clocky")
            deliver(a, started, target)
            val written = success(DesignExchange.parseFile(File(ioDir, "out.clocky").readBytes()))
            assertEquals("Mine", written.name)
            assertEquals(repository.rawDesign(saved.id).toString(), written.design.toString())
            assertEquals(1, written.design.getJSONObject("future").getInt("a"))
            assertEquals(context.getString(R.string.clocky_exchange_export_saved), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun exportOfABuiltinCarriesTheBuiltinDesign() {
        launch(84).use { c ->
            val a = c.get()
            a.cards().first().select()
            a.button(R.id.clocky_gallery_export).performClick()
            val started = shadowOf(a).nextStartedActivityForResult
            assertEquals(context.getString(R.string.clocky_design_clocky_default) + ".clocky", started.intent.getStringExtra(Intent.EXTRA_TITLE))
            deliver(a, started, fileUri("builtin.clocky"))
            val written = success(DesignExchange.parseFile(File(ioDir, "builtin.clocky").readBytes()))
            assertEquals(BuiltinDesigns.default.instantiate(), DigitalDesignCodec.decode(written.design))
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun cancelledExportWritesNothing() {
        launch(85).use { c ->
            val a = c.get()
            a.button(R.id.clocky_gallery_export).performClick()
            val started = shadowOf(a).nextStartedActivityForResult
            deliver(a, started, null)
            assertTrue(ioDir.list()!!.isEmpty())
        }
    }

    @Test
    fun failedExportWriteIsReported() {
        launch(86).use { c ->
            val a = c.get()
            a.button(R.id.clocky_gallery_export).performClick()
            val started = shadowOf(a).nextStartedActivityForResult
            deliver(a, started, Uri.fromFile(File(ioDir, "no-such-dir/out.clocky")))
            assertEquals(context.getString(R.string.clocky_exchange_export_failed), ShadowToast.getTextOfLatestToast())
        }
    }

    // ---- share ----

    @Test
    fun sharingAsTextCodeOpensTheChooserWithARoundTrippableCode() {
        val saved = repository.createFromDocument("Shared", designJson("editorial-paper"))
        launch(87).use { c ->
            val a = c.get()
            a.chip("mine").performClick()
            a.visibleCards().single().select()
            a.button(R.id.clocky_gallery_share).performClick()
            chooseItem(0)
            val chooser = shadowOf(a).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals("text/plain", send.type)
            val outcome = DesignExchange.parseTextCode(send.getStringExtra(Intent.EXTRA_TEXT)!!)
            assertEquals("Shared", success(outcome).name)
            assertEquals(repository.rawDesign(saved.id).toString(), success(outcome).design.toString())
            assertNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }
    }

    @Test
    fun sharingAsFileStagesItUnderTheProviderAndGrantsRead() {
        repository.createFromDocument("Filed", designJson("editorial-paper").also { it.put("keepMe", true) })
        launch(88).use { c ->
            val a = c.get()
            a.chip("mine").performClick()
            a.visibleCards().single().select()
            a.button(R.id.clocky_gallery_share).performClick()
            chooseItem(1)
            val chooser = shadowOf(a).nextStartedActivity
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals(DesignSharing.authority(context), uri.authority)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val staged = File(File(context.cacheDir, DesignSharing.SHARE_DIR), "Filed.clocky")
            assertTrue(staged.isFile)
            val imported = success(DesignExchange.parseFile(staged.readBytes()))
            assertEquals("Filed", imported.name)
            assertTrue(imported.design.getBoolean("keepMe"))
        }
    }

    @Test
    fun designTooBigForATextCodeGoesStraightToTheFileRoute() {
        val big = designJson("bold-block").put("blob", android.util.Base64.encodeToString(ByteArray(20_000).also { java.util.Random(5).nextBytes(it) }, android.util.Base64.NO_WRAP))
        repository.createFromDocument("Big", big)
        launch(89).use { c ->
            val a = c.get()
            a.chip("mine").performClick()
            a.visibleCards().single().select()
            a.button(R.id.clocky_gallery_share).performClick()
            // No chooser dialog: the file route is taken and explained.
            val chooser = shadowOf(a).nextStartedActivity
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertNull(send.getStringExtra(Intent.EXTRA_TEXT))
            val staged = File(File(context.cacheDir, DesignSharing.SHARE_DIR), "Big.clocky")
            assertEquals(big.getString("blob"), success(DesignExchange.parseFile(staged.readBytes())).design.getString("blob"))
            assertEquals(context.getString(R.string.clocky_exchange_share_too_large), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    fun sharingDoesNotChangeTheLibraryOrWidgets() {
        store.save(WidgetInstance(3, BuiltinDesigns.byId("bold-block")!!.instantiate()))
        val before = widgetPrefs()
        val saved = repository.create("Keep", BuiltinDesigns.default.design)
        val snapshot = repository.get(saved.id)
        launch(90).use { c ->
            val a = c.get()
            a.cards().first().select()
            a.button(R.id.clocky_gallery_share).performClick()
            chooseItem(0)
            a.button(R.id.clocky_gallery_share).performClick()
            chooseItem(1)
            a.button(R.id.clocky_gallery_export).performClick()
        }
        assertEquals(snapshot, repository.get(saved.id))
        assertEquals(1, repository.list().size)
        assertEquals(before, widgetPrefs())
    }

    // ---- full cycle, existing library actions ----

    @Test
    fun exportThenImportReproducesTheDesignAndLibraryActionsStillWork() {
        val saved = repository.createFromDocument("Cycle", designJson("minimal-hairline").also { it.getJSONObject("date").put("extra", listOf(1, 2).toString()) })
        launch(91).use { c ->
            val a = c.get()
            a.chip("mine").performClick()
            a.visibleCards().single().select()
            a.button(R.id.clocky_gallery_export).performClick()
            val started = shadowOf(a).nextStartedActivityForResult
            deliver(a, started, fileUri("cycle.clocky"))
            importFileFlow(a, fileUri("cycle.clocky"))
            val all = repository.list()
            assertEquals(2, all.size)
            val copy = all.first { it.id != saved.id }
            assertEquals(repository.rawDesign(saved.id).toString(), repository.rawDesign(copy.id).toString())
            // The imported copy is a normal My Design: favorite, rename, duplicate, delete all still apply.
            a.button(R.id.clocky_gallery_favorite).performClick()
            assertTrue(repository.get(copy.id)!!.favorite)
            a.button(R.id.clocky_gallery_duplicate).performClick()
            assertEquals(3, repository.list().size)
            assertFalse(store.has(91))
        }
    }

    @Test
    fun picker_intents_have_the_documented_shape() {
        val create = com.stupidsavacan.clocky.widget.easy.DesignExchangeController.CreateDesignDocument().createIntent(context, "x.clocky")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, create.action)
        assertEquals("x.clocky", create.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(DesignSharing.MIME_FILE, create.type)
        assertTrue(create.hasCategory(Intent.CATEGORY_OPENABLE))
        val open = com.stupidsavacan.clocky.widget.easy.DesignExchangeController.OpenDesignDocument().createIntent(context, arrayOf("*/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, open.action)
        assertEquals("*/*", open.type)
        assertTrue(open.hasCategory(Intent.CATEGORY_OPENABLE))
        assertNull(open.getParcelableExtra<Uri>(DocumentsContract.EXTRA_INITIAL_URI))
    }

    @Test
    fun noStoragePermissionsOrClockyViewFilterWereAdded() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().toSet()
        assertFalse(permissions.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://x/a.clocky"), DesignSharing.MIME_FILE)
            .setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(view, 0).isEmpty())
    }

    private fun success(outcome: ImportOutcome) = outcome as ImportOutcome.Success

    private companion object {
        val WINDOWS_HOST = System.getProperty("os.name").orEmpty().startsWith("Windows")
    }
}
