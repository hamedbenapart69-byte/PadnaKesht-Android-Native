package com.padnakasht.nativeapp.ui

import android.content.Context
import android.provider.Settings

/**
 * Honors the system "remove animations" / animator-duration-scale setting so decorative
 * motion is skipped for users who turned it off. Defaults to enabled if the setting is unreadable.
 */
fun animationsEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}.getOrDefault(true)
