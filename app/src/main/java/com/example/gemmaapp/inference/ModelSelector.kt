package com.example.gemmaapp.inference

import android.app.ActivityManager
import android.content.Context
import com.example.gemmaapp.data.model.GEMMA_4_E2B
import com.example.gemmaapp.data.model.ModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelSelector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun select(): ModelInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamGb = info.totalMem / (1024.0 * 1024 * 1024)
        if (totalRamGb < 6.0) {
            // show low-RAM warning in Sprint 3; still attempt E2B
        }
        return GEMMA_4_E2B
    }
}
