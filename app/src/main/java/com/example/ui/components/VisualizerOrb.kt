package com.example.ui.components

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.viewmodel.AssistantState

@Composable
fun VisualizerOrb(
    state: AssistantState,
    micLevel: Float,
    speakerLevel: Float,
    size: Dp = 220.dp,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbAnimation")

    // Idle breathing animation
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IdlePulse"
    )

    // Rotating ring for connecting
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Dynamic scale depending on state and audio level
    val dynamicScale = when (state) {
        AssistantState.IDLE -> idlePulse
        AssistantState.CONNECTING -> idlePulse
        AssistantState.LISTENING -> 1.0f + (micLevel * 0.35f)
        AssistantState.SPEAKING -> 1.0f + (speakerLevel * 0.45f)
        AssistantState.ERROR -> 1.0f
    }

    // Dynamic gradient colors
    val orbColors = when (state) {
        AssistantState.IDLE -> listOf(
            Color(0xFF6366F1), // Indigo
            Color(0xFF8B5CF6), // Purple
            Color(0xFF06B6D4)  // Cyan
        )
        AssistantState.CONNECTING -> listOf(
            Color(0xFFEC4899), // Pink
            Color(0xFF8B5CF6), // Violet
            Color(0xFF3B82F6)  // Blue
        )
        AssistantState.LISTENING -> listOf(
            Color(0xFF10B981), // Emerald
            Color(0xFF06B6D4), // Cyan
            Color(0xFF3B82F6)  // Blue
        )
        AssistantState.SPEAKING -> listOf(
            Color(0xFFFF007A), // Hot Pink
            Color(0xFF8B5CF6), // Purple
            Color(0xFF38BDF8)  // Sky Blue
        )
        AssistantState.ERROR -> listOf(
            Color(0xFFEF4444), // Red
            Color(0xFFF97316), // Orange
            Color(0xFFB91C1C)  // Dark Red
        )
    }

    val glowColor = orbColors.first().copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing aura / ripple
        Canvas(
            modifier = Modifier
                .size(size)
                .scale(dynamicScale * 1.25f)
        ) {
            drawCircle(
                color = glowColor,
                radius = size.toPx() / 2f
            )
        }

        // Mid reactive halo ring
        Canvas(
            modifier = Modifier
                .size(size * 0.9f)
                .rotate(if (state == AssistantState.CONNECTING) rotation else 0f)
                .scale(dynamicScale * 1.1f)
        ) {
            drawCircle(
                brush = Brush.sweepGradient(orbColors),
                radius = (size.toPx() * 0.9f) / 2f,
                style = Stroke(width = 6f)
            )
        }

        // Inner Glowing Core Orb
        Box(
            modifier = Modifier
                .size(size * 0.75f)
                .scale(dynamicScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = orbColors,
                        radius = size.value * 1.8f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Icon according to state
            when (state) {
                AssistantState.IDLE -> {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Start Assistant",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                AssistantState.CONNECTING -> {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = "Connecting",
                        tint = Color.White,
                        modifier = Modifier
                            .size(52.dp)
                            .rotate(rotation)
                    )
                }
                AssistantState.LISTENING -> {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Listening",
                        tint = Color.White,
                        modifier = Modifier
                            .size(54.dp)
                            .scale(1f + (micLevel * 0.3f))
                    )
                }
                AssistantState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Speaking",
                        tint = Color.White,
                        modifier = Modifier
                            .size(56.dp)
                            .scale(1f + (speakerLevel * 0.4f))
                    )
                }
                AssistantState.ERROR -> {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Error",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}
