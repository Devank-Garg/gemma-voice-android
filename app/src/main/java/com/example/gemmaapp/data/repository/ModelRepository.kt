package com.example.gemmaapp.data.repository

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.gemmaapp.data.download.ModelDownloadWorker
import com.example.gemmaapp.data.model.DownloadState
import com.example.gemmaapp.data.model.GEMMA_4_E2B
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val WORK_NAME = "model_download"
    }

    fun observeDownloadState(): Flow<DownloadState> =
        settingsRepository.modelPath.flatMapLatest { savedPath ->
            if (savedPath != null && File(savedPath).exists()) {
                flowOf(DownloadState.Complete)
            } else {
                workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { workInfoList ->
                    val info = workInfoList.firstOrNull()
                    if (info == null) {
                        return@map if (isModelDownloaded()) DownloadState.Complete else DownloadState.Idle
                    }
                    when (info.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                            DownloadState.Downloading(0f, 0L, GEMMA_4_E2B.sizeBytes)
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f)
                            val downloaded = info.progress.getLong(ModelDownloadWorker.KEY_BYTES_DOWNLOADED, 0L)
                            val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, GEMMA_4_E2B.sizeBytes)
                            if (progress < 0f) DownloadState.Verifying
                            else DownloadState.Downloading(progress, downloaded, total)
                        }
                        WorkInfo.State.SUCCEEDED -> DownloadState.Complete
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            val error = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed"
                            DownloadState.Error(error)
                        }
                    }
                }
            }
        }

    fun isModelDownloaded(): Boolean {
        val modelsDir = context.getExternalFilesDir(null)?.let { File(it, "models") }
        return modelsDir?.let { File(it, GEMMA_4_E2B.fileName).exists() } ?: false
    }

    suspend fun getModelPath(): String? {
        val savedPath = settingsRepository.modelPath.first()
        if (savedPath != null && File(savedPath).exists()) return savedPath

        val modelsDir = context.getExternalFilesDir(null)?.let { File(it, "models") }
        val file = modelsDir?.let { File(it, GEMMA_4_E2B.fileName) }
        return if (file?.exists() == true) file.absolutePath else null
    }

    suspend fun saveModelPath(path: String) = settingsRepository.setModelPath(path)
}
