package com.muufin.compose.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
    private val KEY_DEFAULT_LIBRARY_TAB = intPreferencesKey("default_library_tab")
    private val KEY_LIBRARY_LAYOUT = intPreferencesKey("library_layout")

    private lateinit var store: DataStore<Preferences>

    private val _preferLosslessDirectPlay = MutableStateFlow(false)
    val preferLosslessDirectPlay: StateFlow<Boolean> = _preferLosslessDirectPlay.asStateFlow()

    
    private val _enablePlaybackReporting = MutableStateFlow(true)
    val enablePlaybackReporting: StateFlow<Boolean> = _enablePlaybackReporting.asStateFlow()

    private val _defaultLibraryTab = MutableStateFlow(0)
    val defaultLibraryTab: StateFlow<Int> = _defaultLibraryTab.asStateFlow()

    private val _libraryLayout = MutableStateFlow(0)
    val libraryLayout: StateFlow<Int> = _libraryLayout.asStateFlow()

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

        scope.launch {
            store.data
                .map { it[KEY_DEFAULT_LIBRARY_TAB] ?: 0 }
                .collect { _defaultLibraryTab.value = it.coerceIn(0, 2) }
        }

        scope.launch {
            store.data
                .map { it[KEY_LIBRARY_LAYOUT] ?: 0 }
                .collect { _libraryLayout.value = it.coerceIn(0, 1) }
        }
    }

    suspend fun setPreferLosslessDirectPlay(enabled: Boolean) {
        store.edit { it[KEY_PREFER_LOSSLESS_DIRECT] = enabled }
    }

    suspend fun setEnablePlaybackReporting(enabled: Boolean) {
        store.edit { it[KEY_ENABLE_PLAYBACK_REPORTING] = enabled }
    }

    suspend fun setDefaultLibraryTab(tab: Int) {
        store.edit { it[KEY_DEFAULT_LIBRARY_TAB] = tab.coerceIn(0, 2) }
    }

    suspend fun setLibraryLayout(layout: Int) {
        store.edit { it[KEY_LIBRARY_LAYOUT] = layout.coerceIn(0, 1) }
    }
}
