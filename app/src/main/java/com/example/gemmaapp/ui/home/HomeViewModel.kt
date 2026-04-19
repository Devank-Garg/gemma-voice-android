package com.example.gemmaapp.ui.home

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemmaapp.data.model.DownloadState
import com.example.gemmaapp.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    val downloadState: StateFlow<DownloadState> = modelRepository.observeDownloadState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = if (modelRepository.isModelDownloaded())
                DownloadState.Complete else DownloadState.Idle
        )

    private val _locateError = MutableStateFlow<String?>(null)
    val locateError: StateFlow<String?> = _locateError.asStateFlow()

    fun onModelLocated(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            val path = resolveUriToPath(contentResolver, uri)
            if (path != null && File(path).exists()) {
                modelRepository.saveModelPath(path)
                _locateError.value = null
            } else {
                _locateError.value = "Could not read file path directly.\n" +
                    "Use ADB instead:\nadb push <model> " +
                    "/sdcard/Android/data/com.example.gemmaapp/files/models/"
            }
        }
    }

    fun clearLocateError() { _locateError.value = null }

    private fun resolveUriToPath(contentResolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        if (uri.authority == "com.android.externalstorage.documents") {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size < 2) return null
            val storageType = parts[0]
            val relativePath = parts[1]
            return if (storageType.equals("primary", ignoreCase = true)) {
                "${Environment.getExternalStorageDirectory()}/$relativePath"
            } else {
                "/storage/$storageType/$relativePath"
            }
        }

        // Fallback: query DATA column (works for Downloads and MediaStore URIs)
        try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val path = cursor.getString(0)
                        if (!path.isNullOrEmpty()) return path
                    }
                }
        } catch (_: Exception) {}

        return null
    }
}
