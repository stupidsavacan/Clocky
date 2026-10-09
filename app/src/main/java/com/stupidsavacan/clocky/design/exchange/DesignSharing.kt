package com.stupidsavacan.clocky.design.exchange

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android share sheet for a design, as a text code or a `.clocky` file (Phase 3C-2). Files go through
 * [FileProvider] under `${applicationId}.files`, which only exposes `cache/shared_designs/` (see
 * `res/xml/clocky_file_paths.xml`); the AOSP `${applicationId}` authority (ClockProvider) is untouched.
 * Receivers get a one-shot read grant on that single URI, no storage permission is involved, and nothing is
 * sent anywhere but the app the user picks in the chooser.
 */
object DesignSharing {
    const val AUTHORITY_SUFFIX = ".files"
    const val SHARE_DIR = "shared_designs"
    const val MIME_FILE = "application/octet-stream"
    const val MIME_TEXT = "text/plain"
    private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000

    /** `${applicationId}.files`, exactly as the manifest placeholder resolves (the namespace is not the applicationId). */
    @Suppress("UNUSED_PARAMETER")
    fun authority(context: Context) = com.android.deskclock.BuildConfig.APPLICATION_ID + AUTHORITY_SUFFIX

    fun textIntent(name: String, code: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType(MIME_TEXT)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .putExtra(Intent.EXTRA_TEXT, code)

    /** Writes [bytes] to the share directory and returns the provider URI for it. */
    fun stageFile(context: Context, name: String, bytes: ByteArray, now: Long = System.currentTimeMillis()): Uri {
        val dir = File(context.cacheDir, SHARE_DIR)
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create ${dir.path}" }
        // Earlier shares are kept for a day so a slow receiving app can still read them.
        dir.listFiles()?.filter { it.isFile && now - it.lastModified() > STALE_AFTER_MS }?.forEach { it.delete() }
        val file = File(dir, DesignExchange.fileName(name))
        file.writeBytes(bytes)
        return uriFor(context, file)
    }

    /**
     * Maps a staged file to its provider URI. A seam only for JVM tests on Windows hosts, where androidx
     * FileProvider's root matching rejects backslash paths (Android itself is POSIX).
     */
    internal var uriFor: (Context, File) -> Uri = { context, file -> FileProvider.getUriForFile(context, authority(context), file) }

    fun fileIntent(uri: Uri, name: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType(MIME_FILE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .apply {
                clipData = ClipData.newRawUri(name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

    fun chooser(send: Intent, title: CharSequence): Intent =
        Intent.createChooser(send, title).also {
            // Carry the grant on the chooser itself so the picked target inherits it.
            if (send.clipData != null) {
                it.clipData = send.clipData
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
}
