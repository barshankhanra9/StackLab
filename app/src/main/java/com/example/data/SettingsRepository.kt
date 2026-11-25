package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.stackLabDataStore: DataStore<Preferences> by preferencesDataStore(name = "stacklab_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MAX_RESOLUTION = stringPreferencesKey("max_resolution")
        val OUTPUT_FORMAT = stringPreferencesKey("output_format")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
    }

    val outputSettings: Flow<OutputSettings> = context.stackLabDataStore.data.map { prefs ->
        val resName = prefs[Keys.MAX_RESOLUTION]
        val formatName = prefs[Keys.OUTPUT_FORMAT]
        val quality = prefs[Keys.JPEG_QUALITY] ?: 95

        OutputSettings(
            maxResolution = MaxResolutionPreset.entries.firstOrNull { it.name == resName }
                ?: MaxResolutionPreset.UHD_4K,
            outputFormat = OutputFormat.entries.firstOrNull { it.name == formatName }
                ?: OutputFormat.JPEG,
            jpegQuality = quality.coerceIn(70, 100)
        )
    }

    suspend fun updateMaxResolution(preset: MaxResolutionPreset) {
        context.stackLabDataStore.edit { prefs ->
            prefs[Keys.MAX_RESOLUTION] = preset.name
        }
    }

    suspend fun updateOutputFormat(format: OutputFormat) {
        context.stackLabDataStore.edit { prefs ->
            prefs[Keys.OUTPUT_FORMAT] = format.name
        }
    }

    suspend fun updateJpegQuality(quality: Int) {
        context.stackLabDataStore.edit { prefs ->
            prefs[Keys.JPEG_QUALITY] = quality.coerceIn(70, 100)
        }
    }
}
