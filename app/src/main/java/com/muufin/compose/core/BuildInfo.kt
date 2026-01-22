package com.muufin.compose.core

import android.content.Context
import com.muufin.compose.BuildConfig

object BuildInfo {
    fun appVersion(context: Context): String = BuildConfig.VERSION_NAME
}
