package com.example.gemmaapp.data.model

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState()
    object Verifying : DownloadState()
    object Complete : DownloadState()
    data class Error(val message: String) : DownloadState()
}
