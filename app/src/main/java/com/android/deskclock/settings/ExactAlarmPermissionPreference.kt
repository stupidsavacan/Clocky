/*
 * Copyright (C) 2026 Clocky contributors
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
 * limitations under the License.
 */

package com.android.deskclock.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import androidx.preference.Preference

/**
 * Opens Android's exact-alarm special-access screen on Android 12 and 12L.
 *
 * Android 13+ uses USE_EXACT_ALARM because exact alarm delivery is core to Clocky's alarm/timer
 * functionality. Older Android releases do not expose the special exact-alarm access screen.
 */
class ExactAlarmPermissionPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    init {
        isPersistent = false
        isVisible = isRelevantPlatform()
    }

    override fun onAttached() {
        super.onAttached()
        isVisible = isRelevantPlatform()
    }

    override fun onClick() {
        if (!isRelevantPlatform()) {
            return
        }

        val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Some vendor builds may omit the per-app special-access activity. In that case the
            // scheduling layer continues to use its existing best-effort fallback instead.
        }
    }

    private fun isRelevantPlatform(): Boolean {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.S ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2
    }
}
