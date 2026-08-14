/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.deskclock.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow

import androidx.preference.DropDownPreference
import androidx.preference.PreferenceViewHolder

import com.android.deskclock.R
import com.android.deskclock.ThemeUtils

internal class SimpleMenuPreference @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int
) : DropDownPreference(context!!, attrs, defStyleAttr, defStyleRes) {
    private lateinit var mAdapter: SimpleMenuAdapter

    constructor(context: Context?) : this(context, null) {
    }

    constructor(context: Context?, attrs: AttributeSet?) :
            this(context, attrs, androidx.preference.R.attr.dropdownPreferenceStyle) {
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) :
            this(context, attrs, defStyle, 0) {
    }

    override fun createAdapter(): ArrayAdapter<CharSequence?> {
        mAdapter = SimpleMenuAdapter(getContext(), R.layout.simple_menu_dropdown_item)
        return mAdapter
    }

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        super.onBindViewHolder(view)
        val view = view.itemView
        view.setOnClickListener {
            val popup = ListPopupWindow(getContext())
            popup.anchorView = view
            popup.setAdapter(mAdapter)
            popup.setDropDownGravity(android.view.Gravity.END)
            popup.setOnItemClickListener { _, _, position, _ ->
                val value = entryValues[position].toString()
                if (callChangeListener(value)) {
                    setValue(value)
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private class SimpleMenuAdapter(context: Context, resource: Int) :
            ArrayAdapter<CharSequence?>(context, resource) {
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val textColor = ThemeUtils.resolveColor(context, android.R.attr.textColorPrimary)
            if (view is android.widget.TextView) {
                view.setTextColor(textColor)
            }
            return view
        }
    }
}