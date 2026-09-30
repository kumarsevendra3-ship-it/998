package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.ui.components.DebugLogsDialog
import com.example.ui.components.TranscriptView
import com.example.ui.components.VisualizerOrb
import com.example.viewmodel.ArushiViewModel
import com.example.viewmodel.AssistantState
import kotlinx.coroutines.launch

@Composable
fun ArushiScreen(
    viewModel: ArushiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val assistantState by viewModel.assistantState.collectAsState()
    val micLevel by viewModel.micLevel.collectAsState()
    val speakerLevel by viewModel.speakerLevel.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val activeActionFeedback by viewModel.activeActionFeedback.collectAsState()
    val conversationHistory by viewModel.conversationHistory.collectAsState()
    val debugLogs by viewModel.debugLogs.collectAsState()

    var showLogsDialog by remember { mutableStateOf(false) }

    // Audio Permission Launcher
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val key = BuildConfig.GEMINI_API_KEY
            viewModel.startAssistant(key)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for voice conversation.")
            }
        }
    }

    val onToggleClicked = {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            val key = BuildConfig.GEMINI_API_KEY
            viewModel.toggleAssistant(key)
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF090B14),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF090B14),
                            Color(0xFF06080F)
                        )
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar Header
            TopHeaderBar(
                onOpenLogs = { showLogsDialog = true }
            )

            // Status Indicator Banner
            StatusBanner(
                state = assistantState,
                errorMessage = errorMessage,
                onRetry = onToggleClicked
            )

            // Middle Interactive Area: Glowing Orb
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f),
                contentAlignment = Alignment.Center
            ) {
                VisualizerOrb(
                    state = assistantState,
                    micLevel = micLevel,
                    speakerLevel = speakerLevel,
                    size = 230.dp,
                    onClick = onToggleClicked
                )
            }

            // Quick Voice Suggestion Prompts
            PromptChipsRow(
                onPromptClicked = { promptText ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Say: \"$promptText\"")
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Conversation / Transcript Scroll View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
            ) {
                TranscriptView(
                    items = conversationHistory,
                    activeActionFeedback = activeActionFeedback,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom Action & Diagnostic Bar
            BottomControlBar(
                state = assistantState,
                onToggleAssistant = onToggleClicked,
                onTestSpeaker = {
                    viewModel.testSpeaker()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Playing 440Hz test tone through speaker...")
                    }
                },
                onOpenLogs = { showLogsDialog = true }
            )
        }
    }

    if (showLogsDialog) {
        DebugLogsDialog(
            logs = debugLogs,
            onDismiss = { showLogsDialog = false }
        )
    }
}

@Composable
private fun TopHeaderBar(
    onOpenLogs: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Arushi",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF6366F1).copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color(0xFF818CF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "English • हिन्दी • Hinglish • Multi-Lingual",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }

        IconButton(
            onClick = onOpenLogs,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Icon(
                imageVector = Icons.Rounded.BugReport,
                contentDescription = "Logs",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusBanner(
    state: AssistantState,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    val (statusText, statusColor, bgColor) = when (state) {
        AssistantState.IDLE -> Triple(
            "Tap the orb or mic button to talk",
            Color(0xFF94A3B8),
            Color(0xFF1E293B).copy(alpha = 0.6f)
        )
        AssistantState.CONNECTING -> Triple(
            "Connecting to Gemini Live...",
            Color(0xFFFBBF24),
            Color(0xFF78350F).copy(alpha = 0.4f)
        )
        AssistantState.LISTENING -> Triple(
            "Listening... Speak naturally in any language",
            Color(0xFF34D399),
            Color(0xFF064E3B).copy(alpha = 0.5f)
        )
        AssistantState.SPEAKING -> Triple(
            "Arushi is speaking...",
            Color(0xFFF472B6),
            Color(0xFF831843).copy(alpha = 0.5f)
        )
        AssistantState.ERROR -> Triple(
            errorMessage ?: "Error encountered (tap to retry)",
            Color(0xFFF87171),
            Color(0xFF7F1D1D).copy(alpha = 0.5f)
        )
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(bgColor)
            .clickable(enabled = state == AssistantState.ERROR, onClick = onRetry)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PromptChipsRow(
    onPromptClicked: (String) -> Unit
) {
    val prompts = listOf(
        "Kya haal hai Arushi?",
        "Open WhatsApp",
        "Tell me a witty joke",
        "Open YouTube",
        "Call Mom",
        "Hindi mein baat karo",
        "Who are you?"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEach { prompt ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onPromptClicked(prompt) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = prompt,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun BottomControlBar(
    state: AssistantState,
    onToggleAssistant: () -> Unit,
    onTestSpeaker: () -> Unit,
    onOpenLogs: () -> Unit
) {
    Surface(
        color = Color(0xFF0F1424),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speaker Diagnostic Test Button (Requirement 15)
            OutlinedButton(
                onClick = onTestSpeaker,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF38BDF8)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Test Speaker",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Big Glowing Center Mic/Power Button
            val isListening = state == AssistantState.LISTENING || state == AssistantState.SPEAKING
            val buttonColor = if (isListening) Color(0xFFEF4444) else Color(0xFF6366F1)

            Button(
                onClick = onToggleAssistant,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier.size(64.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = if (isListening) "Stop" else "Start",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            // Technical Logs shortcut button
            OutlinedButton(
                onClick = onOpenLogs,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFA78BFA)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Logs",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
