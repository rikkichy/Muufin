package cat.ri.muufin.core

import android.content.Context
import cat.ri.muufin.BuildConfig

object BuildInfo {
    fun appVersion(context: Context): String = BuildConfig.VERSION_NAME
}
