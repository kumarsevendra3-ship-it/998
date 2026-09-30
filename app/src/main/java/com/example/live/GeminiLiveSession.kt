package com.example.live

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages the bidirectional WebSocket session with Gemini Live API.
 * Encapsulates setup handshake, microphone streaming, audio output parsing,
 * interruption handling, and tool/function execution.
 */
class GeminiLiveSession(
    private val apiKey: String,
    private val voiceName: String = "Aoede",
    private val onSessionConnected: () -> Unit,
    private val onSessionDisconnected: (reason: String) -> Unit,
    private val onAudioReceived: (base64Audio: String) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onTranscriptReceived: (text: String, isUser: Boolean) -> Unit,
    private val onToolCallReceived: (callId: String, functionName: String, args: JSONObject) -> Unit,
    private val onLog: (tag: String, message: String) -> Unit = { tag, msg -> Log.d(tag, msg) }
) {
    companion object {
        private const val TAG = "GeminiLiveSession"
        private const val HOST = "generativelanguage.googleapis.com"
        private const val PATH = "/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        
        // Preferred Gemini Live models
        const val DEFAULT_MODEL = "models/gemini-2.0-flash-exp"
    }

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    @Volatile
    var isConnected = false
        private set

    fun connect(model: String = DEFAULT_MODEL) {
        if (isConnected) {
            onLog(TAG, "Live session already connected")
            return
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val error = "Missing or placeholder Gemini API key. Ensure valid key is set in .env / Secrets."
            onLog(TAG, error)
            onSessionDisconnected(error)
            return
        }

        val url = "wss://$HOST$PATH?key=$apiKey"
        onLog(TAG, "Connecting to Gemini Live WebSocket: wss://$HOST$PATH with model: $model, voice: $voiceName")

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                onLog(TAG, "Gemini session connected! WebSocket handshake successful.")
                sendSetupMessage(webSocket, model)
                onSessionConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onLog(TAG, "Gemini session closing: code=$code, reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                onLog(TAG, "Gemini session disconnected: code=$code, reason=$reason")
                onSessionDisconnected(reason.ifBlank { "Session closed ($code)" })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                val errorMsg = "WebSocket failure: ${t.message ?: t.javaClass.simpleName}"
                onLog(TAG, errorMsg)
                onSessionDisconnected(errorMsg)
            }
        })
    }

    /**
     * Sends the initial Setup configuration message containing model, system instructions,
     * voice config, response modalities (AUDIO), and available safe device tools.
     */
    private fun sendSetupMessage(ws: WebSocket, model: String) {
        try {
            val setupObj = JSONObject()
            setupObj.put("model", model)

            // Generation config
            val genConfig = JSONObject()
            val modalities = JSONArray().apply { put("AUDIO") }
            genConfig.put("responseModalities", modalities)

            val speechConfig = JSONObject()
            val voiceConfig = JSONObject()
            val prebuiltVoiceConfig = JSONObject().apply {
                put("voiceName", voiceName)
            }
            voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
            speechConfig.put("voiceConfig", voiceConfig)
            genConfig.put("speechConfig", speechConfig)
            setupObj.put("generationConfig", genConfig)

            // System Instruction
            val sysInstruction = JSONObject()
            val parts = JSONArray()
            val promptPart = JSONObject().apply {
                put(
                    "text",
                    "You are Arushi, a young, confident, witty, playful, and emotionally responsive virtual assistant. " +
                    "Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. " +
                    "Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. " +
                    "Automatically understand and respond in the language the user is speaking, including Hindi, English, Hinglish, Marathi, " +
                    "Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and other languages. " +
                    "Keep responses natural, engaging, and concise enough for real-time voice conversation. " +
                    "You can execute safe supported device actions through available tools: openWhatsApp, openApp, openUrl, makeCall, and callContact. " +
                    "Never claim that an action was completed unless the application actually executed it. " +
                    "Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality."
                )
            }
            parts.put(promptPart)
            sysInstruction.put("parts", parts)
            setupObj.put("systemInstruction", sysInstruction)

            // Tool Function Declarations
            val toolsArray = JSONArray()
            val functionDeclarations = JSONArray()

            // 1. openWhatsApp
            functionDeclarations.put(JSONObject().apply {
                put("name", "openWhatsApp")
                put("description", "Opens WhatsApp application or chat when requested by the user in any language (e.g. 'Open WhatsApp', 'WhatsApp kholo', 'WhatsApp open karo').")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject())
                })
            })

            // 2. openApp
            functionDeclarations.put(JSONObject().apply {
                put("name", "openApp")
                put("description", "Opens a supported installed application such as YouTube, Instagram, WhatsApp, Chrome, Camera, Maps, Calculator, Clock, Calendar, or Settings.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("appName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The name of the app to open (e.g. YouTube, Instagram, Camera, Maps, Chrome, Calculator, Clock, Settings)")
                        })
                    })
                    put("required", JSONArray().apply { put("appName") })
                })
            })

            // 3. openUrl
            functionDeclarations.put(JSONObject().apply {
                put("name", "openUrl")
                put("description", "Opens a validated website URL in the user's browser.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("url", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The full web URL to open starting with https://")
                        })
                    })
                    put("required", JSONArray().apply { put("url") })
                })
            })

            // 4. makeCall
            functionDeclarations.put(JSONObject().apply {
                put("name", "makeCall")
                put("description", "Opens the phone dialer with a specified phone number.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("phoneNumber", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The phone number to dial")
                        })
                    })
                    put("required", JSONArray().apply { put("phoneNumber") })
                })
            })

            // 5. callContact
            functionDeclarations.put(JSONObject().apply {
                put("name", "callContact")
                put("description", "Searches device contacts by name (e.g. 'Mom', 'Mummy', 'Dad', 'Rahul') and initiates a call or opens the dialer.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("contactName", JSONObject().apply {
                            put("type", "STRING")
                            put("description", "The contact name to search for (e.g. Mom, Mummy, Dad, Rahul)")
                        })
                    })
                    put("required", JSONArray().apply { put("contactName") })
                })
            })

            val toolsObj = JSONObject()
            toolsObj.put("functionDeclarations", functionDeclarations)
            toolsArray.put(toolsObj)
            setupObj.put("tools", toolsArray)

            val root = JSONObject().apply { put("setup", setupObj) }
            val messageStr = root.toString()

            onLog(TAG, "Sending Gemini Live Setup message with 5 safe device tools")
            ws.send(messageStr)
        } catch (e: Exception) {
            onLog(TAG, "Error building setup message: ${e.message}")
        }
    }

    /**
     * Streams a 16kHz PCM Base64 chunk to Gemini Live.
     */
    fun sendRealtimeAudio(base64Chunk: String) {
        if (!isConnected || webSocket == null) return

        try {
            val root = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()
            val chunk = JSONObject().apply {
                put("mimeType", "audio/pcm;rate=16000")
                put("data", base64Chunk)
            }
            mediaChunks.put(chunk)
            realtimeInput.put("mediaChunks", mediaChunks)
            root.put("realtimeInput", realtimeInput)

            webSocket?.send(root.toString())
        } catch (e: Exception) {
            onLog(TAG, "Error sending realtime audio chunk: ${e.message}")
        }
    }

    /**
     * Parses incoming JSON payload from Gemini Live.
     * Extracts audio buffers, transcription, interruption flags, and tool calls.
     */
    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            // 1. Check for Server Content
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                // Interruption signal
                if (serverContent.optBoolean("interrupted", false)) {
                    onLog(TAG, "Server signal: user INTERRUPTED the assistant")
                    onInterrupted()
                }

                // Model turn parts (Audio and/or text)
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Check audio inlineData
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                val mimeType = inlineData.optString("mimeType", "")
                                val audioData = inlineData.optString("data", "")
                                if (audioData.isNotEmpty()) {
                                    onLog(TAG, "Received native audio from Gemini Live ($mimeType, length=${audioData.length})")
                                    onAudioReceived(audioData)
                                }
                            }

                            // Check text / transcript
                            val text = part.optString("text", "")
                            if (text.isNotEmpty()) {
                                onLog(TAG, "Received transcript from Arushi: $text")
                                onTranscriptReceived(text, false)
                            }
                        }
                    }
                }

                // Turn complete signal
                if (serverContent.optBoolean("turnComplete", false)) {
                    onLog(TAG, "Gemini Live turn complete")
                    onTurnComplete()
                }
            }

            // 2. Check for Tool / Function Calls
            val toolCall = root.optJSONObject("toolCall")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "")
                        val name = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        onLog(TAG, "Received tool call from Gemini Live: id=$callId, name=$name, args=$args")
                        onToolCallReceived(callId, name, args)
                    }
                }
            }

        } catch (e: Exception) {
            onLog(TAG, "Error parsing incoming Gemini Live message: ${e.message}")
        }
    }

    /**
     * Sends execution response for a tool/function call back to Gemini Live.
     */
    fun sendToolResponse(callId: String, output: JSONObject) {
        if (!isConnected || webSocket == null) return

        try {
            val root = JSONObject()
            val toolResponse = JSONObject()
            val functionResponses = JSONArray()

            val fResponse = JSONObject().apply {
                put("id", callId)
                put("response", JSONObject().apply {
                    put("output", output)
                })
            }
            functionResponses.put(fResponse)
            toolResponse.put("functionResponses", functionResponses)
            root.put("toolResponse", toolResponse)

            onLog(TAG, "Sending tool response for callId=$callId: $output")
            webSocket?.send(root.toString())
        } catch (e: Exception) {
            onLog(TAG, "Error sending tool response: ${e.message}")
        }
    }

    fun disconnect() {
        onLog(TAG, "Disconnecting Gemini Live session")
        isConnected = false
        try {
            webSocket?.close(1000, "User closed session")
        } catch (e: Exception) {
            onLog(TAG, "Error closing WebSocket: ${e.message}")
        } finally {
            webSocket = null
            client?.dispatcher?.executorService?.shutdown()
            client = null
        }
    }
}
