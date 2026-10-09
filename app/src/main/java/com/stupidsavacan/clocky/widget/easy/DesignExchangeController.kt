package com.stupidsavacan.clocky.widget.easy

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.exchange.DesignExchange
import com.stupidsavacan.clocky.design.exchange.DesignSharing
import com.stupidsavacan.clocky.design.exchange.ImportError
import com.stupidsavacan.clocky.design.exchange.ImportOutcome
import com.stupidsavacan.clocky.design.exchange.TextCode
import com.stupidsavacan.clocky.design.storage.DesignRepository
import com.stupidsavacan.clocky.design.storage.SavedDesign
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** What a Gallery card exports: its display name and the schema-2 design document. */
class ExportSource(val name: String, val design: JSONObject)

/**
 * Import, export and share for the Gallery (Phase 3C-2). It owns the SAF launchers (no storage permission:
 * `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`), the share sheet and the import dialogs.
 *
 * Imports only ever call [DesignRepository.createFromDocument] (a new My Design); widget settings are never
 * read or written here. Construct during `onCreate`, before the activity is started, so the launchers register.
 */
class DesignExchangeController(
    private val activity: ComponentActivity,
    private val repository: DesignRepository,
    private val onImported: (SavedDesign) -> Unit,
    registry: ActivityResultRegistry = activity.activityResultRegistry,
) {
    private var pendingExportKey: String? = null

    private val createDocument = activity.registerForActivityResult(CreateDesignDocument(), registry) { uri -> onExportTarget(uri) }

    private val openDocument = activity.registerForActivityResult(OpenDesignDocument(), registry) { uri -> onImportSource(uri) }

    /** SAF create-document with the OPENABLE category the platform documents as required. */
    class CreateDesignDocument : ActivityResultContracts.CreateDocument(DesignSharing.MIME_FILE) {
        override fun createIntent(context: Context, input: String): android.content.Intent =
            super.createIntent(context, input).addCategory(android.content.Intent.CATEGORY_OPENABLE)
    }

    /** SAF open-document restricted to openable files (the library's contract does not add the category). */
    class OpenDesignDocument : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): android.content.Intent =
            super.createIntent(context, input).addCategory(android.content.Intent.CATEGORY_OPENABLE)
    }

    /** Resolves a Gallery card key to what it exports; set by the host. */
    var sourceFor: (key: String) -> ExportSource? = { null }

    fun saveState(out: Bundle) {
        pendingExportKey?.let { out.putString(STATE_EXPORT_KEY, it) }
    }

    fun restoreState(state: Bundle) {
        pendingExportKey = state.getString(STATE_EXPORT_KEY)
    }

    // ---- export ----

    fun exportToFile(key: String) {
        val source = sourceFor(key) ?: return toast(R.string.clocky_exchange_export_failed)
        pendingExportKey = key
        launch { createDocument.launch(DesignExchange.fileName(source.name)) }
    }

    private fun onExportTarget(uri: Uri?) {
        val key = pendingExportKey
        pendingExportKey = null
        if (uri == null) return // the picker was cancelled
        val source = key?.let(sourceFor) ?: return toast(R.string.clocky_exchange_export_failed)
        val written = runCatching {
            activity.contentResolver.openOutputStream(uri, "wt")!!.use {
                it.write(DesignExchange.toFileBytes(source.name, source.design))
            }
        }
        toast(if (written.isSuccess) R.string.clocky_exchange_export_saved else R.string.clocky_exchange_export_failed)
    }

    // ---- share ----

    /** Offers text code or file; a design too big for a text code goes straight to the file route. */
    fun share(key: String) {
        val source = sourceFor(key) ?: return toast(R.string.clocky_exchange_share_failed)
        if (DesignExchange.toTextCode(source.name, source.design) is TextCode.TooLarge) {
            toast(R.string.clocky_exchange_share_too_large)
            return shareAsFile(source)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.clocky_exchange_share_title)
            .setItems(
                arrayOf(
                    activity.getString(R.string.clocky_exchange_share_as_code),
                    activity.getString(R.string.clocky_exchange_share_as_file),
                ),
            ) { _, which -> if (which == 0) shareAsText(source) else shareAsFile(source) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun shareAsText(source: ExportSource) {
        val code = DesignExchange.toTextCode(source.name, source.design)
        if (code !is TextCode.Ready) return shareAsFile(source)
        launch { activity.startActivity(chooser(DesignSharing.textIntent(source.name, code.code))) }
    }

    fun shareAsFile(source: ExportSource) {
        val sent = runCatching {
            val uri = DesignSharing.stageFile(activity, source.name, DesignExchange.toFileBytes(source.name, source.design))
            launch { activity.startActivity(chooser(DesignSharing.fileIntent(uri, source.name))) }
        }
        if (sent.isFailure) toast(R.string.clocky_exchange_share_failed)
    }

    private fun chooser(send: android.content.Intent) =
        DesignSharing.chooser(send, activity.getString(R.string.clocky_exchange_share_chooser))

    // ---- import ----

    fun showImportChoices() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.clocky_exchange_import_title)
            .setItems(
                arrayOf(
                    activity.getString(R.string.clocky_exchange_import_from_file),
                    activity.getString(R.string.clocky_exchange_import_from_code),
                ),
            ) { _, which -> if (which == 0) importFromFile() else importFromCode() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun importFromFile() = launch { openDocument.launch(arrayOf("*/*")) }

    private fun onImportSource(uri: Uri?) {
        if (uri == null) return
        val bytes = runCatching { activity.contentResolver.openInputStream(uri)!!.use { readBounded(it) } }.getOrNull()
        if (bytes == null) {
            toast(R.string.clocky_exchange_open_failed)
            return
        }
        handle(DesignExchange.parseFile(bytes))
    }

    /** Reads at most MAX_FILE_BYTES + 1 so an oversized or endless source is refused without being buffered. */
    private fun readBounded(input: InputStream): ByteArray {
        val limit = DesignExchange.MAX_FILE_BYTES + 1
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun importFromCode() {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 3
            maxLines = 8
            hint = activity.getString(R.string.clocky_exchange_code_hint)
            contentDescription = activity.getString(R.string.clocky_exchange_code_title)
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val frame = FrameLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.clocky_exchange_code_title)
            .setView(frame)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.clocky_exchange_code_paste, null)
            .setPositiveButton(R.string.clocky_exchange_code_import, null)
            .create()
        dialog.setOnShowListener {
            // Paste must not dismiss the dialog, so both buttons are wired after show().
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clip = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.let { input.setText(it) }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString()
                if (text.isNotBlank()) {
                    dialog.dismiss()
                    handle(DesignExchange.parseTextCode(text))
                }
            }
        }
        dialog.show()
    }

    /** Saves a valid import as a new My Design, discloses font replacements, or explains the refusal. */
    fun handle(outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.Failure -> showError(outcome.reason)
            is ImportOutcome.Success -> {
                val name = outcome.name ?: activity.getString(R.string.clocky_exchange_imported_default_name)
                val saved = runCatching { repository.createFromDocument(name, outcome.design) }.getOrNull()
                if (saved == null) {
                    toast(R.string.clocky_exchange_save_failed)
                    return
                }
                toast(activity.getString(R.string.clocky_exchange_imported, saved.name))
                onImported(saved)
                if (outcome.unknownFonts.isNotEmpty()) {
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.clocky_exchange_fonts_title)
                        .setMessage(activity.getString(R.string.clocky_exchange_fonts_message, outcome.unknownFonts.joinToString(", ")))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun showError(reason: ImportError) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.clocky_exchange_error_title)
            .setMessage(errorMessage(reason))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun launch(action: () -> Unit) {
        try {
            action()
        } catch (_: ActivityNotFoundException) {
            // No document picker / share target on this device.
            toast(R.string.clocky_exchange_open_failed)
        }
    }

    private fun toast(res: Int) = Toast.makeText(activity, res, Toast.LENGTH_SHORT).show()
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    companion object {
        private const val STATE_EXPORT_KEY = "exchange.exportKey"

        fun errorMessage(reason: ImportError): Int = when (reason) {
            ImportError.EMPTY -> R.string.clocky_exchange_error_empty
            ImportError.WRONG_PREFIX -> R.string.clocky_exchange_error_wrong_prefix
            ImportError.BAD_BASE64 -> R.string.clocky_exchange_error_bad_base64
            ImportError.TRUNCATED -> R.string.clocky_exchange_error_truncated
            ImportError.BAD_DEFLATE -> R.string.clocky_exchange_error_bad_deflate
            ImportError.TOO_LARGE -> R.string.clocky_exchange_error_too_large
            ImportError.NOT_JSON -> R.string.clocky_exchange_error_not_json
            ImportError.WRONG_FORMAT -> R.string.clocky_exchange_error_wrong_format
            ImportError.FUTURE_FORMAT_VERSION -> R.string.clocky_exchange_error_future_format
            ImportError.FUTURE_SCHEMA -> R.string.clocky_exchange_error_future_schema
            ImportError.INVALID_DESIGN -> R.string.clocky_exchange_error_invalid_design
        }
    }
}
