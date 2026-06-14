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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.StrokeCap

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
    val p3Angle      by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3100,  easing = LinearEasing)), label = "p3")
    val p4Angle      by inf.animateFloat(360f, 0f,  infiniteRepeatable(tween(2700,  easing = LinearEasing)), label = "p4")
    val p5Angle      by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3600,  easing = LinearEasing)), label = "p5")

    // IDLE: orbit particles + breathing rings
    val idleOrbit1   by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "io1")
    val idleOrbit2   by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(19000, easing = LinearEasing)), label = "io2")
    val idleOrbit3   by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(9000,  easing = LinearEasing)), label = "io3")
    val idleRingA    by inf.animateFloat(0.10f, 0.38f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ira")
    val idleRingB    by inf.animateFloat(0.05f, 0.22f, infiniteRepeatable(tween(4100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "irb")
    val idleRingC    by inf.animateFloat(0.03f, 0.15f, infiniteRepeatable(tween(5600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "irc")

    // PROCESSING: dotted ring rotation + segment pulse
    val procDots     by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3800,  easing = LinearEasing)), label = "dots")
    val segPulse     by inf.animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(600,  easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "seg")

    // Ripple rings for SPEAKING — 5 staggered
    val ripple1 = remember { Animatable(0f) }
    val ripple2 = remember { Animatable(0f) }
    val ripple3 = remember { Animatable(0f) }
    val ripple4 = remember { Animatable(0f) }
    val ripple5 = remember { Animatable(0f) }

    // Frame clocks for circular waveforms
    var listenFrame by remember { mutableLongStateOf(0L) }
    var speakFrame  by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isListening) { if (isListening) while (true) { withFrameMillis { listenFrame = it } } }

    LaunchedEffect(voiceState) {
        if (isSpeaking) {
            launch { while (true) { ripple1.snapTo(0f); ripple1.animateTo(1f, tween(2200, easing = LinearEasing)) } }
            launch { delay(440);  while (true) { ripple2.snapTo(0f); ripple2.animateTo(1f, tween(2200, easing = LinearEasing)) } }
            launch { delay(880);  while (true) { ripple3.snapTo(0f); ripple3.animateTo(1f, tween(2200, easing = LinearEasing)) } }
            launch { delay(1320); while (true) { ripple4.snapTo(0f); ripple4.animateTo(1f, tween(2200, easing = LinearEasing)) } }
            launch { delay(1760); while (true) { ripple5.snapTo(0f); ripple5.animateTo(1f, tween(2200, easing = LinearEasing)) } }
            while (true) { withFrameMillis { speakFrame = it } }
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

                        // IDLE: 3 breathing concentric rings + drifting arc + 5 orbit particles
                        if (isIdle) {
                            // Outer breathing rings
                            Canvas(modifier = Modifier.size(278.dp)) {
                                drawCircle(BrandCyan.copy(alpha = idleRingC), style = Stroke(1.dp.toPx()))
                            }
                            Canvas(modifier = Modifier.size(248.dp)) {
                                drawCircle(BrandPurple.copy(alpha = idleRingB), style = Stroke(1.dp.toPx()))
                            }
                            Canvas(modifier = Modifier.size(218.dp)) {
                                drawCircle(BrandPurple.copy(alpha = idleRingA), style = Stroke(1.5.dp.toPx()))
                            }
                            // Drifting arc
                            Canvas(modifier = Modifier.size(218.dp).graphicsLayer { rotationZ = idleArcRot }) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0f to Color.Transparent,
                                        0.17f to BrandCyan.copy(alpha = 0.9f),
                                        0.33f to Color.Transparent,
                                        1f to Color.Transparent
                                    ),
                                    style = Stroke(2.5.dp.toPx())
                                )
                            }
                            // 5 orbiting particles at 3 radii
                            Canvas(modifier = Modifier.size(300.dp)) {
                                fun dot(deg: Float, orbitR: Float, sz: Float, col: Color, a: Float) {
                                    val ang = Math.toRadians(deg.toDouble())
                                    val p = Offset(center.x + cos(ang).toFloat() * orbitR, center.y + sin(ang).toFloat() * orbitR)
                                    drawCircle(col.copy(alpha = a * 0.35f), radius = sz * 2.2f, center = p)
                                    drawCircle(col.copy(alpha = a), radius = sz, center = p)
                                }
                                dot(idleOrbit1,        128.dp.toPx(), 3.5.dp.toPx(), BrandCyan,   0.75f)
                                dot(idleOrbit1 + 120f, 128.dp.toPx(), 2.5.dp.toPx(), BrandPurple, 0.60f)
                                dot(idleOrbit1 + 240f, 128.dp.toPx(), 3.0.dp.toPx(), BrandCyan,   0.50f)
                                dot(idleOrbit2,        108.dp.toPx(), 2.0.dp.toPx(), BrandPurple, 0.70f)
                                dot(idleOrbit3,        148.dp.toPx(), 2.0.dp.toPx(), BrandCyan,   0.45f)
                            }
                        }

                        // LISTENING: rotating arc + circular polar waveform
                        if (isListening) {
                            Canvas(modifier = Modifier.size(218.dp)) {
                                drawCircle(BrandCyan.copy(alpha = 0.20f), style = Stroke(1.dp.toPx()))
                            }
                            Canvas(modifier = Modifier.size(256.dp).graphicsLayer { rotationZ = listenArcRot }) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0f    to Color.Transparent,
                                        0.20f to BrandCyan,
                                        0.50f to BrandPurple,
                                        0.80f to Color.Transparent,
                                        1f    to Color.Transparent
                                    ),
                                    style = Stroke(8.dp.toPx())
                                )
                            }
                            // Circular polar waveform: 48 bars radiating from orb edge
                            Canvas(modifier = Modifier.size(300.dp)) {
                                val bars = 48
                                val orbR = 95.dp.toPx()
                                val t = listenFrame
                                for (i in 0 until bars) {
                                    val angle = (i.toFloat() / bars) * 2f * PI.toFloat()
                                    val wave = sin(t / 700.0 + angle * 4.0).toFloat()
                                    val barLen = (wave * 0.5f + 0.5f) * 22.dp.toPx() + 5.dp.toPx()
                                    val frac = i.toFloat() / bars
                                    val col = androidx.compose.ui.graphics.lerp(BrandPurple, BrandCyan, frac)
                                    val alpha = 0.55f + 0.35f * (wave * 0.5f + 0.5f)
                                    drawLine(
                                        color = col.copy(alpha = alpha),
                                        start = Offset(center.x + cos(angle) * orbR, center.y + sin(angle) * orbR),
                                        end   = Offset(center.x + cos(angle) * (orbR + barLen), center.y + sin(angle) * (orbR + barLen)),
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        // PROCESSING: triple arcs + dotted ring + 5 particles
                        if (isProcessing) {
                            Canvas(modifier = Modifier.size(268.dp).graphicsLayer { rotationZ = procOuter }) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0f    to Color.Transparent,
                                        0.30f to BrandPurple,
                                        0.58f to Color.Transparent,
                                        1f    to Color.Transparent
                                    ),
                                    style = Stroke(5.dp.toPx())
                                )
                            }
                            Canvas(modifier = Modifier.size(230.dp).graphicsLayer { rotationZ = procInner }) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        0f    to Color.Transparent,
                                        0.28f to BrandCyan,
                                        0.55f to Color.Transparent,
                                        1f    to Color.Transparent
                                    ),
                                    style = Stroke(5.dp.toPx())
                                )
                            }
                            // Dotted ring (16 evenly spaced dots rotating)
                            Canvas(modifier = Modifier.size(300.dp)) {
                                val dotCount = 16
                                val ringR = 148.dp.toPx()
                                for (i in 0 until dotCount) {
                                    val ang = Math.toRadians((procDots + i * (360f / dotCount)).toDouble())
                                    val p = Offset(center.x + cos(ang).toFloat() * ringR, center.y + sin(ang).toFloat() * ringR)
                                    val col = if (i % 2 == 0) BrandCyan else BrandPurple
                                    drawCircle(col.copy(alpha = 0.25f), radius = 5.dp.toPx(), center = p)
                                    drawCircle(col.copy(alpha = 0.80f), radius = 2.5.dp.toPx(), center = p)
                                }
                            }
                            // 5 orbiting particles
                            Canvas(modifier = Modifier.size(300.dp)) {
                                fun particle(deg: Float, orbitDp: Float, sz: Float, col: Color) {
                                    val ang = Math.toRadians(deg.toDouble())
                                    val r = orbitDp.dp.toPx()
                                    val p = Offset(center.x + cos(ang).toFloat() * r, center.y + sin(ang).toFloat() * r)
                                    drawCircle(col.copy(alpha = 0.45f), radius = sz * 2.5f, center = p)
                                    drawCircle(col, radius = sz, center = p)
                                }
                                particle(p1Angle, 118f, 4.5.dp.toPx(), BrandCyan)
                                particle(p2Angle, 98f,  3.5.dp.toPx(), BrandPurple)
                                particle(p3Angle, 138f, 3.0.dp.toPx(), BrandCyan)
                                particle(p4Angle, 108f, 4.0.dp.toPx(), BrandPurple)
                                particle(p5Angle, 125f, 2.5.dp.toPx(), BrandCyan)
                            }
                        }

                        // SPEAKING: 5 ripple rings + circular radial pulse
                        if (isSpeaking) {
                            Canvas(modifier = Modifier.size(300.dp)) {
                                val baseR = 94.dp.toPx()
                                val rippleColors = listOf(BrandCyan, BrandPurple, BrandCyan, BrandPurple, BrandCyan)
                                val rippleAlphas = listOf(0.65f, 0.50f, 0.55f, 0.45f, 0.40f)
                                val rippleWidths = listOf(2.5f, 1.5f, 2.0f, 1.5f, 1.0f)
                                listOf(ripple1.value, ripple2.value, ripple3.value, ripple4.value, ripple5.value)
                                    .forEachIndexed { i, progress ->
                                        if (progress > 0f) drawCircle(
                                            color = rippleColors[i].copy(alpha = rippleAlphas[i] * (1f - progress)),
                                            radius = baseR * (0.62f + 1.38f * progress),
                                            style = Stroke(rippleWidths[i].dp.toPx())
                                        )
                                    }
                            }
                            // Radial pulse bars around the orb
                            Canvas(modifier = Modifier.size(300.dp)) {
                                val bars = 32
                                val orbR = 94.dp.toPx()
                                val t = speakFrame
                                for (i in 0 until bars) {
                                    val angle = (i.toFloat() / bars) * 2f * PI.toFloat()
                                    val wave = sin(t / 380.0 + angle * 2.5).toFloat()
                                    val barLen = (wave * 0.5f + 0.5f) * 16.dp.toPx() + 4.dp.toPx()
                                    val alpha = 0.40f + 0.40f * (wave * 0.5f + 0.5f)
                                    drawLine(
                                        color = BrandCyan.copy(alpha = alpha),
                                        start = Offset(center.x + cos(angle) * orbR, center.y + sin(angle) * orbR),
                                        end   = Offset(center.x + cos(angle) * (orbR + barLen), center.y + sin(angle) * (orbR + barLen)),
                                        strokeWidth = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }

                        // Arc reactor core
                        OrbCore(
                            modifier = Modifier
                                .size(180.dp)
                                .graphicsLayer { scaleX = coreScale; scaleY = coreScale },
                            isSpeaking = isSpeaking,
                            isListening = isListening,
                            isProcessing = isProcessing,
                            segmentPulse = segPulse,
                        )
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
private fun OrbCore(
    modifier: Modifier,
    isSpeaking: Boolean,
    isListening: Boolean,
    isProcessing: Boolean,
    segmentPulse: Float,
) {
    val segColor = when {
        isSpeaking   -> BrandCyan
        isListening  -> BrandCyan
        isProcessing -> BrandPurple
        else         -> BrandPurple
    }
    val segAlpha = if (isProcessing) segmentPulse else 0.55f

    Canvas(modifier = modifier) {
        val r = size.width / 2f

        // Coil segment ring
        val segments = 30
        val sweep = 360f / segments
        for (i in 0 until segments) {
            drawArc(
                color = if (i % 2 == 0) segColor.copy(alpha = segAlpha)
                        else Color.White.copy(alpha = 0.05f),
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

