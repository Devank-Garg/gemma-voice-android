package com.example.gemmaapp.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gemmaapp.data.model.DownloadState
import com.example.gemmaapp.data.model.GEMMA_4_E2B
import com.example.gemmaapp.ui.theme.BackgroundDark
import com.example.gemmaapp.ui.theme.BorderDark
import com.example.gemmaapp.ui.theme.BrandCyan
import com.example.gemmaapp.ui.theme.BrandPurple
import com.example.gemmaapp.ui.theme.BrandPurpleLight
import com.example.gemmaapp.ui.theme.CardDark
import com.example.gemmaapp.ui.theme.ErrorRed
import com.example.gemmaapp.ui.theme.SuccessGreen
import com.example.gemmaapp.ui.theme.SurfaceDark
import com.example.gemmaapp.ui.theme.TextMuted
import com.example.gemmaapp.ui.theme.TextPrimary
import com.example.gemmaapp.ui.theme.TextSecondary

private val gradientBrush = Brush.horizontalGradient(listOf(BrandPurple, BrandCyan))
private val bgBrush = Brush.verticalGradient(
    listOf(BackgroundDark, Color(0xFF0D1128), BackgroundDark)
)

@Composable
fun HomeScreen(
    onStartChat: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.downloadState.collectAsStateWithLifecycle()
    val locateError by viewModel.locateError.collectAsStateWithLifecycle()
    val modelReady = state is DownloadState.Complete
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onModelLocated(it, context.contentResolver) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            MicHero()

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Gemma Voice",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Your private AI voice assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Runs 100% on-device  ·  No cloud  ·  No data shared",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            ModelCard(
                state = state,
                locateError = locateError,
                onLocate = { filePicker.launch(arrayOf("*/*")) }
            )

            Spacer(Modifier.weight(1f))

            GradientButton(
                text = if (modelReady) "Start Conversation" else "Locate model to begin",
                enabled = modelReady,
                onClick = onStartChat
            )

            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun MicHero() {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandPurple.copy(alpha = 0.25f),
                            BrandCyan.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandPurple.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(BrandPurple.copy(0.5f), BrandCyan.copy(0.5f))),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(gradientBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun ModelCard(
    state: DownloadState,
    locateError: String?,
    onLocate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardDark)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(BorderDark, BrandPurple.copy(alpha = 0.3f), BorderDark)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "MODEL",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(12.dp))

            when (val s = state) {
                is DownloadState.Idle -> IdleModelRow(onLocate)
                is DownloadState.Downloading -> DownloadingModelRow(s)
                is DownloadState.Verifying -> VerifyingModelRow()
                is DownloadState.Complete -> ReadyModelRow()
                is DownloadState.Error -> IdleModelRow(onLocate)
            }

            if (locateError != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = locateError,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun IdleModelRow(onLocate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(GEMMA_4_E2B.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "INT4  ·  ~2.58 GB  ·  not found",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(gradientBrush)
                .clickable { onLocate() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Locate",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun DownloadingModelRow(s: DownloadState.Downloading) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(GEMMA_4_E2B.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "${(s.progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = BrandPurpleLight
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SurfaceDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(s.progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(gradientBrush)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (s.totalBytes > 0)
                "${formatBytes(s.bytesDownloaded)} of ${formatBytes(s.totalBytes)}"
            else "Connecting…",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun VerifyingModelRow() {
    Column {
        Text(GEMMA_4_E2B.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = BrandCyan,
            trackColor = SurfaceDark
        )
        Spacer(Modifier.height(8.dp))
        Text("Verifying integrity…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun ReadyModelRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(GEMMA_4_E2B.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "Ready  ·  INT4  ·  on-device",
                style = MaterialTheme.typography.bodySmall,
                color = SuccessGreen
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SuccessGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Ready",
                tint = SuccessGreen,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun GradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) gradientBrush
                else Brush.horizontalGradient(listOf(CardDark, CardDark))
            )
            .border(
                width = 1.dp,
                brush = if (enabled) gradientBrush
                        else Brush.horizontalGradient(listOf(BorderDark, BorderDark)),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.1f KB".format(bytes / 1024.0)
}
