/**
 * Audio format conversion utilities for Gemini Live WebSocket API.
 * Gemini Live requires:
 * - Input: 16000 Hz, 16-bit linear PCM, mono, little-endian, Base64
 * - Output: 24000 Hz, 16-bit linear PCM, mono, little-endian, Base64
 */

export function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binaryString = window.atob(base64);
  const len = binaryString.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}

export function arrayBufferToBase64(buffer: ArrayBuffer): string {
  let binary = '';
  const bytes = new Uint8Array(buffer);
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return window.btoa(binary);
}

/**
 * Converts 16-bit signed PCM ArrayBuffer (little-endian) into Float32Array [-1.0, 1.0]
 */
export function pcm16ToFloat32(pcmBuffer: ArrayBuffer): Float32Array {
  const dataView = new DataView(pcmBuffer);
  const sampleCount = Math.floor(pcmBuffer.byteLength / 2);
  const float32 = new Float32Array(sampleCount);

  for (let i = 0; i < sampleCount; i++) {
    const int16 = dataView.getInt16(i * 2, true); // little-endian
    // Normalize to [-1.0, 1.0]
    float32[i] = int16 < 0 ? int16 / 32768 : int16 / 32767;
  }
  return float32;
}

/**
 * Converts Float32Array [-1.0, 1.0] into 16-bit signed PCM ArrayBuffer (little-endian)
 */
export function float32ToPcm16(float32: Float32Array): ArrayBuffer {
  const buffer = new ArrayBuffer(float32.length * 2);
  const view = new DataView(buffer);
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]));
    const val = s < 0 ? s * 0x8000 : s * 0x7FFF;
    view.setInt16(i * 2, Math.floor(val), true); // little-endian
  }
  return buffer;
}

/**
 * Resamples a Float32Array from inSampleRate to outSampleRate (e.g. 48000 -> 16000)
 */
export function resampleAudio(
  inputData: Float32Array,
  inSampleRate: number,
  outSampleRate: number
): Float32Array {
  if (inSampleRate === outSampleRate) {
    return inputData;
  }
  const ratio = inSampleRate / outSampleRate;
  const newLength = Math.round(inputData.length / ratio);
  const result = new Float32Array(newLength);
  let offsetResult = 0;
  let offsetInput = 0;

  while (offsetResult < result.length) {
    const nextOffsetInput = Math.round((offsetResult + 1) * ratio);
    let accum = 0;
    let count = 0;
    for (let i = offsetInput; i < nextOffsetInput && i < inputData.length; i++) {
      accum += inputData[i];
      count++;
    }
    result[offsetResult] = count > 0 ? accum / count : 0;
    offsetResult++;
    offsetInput = nextOffsetInput;
  }
  return result;
}

/**
 * Computes RMS volume level from Float32Array samples, normalized roughly to [0, 1]
 */
export function calculateRMS(samples: Float32Array): number {
  let sum = 0;
  for (let i = 0; i < samples.length; i++) {
    sum += samples[i] * samples[i];
  }
  const rms = Math.sqrt(sum / samples.length);
  return Math.min(1, rms * 5); // boosted for visual sensitivity
}
