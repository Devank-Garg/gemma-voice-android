package com.example.gemmaapp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_MODEL_PATH = stringPreferencesKey("model_path")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val modelPath: Flow<String?> = context.dataStore.data.map { it[KEY_MODEL_PATH] }

    val isOnboardingDone: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun setModelPath(path: String) {
        context.dataStore.edit { it[KEY_MODEL_PATH] = path }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
    }
}
