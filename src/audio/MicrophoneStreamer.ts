import { arrayBufferToBase64, calculateRMS, float32ToPcm16, resampleAudio } from './pcmUtils';

/**
 * Captures microphone audio continuously, converts to 16kHz 16-bit PCM Mono Base64,
 * and streams chunks to Gemini Live.
 */
export class MicrophoneStreamer {
  private mediaStream: MediaStream | null = null;
  private audioCtx: AudioContext | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  private processorNode: ScriptProcessorNode | null = null;
  private isRecording = false;
  private onAudioChunk: (base64Chunk: string) => void;
  private onAudioLevel: (level: number) => void;
  private onLog: (tag: string, message: string) => void;

  constructor(options: {
    onAudioChunk: (base64Chunk: string) => void;
    onAudioLevel: (level: number) => void;
    onLog?: (tag: string, message: string) => void;
  }) {
    this.onAudioChunk = options.onAudioChunk;
    this.onAudioLevel = options.onAudioLevel;
    this.onLog = options.onLog || ((tag, msg) => console.log(`[${tag}]`, msg));
  }

  public async start(): Promise<boolean> {
    if (this.isRecording) return true;

    try {
      this.onLog('Microphone', 'Requesting microphone permission...');
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });

      this.onLog('Microphone', 'Microphone permission granted.');

      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.audioCtx = new AudioContextClass();
      if (this.audioCtx.state === 'suspended') {
        await this.audioCtx.resume();
      }

      const actualSampleRate = this.audioCtx.sampleRate;
      this.onLog('Microphone', `Mic AudioContext active (sampleRate=${actualSampleRate})`);

      this.sourceNode = this.audioCtx.createMediaStreamSource(this.mediaStream);

      // Buffer size: 2048 samples (at 44.1k/48k ~ 40ms, at 16k ~ 128ms)
      const bufferSize = 2048;
      this.processorNode = this.audioCtx.createScriptProcessor(bufferSize, 1, 1);

      let chunkCount = 0;
      this.processorNode.onaudioprocess = (e) => {
        if (!this.isRecording) return;

        const inputChannelData = e.inputBuffer.getChannelData(0);

        // Calculate RMS for listening visualizer
        const rms = calculateRMS(inputChannelData);
        this.onAudioLevel(rms);

        // Resample to 16000 Hz if necessary
        const targetRate = 16000;
        const resampledData = resampleAudio(inputChannelData, actualSampleRate, targetRate);

        // Convert Float32 to 16-bit PCM little-endian ArrayBuffer
        const pcmBuffer = float32ToPcm16(resampledData);

        // Convert to Base64
        const base64Chunk = arrayBufferToBase64(pcmBuffer);

        chunkCount++;
        if (chunkCount % 25 === 0) {
          this.onLog('Microphone', `Audio chunk #${chunkCount} sent (${pcmBuffer.byteLength} bytes, level=${rms.toFixed(2)})`);
        }

        this.onAudioChunk(base64Chunk);
      };

      this.sourceNode.connect(this.processorNode);
      // ScriptProcessor requires connection to destination to fire in some browsers
      const muteGain = this.audioCtx.createGain();
      muteGain.gain.value = 0;
      this.processorNode.connect(muteGain);
      muteGain.connect(this.audioCtx.destination);

      this.isRecording = true;
      this.onLog('Microphone', 'Continuous microphone streaming started (16kHz PCM Base64 chunks).');
      return true;
    } catch (err: unknown) {
      this.onLog('Microphone', `Failed to start microphone: ${err instanceof Error ? err.message : String(err)}`);
      this.stop();
      return false;
    }
  }

  public stop(): void {
    if (!this.isRecording && !this.mediaStream) return;
    this.onLog('Microphone', 'Stopping microphone capture...');
    this.isRecording = false;

    if (this.processorNode) {
      this.processorNode.disconnect();
      this.processorNode.onaudioprocess = null;
      this.processorNode = null;
    }

    if (this.sourceNode) {
      this.sourceNode.disconnect();
      this.sourceNode = null;
    }

    if (this.audioCtx && this.audioCtx.state !== 'closed') {
      try {
        this.audioCtx.close();
      } catch {
        // ignore
      }
      this.audioCtx = null;
    }

    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach((track) => track.stop());
      this.mediaStream = null;
    }

    this.onAudioLevel(0);
    this.onLog('Microphone', 'Microphone stopped and cleaned up.');
  }

  public getIsRecording(): boolean {
    return this.isRecording;
  }
}
