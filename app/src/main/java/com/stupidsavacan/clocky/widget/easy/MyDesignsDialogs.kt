package com.stupidsavacan.clocky.widget.easy

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.storage.DesignRepository
import com.stupidsavacan.clocky.design.storage.FileDesignRepository

/** The small dialogs My Designs needs: name entry, delete confirmation and "save to My Designs". */
object MyDesignsDialogs {
    fun askName(context: Context, @StringRes title: Int, initial: String, onName: (String) -> Unit) {
        val input = EditText(context).apply {
            setText(initial)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(FileDesignRepository.MAX_NAME_LENGTH))
            contentDescription = context.getString(R.string.clocky_my_designs_name_hint)
            hint = context.getString(R.string.clocky_my_designs_name_hint)
        }
        val pad = (20 * context.resources.displayMetrics.density).toInt()
        val frame = FrameLayout(context).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(frame)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            // Validate before dismissing so a blank name keeps the dialog open.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString()
                if (name.isBlank()) {
                    input.error = context.getString(R.string.clocky_my_designs_name_blank)
                } else {
                    dialog.dismiss()
                    onName(name)
                }
            }
        }
        dialog.show()
    }

    fun confirmDelete(context: Context, name: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(R.string.clocky_my_designs_delete_title)
            .setMessage(context.getString(R.string.clocky_my_designs_delete_message, name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clocky_my_designs_delete_confirm) { _, _ -> onConfirm() }
            .show()
    }

    /** Asks for a name, then saves a snapshot of [design]. Placed widgets are not touched. */
    fun saveDesign(context: Context, repository: DesignRepository, design: DigitalDesign, onSaved: () -> Unit = {}) {
        askName(context, R.string.clocky_my_designs_save_title, DesignLabels.designName(context, design)) { name ->
            val saved = runCatching { repository.create(name, design) }
            Toast.makeText(
                context,
                if (saved.isSuccess) R.string.clocky_my_designs_saved else R.string.clocky_my_designs_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
            if (saved.isSuccess) onSaved()
        }
    }
}
