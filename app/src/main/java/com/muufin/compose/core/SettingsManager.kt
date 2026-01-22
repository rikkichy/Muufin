package com.muufin.compose.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


object SettingsManager {
    private const val STORE_NAME = "muufin_settings"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val KEY_PREFER_LOSSLESS_DIRECT = booleanPreferencesKey("prefer_lossless_direct_play")
    private val KEY_ENABLE_PLAYBACK_REPORTING = booleanPreferencesKey("enable_playback_reporting")

    private lateinit var store: DataStore<Preferences>

    private val _preferLosslessDirectPlay = MutableStateFlow(false)
    val preferLosslessDirectPlay: StateFlow<Boolean> = _preferLosslessDirectPlay.asStateFlow()

    
    private val _enablePlaybackReporting = MutableStateFlow(true)
    val enablePlaybackReporting: StateFlow<Boolean> = _enablePlaybackReporting.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext

        store = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { appContext.preferencesDataStoreFile(STORE_NAME) },
        )

        scope.launch {
            store.data
                .map { it[KEY_PREFER_LOSSLESS_DIRECT] ?: false }
                .collect { _preferLosslessDirectPlay.value = it }
        }

        scope.launch {
            store.data
                .map { it[KEY_ENABLE_PLAYBACK_REPORTING] ?: true }
                .collect { _enablePlaybackReporting.value = it }
        }
    }

    suspend fun setPreferLosslessDirectPlay(enabled: Boolean) {
        store.edit { it[KEY_PREFER_LOSSLESS_DIRECT] = enabled }
    }

    suspend fun setEnablePlaybackReporting(enabled: Boolean) {
        store.edit { it[KEY_ENABLE_PLAYBACK_REPORTING] = enabled }
    }
}
