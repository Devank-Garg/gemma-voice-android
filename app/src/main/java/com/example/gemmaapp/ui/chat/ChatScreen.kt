package com.example.gemmaapp.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gemmaapp.data.model.ChatMessage
import com.example.gemmaapp.ui.theme.BackgroundDark
import com.example.gemmaapp.ui.theme.BrandCyan
import com.example.gemmaapp.ui.theme.BrandPurple
import com.example.gemmaapp.ui.theme.CardDark
import com.example.gemmaapp.ui.theme.ErrorRed
import com.example.gemmaapp.ui.theme.SuccessGreen
import com.example.gemmaapp.ui.theme.TextMuted
import com.example.gemmaapp.ui.theme.TextSecondary
import androidx.compose.runtime.withFrameMillis

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private val gradientBrush get() = Brush.horizontalGradient(listOf(BrandPurple, BrandCyan))
private val Mono = FontFamily.Monospace

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startVoiceCapture() }

    // Auto-scroll to bottom when messages change
    LaunchedEffect(uiState.messages.size, uiState.voiceState) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        AmbientGlow()

        Column(modifier = Modifier.fillMaxSize()) {
            ChatAppBar(
                engineState = uiState.engineState,
                onBack = onBack,
                onClear = viewModel::clearConversation,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (uiState.messages.isEmpty()) {
                    item { EmptyState() }
                } else {
                    // Group messages by date and insert dividers
                    val grouped = uiState.messages.groupByDate()
                    grouped.forEach { (date, msgs) ->
                        item(key = "divider_$date") { DateDivider(label = date) }
                        items(msgs, key = { it.id }) { msg ->
                            if (msg.role == ChatMessage.Role.USER) {
                                UserBubble(message = msg)
                            } else {
                                AssistantBubble(message = msg)
                            }
                        }
                    }
                    if (uiState.voiceState == VoiceState.PROCESSING && uiState.messages.last().role == ChatMessage.Role.USER) {
                        item { ThinkingIndicator() }
                    }
                }
            }

            BottomBar(
                uiState = uiState,
                onInput = viewModel::updateInput,
                onSend = viewModel::sendTextMessage,
                onToggleKeyboard = viewModel::toggleKeyboardMode,
                onMicClick = {
                    if (uiState.voiceState == VoiceState.LISTENING) {
                        viewModel.stopVoiceCapture()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) viewModel.startVoiceCapture()
                        else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )

            // Home gesture indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(BackgroundDark),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 108.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
            }
        }

        // Engine loading overlay
        if (uiState.engineState is ChatViewModel.EngineState.Loading) {
            EngineLoadingOverlay()
        }
    }
}

// ─── Ambient glow blobs ──────────────────────────────────────

@Composable
private fun AmbientGlow() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-60).dp, y = (-100).dp)
                .background(
                    Brush.radialGradient(listOf(BrandPurple.copy(alpha = 0.18f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = 20.dp)
                .background(
                    Brush.radialGradient(listOf(BrandCyan.copy(alpha = 0.11f), Color.Transparent)),
                    CircleShape
                )
        )
    }
}

// ─── App bar ─────────────────────────────────────────────────

@Composable
private fun ChatAppBar(
    engineState: ChatViewModel.EngineState,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.04f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White.copy(alpha = 0.9f),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Gemma Voice",
                style = TextStyle(
                    brush = gradientBrush,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val dotColor = when (engineState) {
                    is ChatViewModel.EngineState.Ready -> SuccessGreen
                    is ChatViewModel.EngineState.Loading -> BrandCyan
                    is ChatViewModel.EngineState.Error -> ErrorRed
                    else -> TextMuted
                }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(dotColor, CircleShape)
                )
                val label = when (engineState) {
                    is ChatViewModel.EngineState.Loading -> "LOADING MODEL…"
                    is ChatViewModel.EngineState.Ready -> "ON-DEVICE · GEMMA 4 E2B"
                    is ChatViewModel.EngineState.Error -> "ENGINE ERROR"
                    else -> "MODEL NOT LOADED"
                }
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontFamily = Mono,
                    letterSpacing = 0.4.sp,
                )
            }
        }

        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

