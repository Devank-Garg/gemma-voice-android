package com.example.gemmaapp.ui.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemmaapp.ui.chat.VoiceState
import com.example.gemmaapp.ui.theme.BackgroundDark
import com.example.gemmaapp.ui.theme.BrandCyan
import com.example.gemmaapp.ui.theme.BrandPurple
import com.example.gemmaapp.ui.theme.CardDark
import com.example.gemmaapp.ui.theme.TextMuted
import com.example.gemmaapp.ui.theme.TextPrimary
import com.example.gemmaapp.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceModeScreen(
    voiceState: VoiceState,
    engineReady: Boolean,
    onOrbTap: () -> Unit,
    onEndSession: () -> Unit,
    onToggleKeyboard: () -> Unit,
) {
    val isSpeaking = voiceState == VoiceState.SPEAKING
    val isListening = voiceState == VoiceState.LISTENING || voiceState == VoiceState.RECORDING
    val isProcessing = voiceState == VoiceState.PROCESSING
    val isIdle = voiceState == VoiceState.IDLE || voiceState == VoiceState.ERROR

    val inf = rememberInfiniteTransition(label = "voice")

    // Halo glow pulse (all states)
    val glowAlpha by inf.animateFloat(
        0.5f, 0.95f,
        infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    // Core scale — breathe when active, slow pulse when idle
    val coreScale by inf.animateFloat(
        1f, if (isListening || isSpeaking) 1.07f else 1.045f,
        infiniteRepeatable(
            tween(if (isListening || isSpeaking) 2600 else 4000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "coreScale"
    )

    // Spinning arc rotations
    val idleArcRot   by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000,  easing = LinearEasing)), label = "idleArc")
    val listenArcRot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3600,  easing = LinearEasing)), label = "listenArc")
    val procOuter    by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2100,  easing = LinearEasing)), label = "procOuter")
    val procInner    by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(1500,  easing = LinearEasing)), label = "procInner")
    val p1Angle      by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2400,  easing = LinearEasing)), label = "p1")
    val p2Angle      by inf.animateFloat(0f, -360f,infiniteRepeatable(tween(1900,  easing = LinearEasing)), label = "p2")

    // Ripple rings for SPEAKING — staggered with Animatable + delay
    val ripple1 = remember { Animatable(0f) }
    val ripple2 = remember { Animatable(0f) }
    val ripple3 = remember { Animatable(0f) }
    LaunchedEffect(voiceState) {
        if (isSpeaking) {
            launch { while (true) { ripple1.snapTo(0f); ripple1.animateTo(1f, tween(2600, easing = LinearEasing)) } }
            launch { delay(870);  while (true) { ripple2.snapTo(0f); ripple2.animateTo(1f, tween(2600, easing = LinearEasing)) } }
            launch { delay(1730); while (true) { ripple3.snapTo(0f); ripple3.animateTo(1f, tween(2600, easing = LinearEasing)) } }
        }
    }

    val stateLabel = when {
        !engineReady              -> "INITIALIZING…"
        voiceState == VoiceState.LISTENING ||
        voiceState == VoiceState.RECORDING  -> "LISTENING…"
        voiceState == VoiceState.PROCESSING -> "THINKING…"
        voiceState == VoiceState.SPEAKING   -> "J.A.R.V.I.S SPEAKING"
        voiceState == VoiceState.ERROR      -> "TAP TO RETRY"
        else                                -> "TAP TO SPEAK, SIR"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Ambient field glow centered near mid-screen
        Canvas(
            modifier = Modifier
                .size(560.dp)
                .align(Alignment.Center)
                .graphicsLayer { translationY = -80.dp.value }
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f    to (if (isSpeaking) BrandCyan else BrandPurple).copy(alpha = 0.20f * glowAlpha),
                    0.38f to BrandCyan.copy(alpha = 0.12f * glowAlpha),
                    1f    to Color.Transparent
                )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // ── App bar ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Keyboard toggle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onToggleKeyboard() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Keyboard,
                        contentDescription = "Text mode",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Canvas(modifier = Modifier.size(8.dp)) {
                        drawCircle(BrandCyan)
                        drawCircle(BrandCyan.copy(alpha = 0.4f), radius = size.width)
                    }
                    Text(
                        "J.A.R.V.I.S",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        letterSpacing = 5.sp
                    )
                }

                Spacer(Modifier.size(40.dp))
            }

            // ── Orb stage ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = engineReady,
                                onClick = onOrbTap
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glow halo
                        Canvas(modifier = Modifier.size(260.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to (if (isSpeaking) BrandCyan else BrandPurple).copy(alpha = 0.32f * glowAlpha),
                                    1f to Color.Transparent
                                )
                            )
                        }

                        // IDLE: static guide ring + slow drifting arc
                        if (isIdle) {
                            Canvas(modifier = Modifier.size(218.dp)) {
                                drawCircle(BrandPurple.copy(alpha = 0.32f), style = Stroke(1.dp.toPx()))
                            }
                            Canvas(
                                modifier = Modifier
                                    .size(218.dp)
                                    .graphicsLayer { rotationZ = idleArcRot }
                            ) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0f to Color.Transparent,
                                        0.17f to BrandCyan.copy(alpha = 0.85f),
                                        0.33f to Color.Transparent,
                                        1f to Color.Transparent
                                    ),
                                    style = Stroke(2.dp.toPx())
                                )
                            }
                        }

                        // LISTENING: rotating gradient arc
                        if (isListening) {
                            Canvas(modifier = Modifier.size(218.dp)) {
                                drawCircle(BrandCyan.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()))
                            }
                            Canvas(
                                modifier = Modifier
                                    .size(244.dp)
                                    .graphicsLayer { rotationZ = listenArcRot }
                            ) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0f    to Color.Transparent,
                                        0.22f to BrandCyan,
                                        0.53f to BrandPurple,
                                        0.83f to Color.Transparent,
                                        1f    to Color.Transparent
                                    ),
                                    style = Stroke(6.dp.toPx())
                                )
                            }
                        }

                        // PROCESSING: dual counter-rotating arcs + particles
                        if (isProcessing) {
                            Canvas(
                                modifier = Modifier
                                    .size(250.dp)
                                    .graphicsLayer { rotationZ = procOuter }
                            ) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0f    to Color.Transparent,
                                        0.33f to BrandPurple,
                                        0.61f to Color.Transparent,
                                        1f    to Color.Transparent
                                    ),
                                    style = Stroke(4.dp.toPx())
                                )
                            }
                            Canvas(
                                modifier = Modifier
                                    .size(214.dp)
                                    .graphicsLayer { rotationZ = procInner }
                            ) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0f    to Color.Transparent,
                                        0.31f to BrandCyan,
                                        0.58f to Color.Transparent,
                                        1f    to Color.Transparent
                                    ),
                                    style = Stroke(4.dp.toPx())
                                )
                            }
                            // Orbiting particles
                            Canvas(modifier = Modifier.size(236.dp)) {
                                val r1 = size.width / 2f
                                val a1 = Math.toRadians(p1Angle.toDouble())
                                val pos1 = Offset(center.x + r1 * cos(a1).toFloat(), center.y + r1 * sin(a1).toFloat())
                                drawCircle(BrandCyan.copy(alpha = 0.6f), radius = 12.dp.toPx(), center = pos1)
                                drawCircle(BrandCyan, radius = 4.5.dp.toPx(), center = pos1)
                            }
                            Canvas(modifier = Modifier.size(200.dp)) {
                                val r2 = size.width / 2f
                                val a2 = Math.toRadians(p2Angle.toDouble())
                                val pos2 = Offset(center.x + r2 * cos(a2).toFloat(), center.y + r2 * sin(a2).toFloat())
                                drawCircle(BrandPurple.copy(alpha = 0.5f), radius = 9.dp.toPx(), center = pos2)
                                drawCircle(BrandPurple, radius = 3.5.dp.toPx(), center = pos2)
                            }
                        }

                        // SPEAKING: 3 staggered ripple rings
                        if (isSpeaking) {
                            Canvas(modifier = Modifier.size(300.dp)) {
                                val baseR = 94.dp.toPx()
                                fun ripple(progress: Float, baseAlpha: Float, color: Color) {
                                    if (progress <= 0f) return
                                    drawCircle(
                                        color = color.copy(alpha = baseAlpha * (1f - progress)),
                                        radius = baseR * (0.62f + 1.33f * progress),
                                        style = Stroke(2.dp.toPx())
                                    )
                                }
                                ripple(ripple1.value, 0.60f, BrandCyan)
                                ripple(ripple2.value, 0.45f, BrandCyan)
                                ripple(ripple3.value, 0.40f, BrandPurple)
                            }
                        }

                        // Arc reactor core
                        OrbCore(
                            modifier = Modifier
                                .size(180.dp)
                                .graphicsLayer { scaleX = coreScale; scaleY = coreScale },
                            isSpeaking = isSpeaking
                        )

                        // Waveform bars (listening)
                        if (isListening) {
                            WaveformBars(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stateLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                        letterSpacing = 3.sp
                    )
                }
            }

            // ── End session button ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(CardDark)
                        .clickable { onEndSession() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = "End session",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Gesture nav pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
private fun OrbCore(modifier: Modifier, isSpeaking: Boolean) {
    Canvas(modifier = modifier) {
        val r = size.width / 2f

        // Coil segment ring (alternating lit / dim segments around the rim)
        val segments = 30
        val sweep = 360f / segments
        for (i in 0 until segments) {
            drawArc(
                color = if (i % 2 == 0)
                    (if (isSpeaking) BrandCyan else BrandPurple).copy(alpha = 0.5f)
                else
                    Color.White.copy(alpha = 0.05f),
                startAngle = -90f + i * sweep,
                sweepAngle = sweep * 0.65f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Main core gradient
        val innerR = r - 13.dp.toPx()
        val coreCenter = Offset(center.x, center.y - innerR * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                0f    to if (isSpeaking) Color(0xFFCFF6FF) else Color(0xFFEAD9FF),
                0.26f to if (isSpeaking) Color(0xFF22C9E8) else Color(0xFFA06BF5),
                0.48f to if (isSpeaking) BrandCyan        else BrandPurple,
                0.74f to if (isSpeaking) Color(0xFF1F6E8C) else Color(0xFF2C8FB8),
                1f    to Color(0xFF0A1530),
                center = coreCenter,
                radius = innerR
            ),
            radius = innerR,
            center = coreCenter
        )

        // Outer glow bloom (simulates box-shadow)
        drawCircle(
            brush = Brush.radialGradient(
                0f    to Color.Transparent,
                0.55f to (if (isSpeaking) BrandCyan else BrandPurple).copy(alpha = 0.45f),
                0.80f to (if (isSpeaking) BrandCyan else BrandCyan).copy(alpha = 0.25f),
                1f    to Color.Transparent,
                radius = innerR * 1.5f
            ),
            radius = innerR * 1.5f
        )

        // Mid structural ring
        drawCircle(Color.White.copy(alpha = 0.22f), radius = r - 36.dp.toPx(), style = Stroke(1.dp.toPx()))

        // Inner accent ring
        drawCircle(
            (if (isSpeaking) BrandCyan else BrandPurple).copy(alpha = 0.4f),
            radius = r - 50.dp.toPx(),
            style = Stroke(1.dp.toPx())
        )

        // Bright center highlight
        val brightR = r - 62.dp.toPx()
        val brightCenter = Offset(center.x, center.y - brightR * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                0f    to Color.White,
                0.55f to if (isSpeaking) Color(0xFF9EEBFF) else Color(0xFFC9B0FF),
                1f    to Color.Transparent,
                center = brightCenter,
                radius = brightR
            ),
            radius = brightR,
            center = brightCenter
        )
    }
}

@Composable
private fun WaveformBars(modifier: Modifier = Modifier) {
    val baseHeights = listOf(16, 30, 46, 24, 52, 20, 42, 28, 50, 22, 34)
    Row(
        modifier = modifier.height(56.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        baseHeights.forEachIndexed { i, base ->
            val inf = rememberInfiniteTransition(label = "bar$i")
            val scale by inf.animateFloat(
                0.3f, 1f,
                infiniteRepeatable(
                    tween(900, delayMillis = i * 85, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "barH$i"
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((base * scale).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(BrandCyan, BrandPurple)))
            )
        }
    }
}
