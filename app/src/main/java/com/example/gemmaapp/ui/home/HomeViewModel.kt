package com.example.gemmaapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemmaapp.data.model.DownloadState
import com.example.gemmaapp.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

    fun startDownload() = modelRepository.startDownload()
}
