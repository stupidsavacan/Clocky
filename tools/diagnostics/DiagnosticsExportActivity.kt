package com.stupidsavacan.clocky.diagnostics

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsExportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticsTrace.install(applicationContext)
        DiagnosticsTrace.event("diagnostics.screen.opened")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        content.addView(TextView(this).apply {
            text = "Clocky Diagnostics TEMP\n\nAfter reproducing the widget problem, use the button below to save the locally recorded diagnostic text to a file you choose."
            textSize = 17f
        })
        content.addView(Button(this).apply {
            text = "Save diagnostics"
            setOnClickListener { chooseDestination() }
        })
        content.addView(Button(this).apply {
            text = "Clear diagnostics"
            setOnClickListener {
                DiagnosticsTrace.clear()
                Toast.makeText(this@DiagnosticsExportActivity, "Cleared", Toast.LENGTH_SHORT).show()
            }
        })
        setContentView(content)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SAVE || resultCode != RESULT_OK) return
        val destination = data?.data ?: return
        val bytes = DiagnosticsTrace.snapshot().toByteArray(Charsets.UTF_8)
        contentResolver.openOutputStream(destination, "w")?.use { it.write(bytes) }
        DiagnosticsTrace.event("diagnostics.saved", mapOf("bytes" to bytes.size))
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun chooseDestination() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "clocky-diagnostics-$stamp.txt")
            },
            REQUEST_SAVE,
        )
    }

    companion object {
        private const val REQUEST_SAVE = 6001
    }
}