// ─── Message bubbles ─────────────────────────────────────────

@Composable
private fun UserBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(gradientBrush)
                .padding(horizontal = 15.dp, vertical = 11.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTime(message.timestampMs),
                fontSize = 10.sp, color = TextMuted, fontFamily = Mono,
            )
            Text("·", fontSize = 10.sp, color = TextMuted)
            Text(
                text = "voice",
                fontSize = 10.sp, color = SuccessGreen, fontFamily = Mono,
            )
        }
    }
}

@Composable
private fun AssistantBubble(message: ChatMessage) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursor_alpha",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        // Gemma avatar label
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(gradientBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", fontSize = 7.sp, color = Color.White)
            }
            Text(
                text = "GEMMA",
                fontSize = 11.sp,
                color = TextSecondary,
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        }

        // Bubble
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(Color.White.copy(alpha = 0.035f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row {
                Text(
                    text = message.text,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )
                if (message.isStreaming) {
                    Spacer(Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 8.dp, height = 16.dp)
                            .align(Alignment.Bottom)
                            .background(BrandCyan.copy(alpha = cursorAlpha))
                    )
                }
            }
        }

        // Metadata
        if (!message.isStreaming && message.tokenCount > 0) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatTime(message.timestampMs), fontSize = 10.sp, color = TextMuted, fontFamily = Mono)
                Text("·", fontSize = 10.sp, color = TextMuted)
                Text("${message.tokenCount} tok", fontSize = 10.sp, color = TextMuted, fontFamily = Mono)
                Text("·", fontSize = 10.sp, color = TextMuted)
                Text(
                    text = "${"%.1f".format(message.tokensPerSecond)} tok/s",
                    fontSize = 10.sp, color = BrandCyan, fontFamily = Mono,
                )
            }
        }
    }
}

@Composable
private fun DateDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(40.dp, 1.dp).background(Color.White.copy(alpha = 0.08f)))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp, color = TextMuted, fontFamily = Mono,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.size(40.dp, 1.dp).background(Color.White.copy(alpha = 0.08f)))
    }
}

@Composable
private fun ThinkingIndicator() {
    var frameTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { withFrameMillis { t -> frameTime = t } }
    }

    Row(
        modifier = Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val pulseScale = (sin(frameTime / 700.0) * 0.15 + 1.0).toFloat()
        Box(
            modifier = Modifier
                .size((18 * pulseScale).dp)
                .background(gradientBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", fontSize = 7.sp, color = Color.White)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(Color.White.copy(alpha = 0.035f))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.07f),
                    RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            ThinkingDots(frameTime = frameTime)
        }
    }
}

@Composable
private fun ThinkingDots(frameTime: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val period = 1200L
            val delay = i * 150L
            val t = ((frameTime - delay).mod(period.toInt()) + period) % period
            val phase = t.toFloat() / period
            val yOffset: Dp = when {
                phase < 0.4f -> (-4f * (phase / 0.4f)).dp
                phase < 0.8f -> (-4f * (1f - (phase - 0.4f) / 0.4f)).dp
                else -> 0.dp
            }
            val alpha = if (phase in 0.3f..0.5f) 1f else 0.6f
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = yOffset)
                    .background(Color.White.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "idle_pulse")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow",
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(gradientBrush.let {
                    Brush.radialGradient(
                        listOf(BrandPurple.copy(alpha = glowAlpha), BrandCyan.copy(alpha = glowAlpha * 0.6f))
                    )
                }, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", fontSize = 28.sp, color = Color.White)
        }
        Text(
            text = "Start Speaking",
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
        )
        Text(
            text = "Gemma is ready and listening",
            fontSize = 14.sp, color = TextSecondary,
        )
    }
}

// ─── Bottom bar (voice + keyboard) ───────────────────────────

