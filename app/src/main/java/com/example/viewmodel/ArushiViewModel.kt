package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.actions.DeviceActionHandler
import com.example.audio.AudioRecordManager
import com.example.audio.AudioTrackManager
import com.example.live.GeminiLiveSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AssistantState {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class ConversationItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "You", "Arushi", "Action"
    val message: String,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ArushiViewModel"
    }

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _speakerLevel = MutableStateFlow(0f)
    val speakerLevel: StateFlow<Float> = _speakerLevel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeActionFeedback = MutableStateFlow<String?>(null)
    val activeActionFeedback: StateFlow<String?> = _activeActionFeedback.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationItem>> = _conversationHistory.asStateFlow()

    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    private val deviceActionHandler = DeviceActionHandler(application) { tag, msg ->
        addDebugLog("[$tag] $msg")
    }

    private var audioRecordManager: AudioRecordManager? = null
    private var audioTrackManager: AudioTrackManager? = null
    private var liveSession: GeminiLiveSession? = null

    init {
        addDebugLog("[System] Arushi AI Voice Assistant initialized")
        setupAudioPipeline()
    }

    private fun setupAudioPipeline() {
        audioRecordManager = AudioRecordManager(
            onAudioChunkReady = { base64Chunk ->
                liveSession?.sendRealtimeAudio(base64Chunk)
            },
            onAudioLevelChanged = { level ->
                if (_assistantState.value == AssistantState.LISTENING) {
                    _micLevel.value = level
                }
            },
            onLog = { tag, msg -> addDebugLog("[$tag] $msg") }
        )

        audioTrackManager = AudioTrackManager(
            onPlaybackStarted = {
                viewModelScope.launch(Dispatchers.Main) {
                    _assistantState.value = AssistantState.SPEAKING
                    addDebugLog("[UI] State changed to SPEAKING")
                }
            },
            onPlaybackEnded = {
                viewModelScope.launch(Dispatchers.Main) {
                    if (liveSession?.isConnected == true) {
                        _assistantState.value = AssistantState.LISTENING
                        addDebugLog("[UI] State changed to LISTENING")
                    } else if (_assistantState.value != AssistantState.ERROR) {
                        _assistantState.value = AssistantState.IDLE
                        addDebugLog("[UI] State changed to IDLE")
                    }
                }
            },
            onAudioLevelChanged = { level ->
                if (_assistantState.value == AssistantState.SPEAKING) {
                    _speakerLevel.value = level
                }
            },
            onLog = { tag, msg -> addDebugLog("[$tag] $msg") }
        )
    }

    fun toggleAssistant(apiKey: String) {
        if (_assistantState.value == AssistantState.IDLE || _assistantState.value == AssistantState.ERROR) {
            startAssistant(apiKey)
        } else {
            stopAssistant()
        }
    }

    fun startAssistant(apiKey: String) {
        if (_assistantState.value == AssistantState.CONNECTING || _assistantState.value == AssistantState.LISTENING) {
            return
        }

        _errorMessage.value = null
        _assistantState.value = AssistantState.CONNECTING
        addDebugLog("[Session] Starting Arushi Live Session...")

        audioTrackManager?.startPlaybackLoop(viewModelScope)

        liveSession = GeminiLiveSession(
            apiKey = apiKey,
            voiceName = "Aoede",
            onSessionConnected = {
                viewModelScope.launch(Dispatchers.Main) {
                    _assistantState.value = AssistantState.LISTENING
                    addDebugLog("[Session] Live Session ready! Starting microphone capture...")
                    val started = audioRecordManager?.startRecording(viewModelScope) ?: false
                    if (!started) {
                        _assistantState.value = AssistantState.ERROR
                        _errorMessage.value = "Failed to start microphone. Please check permissions."
                    }
                }
            },
            onSessionDisconnected = { reason ->
                viewModelScope.launch(Dispatchers.Main) {
                    addDebugLog("[Session] Disconnected: $reason")
                    audioRecordManager?.stopRecording()
                    if (_assistantState.value != AssistantState.IDLE) {
                        _assistantState.value = AssistantState.ERROR
                        _errorMessage.value = reason
                    }
                }
            },
            onAudioReceived = { base64Audio ->
                audioTrackManager?.enqueueBase64Audio(base64Audio)
            },
            onInterrupted = {
                viewModelScope.launch(Dispatchers.Main) {
                    addDebugLog("[Interruption] User interrupted. Halting current audio.")
                    audioTrackManager?.interrupt()
                    _assistantState.value = AssistantState.LISTENING
                }
            },
            onTurnComplete = {
                viewModelScope.launch(Dispatchers.Main) {
                    addDebugLog("[Turn] Turn complete signal received from model.")
                }
            },
            onTranscriptReceived = { text, isUser ->
                viewModelScope.launch(Dispatchers.Main) {
                    addConversationItem(if (isUser) "You" else "Arushi", text)
                }
            },
            onToolCallReceived = { callId, functionName, args ->
                handleToolCall(callId, functionName, args)
            },
            onLog = { tag, msg -> addDebugLog("[$tag] $msg") }
        )

        liveSession?.connect()
    }

    private fun handleToolCall(callId: String, functionName: String, args: JSONObject) {
        viewModelScope.launch(Dispatchers.Main) {
            val actionDescription = when (functionName) {
                "openWhatsApp" -> "Opening WhatsApp..."
                "openApp" -> "Opening ${args.optString("appName", "app")}..."
                "openUrl" -> "Opening link ${args.optString("url", "")}..."
                "makeCall" -> "Calling ${args.optString("phoneNumber", "")}..."
                "callContact" -> "Calling contact ${args.optString("contactName", "")}..."
                else -> "Executing $functionName..."
            }
            _activeActionFeedback.value = actionDescription
            addConversationItem("Action", actionDescription)
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = deviceActionHandler.executeAction(functionName, args)
            liveSession?.sendToolResponse(callId, result)
            viewModelScope.launch(Dispatchers.Main) {
                val success = result.optBoolean("success", false)
                val msg = result.optString("message", result.optString("error", "Action completed"))
                addDebugLog("[Tool Result] $functionName -> success=$success, $msg")
                _activeActionFeedback.value = null
            }
        }
    }

    fun stopAssistant() {
        addDebugLog("[Session] Stopping Arushi assistant...")
        audioRecordManager?.stopRecording()
        audioTrackManager?.interrupt()
        liveSession?.disconnect()
        liveSession = null
        _assistantState.value = AssistantState.IDLE
        _micLevel.value = 0f
        _speakerLevel.value = 0f
        _activeActionFeedback.value = null
        addDebugLog("[Session] Assistant stopped. Idle.")
    }

    /**
     * Requirement 15: Speaker Diagnostic Test
     */
    fun testSpeaker() {
        addDebugLog("[Diagnostic] Speaker Diagnostic Test requested")
        audioTrackManager?.startPlaybackLoop(viewModelScope)
        audioTrackManager?.playSpeakerTestTone(440f, 1.0f)
    }

    fun clearError() {
        _errorMessage.value = null
        if (_assistantState.value == AssistantState.ERROR) {
            _assistantState.value = AssistantState.IDLE
        }
    }

    private fun addConversationItem(sender: String, message: String) {
        val item = ConversationItem(sender = sender, message = message)
        _conversationHistory.value = (_conversationHistory.value + item).takeLast(50)
    }

    private fun addDebugLog(log: String) {
        Log.d(TAG, log)
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = "[$time] $log"
        _debugLogs.value = (_debugLogs.value + entry).takeLast(100)
    }

    override fun onCleared() {
        super.onCleared()
        stopAssistant()
        audioTrackManager?.release()
    }
}
