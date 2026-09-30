package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages streaming audio output from Gemini Live responses to the device speaker.
 * Requirements:
 * - 24000 Hz sample rate (Gemini Live native output)
 * - 16-bit PCM little-endian
 * - Mono channel
 * - Sequential playback queue
 * - Gapless chunk streaming
 * - Interruption handling (clear queue, flush AudioTrack)
 * - Speaker diagnostic test (440Hz tone)
 * - Real-time amplitude calculation for speaking visualizer
 */
class AudioTrackManager(
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackEnded: () -> Unit,
    private val onAudioLevelChanged: (level: Float) -> Unit,
    private val onLog: (tag: String, message: String) -> Unit = { tag, msg -> Log.d(tag, msg) }
) {
    companion object {
        private const val TAG = "AudioTrackManager"
        const val SAMPLE_RATE = 24000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    @Volatile
    var isPlaying = false
        private set

    init {
        initializeAudioTrack()
    }

    private fun initializeAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize * 4, 32768)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                onLog(TAG, "AudioTrack initialized successfully. sampleRate=$SAMPLE_RATE Hz, bufferSize=$bufferSize bytes")
            } else {
                onLog(TAG, "AudioTrack initialization failed! State: ${audioTrack?.state}")
            }
        } catch (e: Exception) {
            onLog(TAG, "Exception initializing AudioTrack: ${e.message}")
        }
    }

    fun startPlaybackLoop(coroutineScope: CoroutineScope) {
        if (playbackJob?.isActive == true) return

        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            initializeAudioTrack()
        }

        try {
            audioTrack?.play()
            onLog(TAG, "AudioTrack play state activated")
        } catch (e: Exception) {
            onLog(TAG, "Error calling audioTrack.play(): ${e.message}")
        }

        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            var activePlayingState = false
            var idleCount = 0

            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null && chunk.isNotEmpty()) {
                    if (!activePlayingState) {
                        activePlayingState = true
                        isPlaying = true
                        onPlaybackStarted()
                        onLog(TAG, "Audio playback started through device speaker")
                    }
                    idleCount = 0

                    // Calculate amplitude for speaking visualizer
                    val rms = calculateRms(chunk, chunk.size)
                    val normalizedLevel = (rms / 7000f).coerceIn(0f, 1f)
                    onAudioLevelChanged(normalizedLevel)

                    // Write PCM buffer to AudioTrack
                    var bytesWritten = 0
                    while (bytesWritten < chunk.size && isActive) {
                        val written = audioTrack?.write(chunk, bytesWritten, chunk.size - bytesWritten) ?: -1
                        if (written > 0) {
                            bytesWritten += written
                        } else {
                            onLog(TAG, "AudioTrack.write returned error: $written")
                            break
                        }
                    }
                } else {
                    if (activePlayingState) {
                        idleCount++
                        if (idleCount > 6) { // ~300ms without new chunks -> turn complete
                            activePlayingState = false
                            isPlaying = false
                            onAudioLevelChanged(0f)
                            onPlaybackEnded()
                            onLog(TAG, "Audio playback ended. Speaker queue drained")
                        }
                    }
                    try {
                        Thread.sleep(30)
                    } catch (_: InterruptedException) {}
                }
            }
        }
    }

    fun enqueueBase64Audio(base64Data: String) {
        try {
            val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (pcmBytes.isNotEmpty()) {
                val durationMs = (pcmBytes.size / (SAMPLE_RATE * 2.0 / 1000.0)).toInt()
                onLog(TAG, "Received audio chunk: ${pcmBytes.size} bytes (~${durationMs}ms at ${SAMPLE_RATE}Hz). Enqueued.")
                audioQueue.offer(pcmBytes)
            }
        } catch (e: Exception) {
            onLog(TAG, "Base64 decoding failed for audio chunk: ${e.message}")
        }
    }

    /**
     * Interruption handling:
     * When user speaks while Arushi is speaking, immediately clear queued audio,
     * flush the AudioTrack buffer, and reset state.
     */
    fun interrupt() {
        onLog(TAG, "Audio playback interrupted! Flushing AudioTrack and clearing queue.")
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            onLog(TAG, "Error during interrupt flush: ${e.message}")
        }
        isPlaying = false
        onAudioLevelChanged(0f)
        onPlaybackEnded()
    }

    /**
     * Requirement 15: Speaker Diagnostic Test
     * Generates a 440Hz sine wave tone at 24000Hz (1 second) and plays it through the SAME AudioTrack.
     */
    fun playSpeakerTestTone(frequency: Float = 440f, durationSeconds: Float = 1.0f) {
        onLog(TAG, "Running Speaker Diagnostic Test: 440Hz tone, duration=${durationSeconds}s")
        try {
            val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
            val pcmBytes = ByteArray(totalSamples * 2)

            for (i in 0 until totalSamples) {
                val angle = 2.0 * PI * i * frequency / SAMPLE_RATE
                val sampleValue = (sin(angle) * 16000.0).toInt().toShort()
                // 16-bit PCM little endian
                pcmBytes[i * 2] = (sampleValue.toInt() and 0xFF).toByte()
                pcmBytes[i * 2 + 1] = ((sampleValue.toInt() shr 8) and 0xFF).toByte()
            }

            audioQueue.offer(pcmBytes)
            onLog(TAG, "Diagnostic 440Hz tone enqueued successfully (${pcmBytes.size} bytes)")
        } catch (e: Exception) {
            onLog(TAG, "Diagnostic test failed: ${e.message}")
        }
    }

    fun release() {
        onLog(TAG, "Releasing AudioTrackManager")
        playbackJob?.cancel()
        playbackJob = null
        audioQueue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            onLog(TAG, "Error releasing AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
            isPlaying = false
            onAudioLevelChanged(0f)
        }
    }

    private fun calculateRms(buffer: ByteArray, bytesRead: Int): Float {
        var sum = 0.0
        val sampleCount = bytesRead / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until bytesRead step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        return sqrt(sum / sampleCount).toFloat()
    }
}