@Composable
private fun BottomBar(
    uiState: ChatViewModel.UiState,
    onInput: (String) -> Unit,
    onSend: (String) -> Unit,
    onToggleKeyboard: () -> Unit,
    onMicClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, BackgroundDark.copy(alpha = 0.95f))
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.isKeyboardMode) {
            // Text input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(32.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleKeyboard, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Switch to voice",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Type a message…", color = TextMuted, fontSize = 14.sp)
                    },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = BrandCyan,
                    ),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend(uiState.inputText) }),
                )
                IconButton(
                    onClick = { onSend(uiState.inputText) },
                    enabled = uiState.inputText.isNotBlank() && uiState.engineState is ChatViewModel.EngineState.Ready,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (uiState.inputText.isNotBlank()) gradientBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                            CircleShape
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (uiState.inputText.isNotBlank()) Color.White else TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            // Voice bar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(32.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
                    .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Keyboard toggle
                IconButton(
                    onClick = onToggleKeyboard,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Switch to text",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Waveform
                WaveformBars(
                    modifier = Modifier.weight(1f),
                    active = uiState.voiceState == VoiceState.LISTENING || uiState.voiceState == VoiceState.SPEAKING,
                )

                // Mic / stop button
                MicButton(voiceState = uiState.voiceState, onClick = onMicClick)
            }

            // Status label
            val statusLabel = when (uiState.voiceState) {
                VoiceState.LISTENING -> "LISTENING…"
                VoiceState.PROCESSING -> "THINKING ON-DEVICE…"
                VoiceState.SPEAKING -> "SPEAKING"
                VoiceState.ERROR -> "ERROR — TAP TO RETRY"
                else -> "TAP TO SPEAK"
            }
            Text(
                text = statusLabel,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 11.sp,
                color = if (uiState.voiceState == VoiceState.IDLE) TextMuted else Color.White,
                fontFamily = Mono,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

@Composable
private fun WaveformBars(modifier: Modifier = Modifier, active: Boolean) {
    var frameTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (active) {
            while (true) { withFrameMillis { t -> frameTime = t } }
        }
    }

    Row(
        modifier = modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(32) { i ->
            val phase = i * 0.18f
            val height: Dp = if (active) {
                val raw = sin(frameTime / 1100.0 * 2 * PI + phase).toFloat()
                ((raw * 0.5f + 0.5f) * 24f + 4f).dp
            } else 4.dp

            val barColor = if (active) {
                val hue = 260f + (i / 32f) * 60f
                Color.hsl(hue, 0.85f, 0.62f)
            } else Color.White.copy(alpha = 0.15f)

            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(height)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun MicButton(voiceState: VoiceState, onClick: () -> Unit) {
    val active = voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING

    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing), RepeatMode.Restart
        ),
        label = "ripple_scale",
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "ripple_alpha",
    )

    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Ripple rings when listening
        if (voiceState == VoiceState.LISTENING) {
            val ringSize = (56 * rippleScale).dp
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .drawBehind {
                        drawCircle(
                            color = BrandPurple.copy(alpha = rippleAlpha),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
            )
        }

        val bgBrush = if (active || voiceState == VoiceState.PROCESSING) {
            gradientBrush
        } else {
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(bgBrush, CircleShape),
        ) {
            var frameTime by remember { mutableLongStateOf(0L) }
            if (voiceState == VoiceState.PROCESSING) {
                LaunchedEffect(Unit) {
                    while (true) { withFrameMillis { t -> frameTime = t } }
                }
                ThinkingDots(frameTime = frameTime)
            } else {
                Icon(
                    imageVector = if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.LISTENING)
                        Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

// ─── Engine loading overlay ───────────────────────────────────

@Composable
private fun EngineLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = BrandPurple,
                trackColor = BrandCyan.copy(alpha = 0.2f),
            )
            Text(
                text = "Loading Gemma 4 E2B…",
                fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White,
            )
            Text(
                text = "This takes 5–10 s on first run",
                fontSize = 12.sp, color = TextSecondary,
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────

private fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
private fun List<ChatMessage>.groupByDate(): Map<String, List<ChatMessage>> =
    groupBy { dateFormat.format(Date(it.timestampMs)) }
