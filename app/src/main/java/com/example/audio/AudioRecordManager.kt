package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Manages continuous microphone audio capture for Gemini Live session.
 * Requirements:
 * - 16000 Hz sample rate
 * - 16-bit PCM little-endian
 * - Mono channel
 * - Base64 encoded chunks
 * - Real-time RMS audio level calculation for visualizer
 */
class AudioRecordManager(
    private val onAudioChunkReady: (base64Chunk: String) -> Unit,
    private val onAudioLevelChanged: (level: Float) -> Unit,
    private val onLog: (tag: String, message: String) -> Unit = { tag, msg -> Log.d(tag, msg) }
) {
    companion object {
        private const val TAG = "AudioRecordManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 2048 bytes = 1024 16-bit samples = 64ms chunk at 16kHz
        private const val CHUNK_BYTE_SIZE = 2048
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile
    var isRecording = false
        private set

    @SuppressLint("MissingPermission")
    fun startRecording(coroutineScope: CoroutineScope): Boolean {
        if (isRecording) {
            onLog(TAG, "Recording already in progress")
            return true
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onLog(TAG, "AudioRecord minBufferSize error: $minBufferSize")
                return false
            }

            val bufferSize = maxOf(minBufferSize * 2, CHUNK_BYTE_SIZE * 2)
            onLog(TAG, "Microphone permission checked. Initializing AudioRecord: sampleRate=$SAMPLE_RATE, bufferSize=$bufferSize")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to DEFAULT audio source if VOICE_RECOGNITION is unavailable
                audioRecord?.release()
                onLog(TAG, "VOICE_RECOGNITION uninitialized, falling back to MIC audio source")
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onLog(TAG, "Failed to initialize AudioRecord. State: ${audioRecord?.state}")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            onLog(TAG, "Microphone started successfully. Recording PCM chunks at $SAMPLE_RATE Hz mono")

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_BYTE_SIZE)
                var chunkCount = 0

                while (isActive && isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        // Calculate RMS amplitude for real-time visualizer
                        val rms = calculateRms(buffer, bytesRead)
                        val normalizedLevel = (rms / 8000f).coerceIn(0f, 1f)
                        onAudioLevelChanged(normalizedLevel)

                        // Convert PCM bytes to Base64 without newlines
                        val base64 = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
                        chunkCount++
                        if (chunkCount % 25 == 0) {
                            onLog(TAG, "Microphone audio chunk #$chunkCount sent (bytes=$bytesRead, level=${String.format("%.2f", normalizedLevel)})")
                        }
                        onAudioChunkReady(base64)
                    } else if (bytesRead < 0) {
                        onLog(TAG, "AudioRecord read error: $bytesRead")
                        break
                    }
                }
                onAudioLevelChanged(0f)
            }
            return true
        } catch (e: Exception) {
            onLog(TAG, "Error starting AudioRecord: ${e.message}")
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        if (!isRecording && audioRecord == null) return
        onLog(TAG, "Microphone stopping...")
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            onLog(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord?.release()
            audioRecord = null
            onAudioLevelChanged(0f)
            onLog(TAG, "Microphone stopped and cleaned up")
        }
    }

    private fun calculateRms(buffer: ByteArray, bytesRead: Int): Float {
        var sum = 0.0
        val sampleCount = bytesRead / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until bytesRead step 2) {
            // Little-endian 16-bit PCM
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        return sqrt(sum / sampleCount).toFloat()
    }
}
