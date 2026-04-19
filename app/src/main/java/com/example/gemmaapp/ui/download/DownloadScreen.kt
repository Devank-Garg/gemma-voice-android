package com.example.gemmaapp.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.example.gemmaapp.data.model.GEMMA_4_E2B
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gemmaapp.data.model.DownloadState

@Composable
fun DownloadScreen(
    onDownloadComplete: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val state by viewModel.downloadState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is DownloadState.Complete) onDownloadComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Download Model", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "${GEMMA_4_E2B.name}  ·  ~${formatBytes(GEMMA_4_E2B.sizeBytes)}  ·  one-time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        when (val s = state) {
            is DownloadState.Idle -> {
                Text(
                    "The model runs entirely on your device.\nWi-Fi recommended.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is DownloadState.Downloading -> {
                Text(
                    "${(s.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (s.totalBytes > 0) {
                    Text(
                        "${formatBytes(s.bytesDownloaded)} / ${formatBytes(s.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is DownloadState.Verifying -> {
                Text("Verifying download…", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            is DownloadState.Complete -> {
                Text("Ready!", style = MaterialTheme.typography.headlineSmall)
            }

            is DownloadState.Error -> {
                Text(
                    "Download failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.1f KB".format(bytes / 1024.0)
}
