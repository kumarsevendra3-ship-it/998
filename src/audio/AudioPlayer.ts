import { base64ToArrayBuffer, pcm16ToFloat32 } from './pcmUtils';

/**
 * Manages audio output to device speakers via Web Audio API.
 * Features:
 * - Persistent AudioContext with autoplay unlock
 * - Sequential, gapless queue scheduling for streaming 24kHz PCM chunks
 * - Immediate interruption handling
 * - Speaker diagnostic test (440Hz tone)
 * - Real-time output level feedback
 */
export class AudioPlayer {
  private audioCtx: AudioContext | null = null;
  private gainNode: GainNode | null = null;
  private nextPlayTime = 0;
  private activeSources: AudioBufferSourceNode[] = [];
  private isPlaying = false;
  private onPlaybackStateChange?: (isPlaying: boolean) => void;
  private onAudioLevel?: (level: number) => void;
  private onLog: (tag: string, message: string) => void;
  private checkEndTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor(options: {
    onPlaybackStateChange?: (isPlaying: boolean) => void;
    onAudioLevel?: (level: number) => void;
    onLog?: (tag: string, message: string) => void;
  }) {
    this.onPlaybackStateChange = options.onPlaybackStateChange;
    this.onAudioLevel = options.onAudioLevel;
    this.onLog = options.onLog || ((tag, msg) => console.log(`[${tag}]`, msg));
  }

  /**
   * Initializes or resumes the single persistent AudioContext.
   * MUST be triggered by genuine user gesture (e.g. mic click).
   */
  public async ensureContext(): Promise<AudioContext> {
    if (!this.audioCtx || this.audioCtx.state === 'closed') {
      const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.audioCtx = new AudioContextClass({ sampleRate: 24000 });
      this.onLog('AudioPlayer', `AudioContext created (sampleRate=${this.audioCtx.sampleRate}, state=${this.audioCtx.state})`);

      this.gainNode = this.audioCtx.createGain();
      this.gainNode.gain.setValueAtTime(1.0, this.audioCtx.currentTime);
      this.gainNode.connect(this.audioCtx.destination);
      this.onLog('AudioPlayer', 'Audio graph connected: Source -> GainNode(1.0) -> Destination');
    }

    if (this.audioCtx.state === 'suspended') {
      this.onLog('AudioPlayer', 'AudioContext suspended. Resuming...');
      await this.audioCtx.resume();
      this.onLog('AudioPlayer', `AudioContext resumed. State: ${this.audioCtx.state}`);
    }

    return this.audioCtx;
  }

  /**
   * Decodes Base64 24kHz PCM from Gemini Live and schedules sequential playback.
   */
  public async playChunk(base64Chunk: string, sampleRate = 24000): Promise<void> {
    try {
      const ctx = await this.ensureContext();

      const pcmBytes = base64ToArrayBuffer(base64Chunk);
      if (pcmBytes.byteLength === 0) return;

      const float32Data = pcm16ToFloat32(pcmBytes);
      if (float32Data.length === 0) return;

      const audioBuffer = ctx.createBuffer(1, float32Data.length, sampleRate);
      audioBuffer.copyToChannel(float32Data, 0);

      const source = ctx.createBufferSource();
      source.buffer = audioBuffer;
      source.connect(this.gainNode!);

      // Calculate sequential playback start time
      const currentTime = ctx.currentTime;
      const startTime = Math.max(currentTime, this.nextPlayTime);
      this.nextPlayTime = startTime + audioBuffer.duration;

      source.start(startTime);
      this.activeSources.push(source);

      if (!this.isPlaying) {
        this.isPlaying = true;
        this.onPlaybackStateChange?.(true);
        this.onLog('AudioPlayer', `Audio playback started at ${startTime.toFixed(3)}s`);
      }

      // Simple amplitude estimation for visualizer
      let sum = 0;
      for (let i = 0; i < Math.min(float32Data.length, 512); i += 8) {
        sum += Math.abs(float32Data[i]);
      }
      const avgAmp = Math.min(1, (sum / 64) * 4);
      this.onAudioLevel?.(avgAmp);

      source.onended = () => {
        const index = this.activeSources.indexOf(source);
        if (index > -1) {
          this.activeSources.splice(index, 1);
        }
        if (this.activeSources.length === 0) {
          if (this.checkEndTimeout) clearTimeout(this.checkEndTimeout);
          this.checkEndTimeout = setTimeout(() => {
            if (this.activeSources.length === 0 && this.isPlaying) {
              this.isPlaying = false;
              this.onPlaybackStateChange?.(false);
              this.onAudioLevel?.(0);
              this.onLog('AudioPlayer', 'Audio playback queue drained. State returned to listening/idle.');
            }
          }, 150);
        }
      };

      this.onLog('AudioPlayer', `Chunk scheduled: ${float32Data.length} samples (${(audioBuffer.duration * 1000).toFixed(0)}ms)`);
    } catch (err: unknown) {
      this.onLog('AudioPlayer', `Error decoding/scheduling audio chunk: ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  /**
   * Interruption: Instantly stops all currently scheduled and playing audio.
   */
  public interrupt(): void {
    this.onLog('AudioPlayer', 'Audio interrupted! Stopping all active sources & clearing schedule.');
    for (const source of this.activeSources) {
      try {
        source.stop();
        source.disconnect();
      } catch {
        // Source might have already finished
      }
    }
    this.activeSources = [];
    if (this.audioCtx) {
      this.nextPlayTime = this.audioCtx.currentTime;
    }
    if (this.isPlaying) {
      this.isPlaying = false;
      this.onPlaybackStateChange?.(false);
      this.onAudioLevel?.(0);
    }
  }

  /**
   * Requirement 15: Speaker Diagnostic Test
   * Generates a short 440Hz sine wave tone using the SAME AudioContext and GainNode.
   */
  public async playSpeakerTestTone(freq = 440, duration = 1.0): Promise<void> {
    this.onLog('AudioPlayer', `Speaker Diagnostic Test: Playing ${freq}Hz tone for ${duration}s...`);
    const ctx = await this.ensureContext();

    const sampleRate = ctx.sampleRate;
    const numSamples = Math.floor(sampleRate * duration);
    const audioBuffer = ctx.createBuffer(1, numSamples, sampleRate);
    const channelData = audioBuffer.getChannelData(0);

    for (let i = 0; i < numSamples; i++) {
      const t = i / sampleRate;
      // Soft envelope to prevent clicks
      let envelope = 1.0;
      if (t < 0.05) envelope = t / 0.05;
      else if (t > duration - 0.05) envelope = (duration - t) / 0.05;
      channelData[i] = Math.sin(2 * Math.PI * freq * t) * 0.3 * envelope;
    }

    const source = ctx.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(this.gainNode!);
    source.start(ctx.currentTime);
    this.onLog('AudioPlayer', 'Speaker diagnostic tone started on device speakers.');
  }

  public getContextState(): string {
    return this.audioCtx ? this.audioCtx.state : 'uninitialized';
  }

  public stop(): void {
    this.interrupt();
    if (this.audioCtx && this.audioCtx.state !== 'closed') {
      try {
        this.audioCtx.close();
      } catch {
        // ignore
      }
      this.audioCtx = null;
    }
  }
}
