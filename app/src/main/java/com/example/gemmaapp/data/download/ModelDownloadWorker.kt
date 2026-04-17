package com.example.gemmaapp.data.download

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.gemmaapp.GemmaVoiceApp
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val checksumVerifier: ChecksumVerifier
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_EXPECTED_SHA256 = "expected_sha256"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_DOWNLOAD_URL)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing download URL"))
        val fileName = inputData.getString(KEY_FILE_NAME)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing file name"))
        val expectedSha256 = inputData.getString(KEY_EXPECTED_SHA256) ?: ""

        val modelsDir = applicationContext.getExternalFilesDir(null)
            ?.let { File(it, "models") }
            ?: return Result.failure(workDataOf(KEY_ERROR to "External storage unavailable"))
        modelsDir.mkdirs()

        val partFile = File(modelsDir, "$fileName.part")
        val finalFile = File(modelsDir, fileName)

        if (finalFile.exists()) return Result.success()

        setForeground(createForegroundInfo())

        return try {
            downloadFile(url, partFile, finalFile, expectedSha256)
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun downloadFile(
        url: String,
        partFile: File,
        finalFile: File,
        expectedSha256: String
    ): Result {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        val request = Request.Builder()
            .url(url)
            .apply { if (existingBytes > 0) header("Range", "bytes=$existingBytes-") }
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful && response.code != 206) {
            return Result.failure(workDataOf(KEY_ERROR to "HTTP ${response.code}"))
        }

        val body = response.body
            ?: return Result.failure(workDataOf(KEY_ERROR to "Empty response body"))

        val contentLength = body.contentLength()
        val totalBytes = if (existingBytes > 0 && contentLength > 0)
            existingBytes + contentLength else contentLength

        withContext(Dispatchers.IO) {
            FileOutputStream(partFile, existingBytes > 0).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var downloaded = existingBytes
                    var lastReportMs = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportMs > 500) {
                            val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                            setProgress(workDataOf(
                                KEY_PROGRESS to progress,
                                KEY_BYTES_DOWNLOADED to downloaded,
                                KEY_TOTAL_BYTES to totalBytes
                            ))
                            lastReportMs = now
                        }
                    }
                }
            }
        }

        // Signal verifying phase (progress = -1 is the sentinel)
        setProgress(workDataOf(KEY_PROGRESS to -1f))

        if (!checksumVerifier.verify(partFile, expectedSha256)) {
            partFile.delete()
            return Result.failure(workDataOf(KEY_ERROR to "Checksum mismatch — file corrupted, please retry"))
        }

        partFile.renameTo(finalFile)
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, GemmaVoiceApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Downloading Gemma 4 model")
            .setContentText("First-launch download — ~2.58 GB")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
