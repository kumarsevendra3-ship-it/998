import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Sparkles, Terminal, Volume2, Mic, MicOff, RefreshCw, AlertCircle } from 'lucide-react';
import { AudioPlayer } from './audio/AudioPlayer';
import { MicrophoneStreamer } from './audio/MicrophoneStreamer';
import { GeminiLiveSession } from './audio/GeminiLiveSession';
import { VisualizerOrb, AssistantState } from './components/VisualizerOrb';
import { TranscriptView, ChatMessage } from './components/TranscriptView';
import { DebugLogsModal } from './components/DebugLogsModal';

export default function App() {
  const [state, setState] = useState<AssistantState>('IDLE');
  const [micLevel, setMicLevel] = useState<number>(0);
  const [speakerLevel, setSpeakerLevel] = useState<number>(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [activeAction, setActiveAction] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [logs, setLogs] = useState<string[]>([]);
  const [isLogsOpen, setIsLogsOpen] = useState<boolean>(false);
  const [apiKey, setApiKey] = useState<string>('');

  const audioPlayerRef = useRef<AudioPlayer | null>(null);
  const micStreamerRef = useRef<MicrophoneStreamer | null>(null);
  const liveSessionRef = useRef<GeminiLiveSession | null>(null);

  const addLog = useCallback((tag: string, message: string) => {
    const time = new Date().toLocaleTimeString();
    const entry = `[${time}] [${tag}] ${message}`;
    console.log(entry);
    setLogs((prev) => [...prev.slice(-150), entry]);
  }, []);

  const addMessage = useCallback((sender: 'You' | 'Arushi' | 'Action', text: string) => {
    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    setMessages((prev) => [...prev, { id: Math.random().toString(36).substring(7), sender, text, timestamp: time }]);
  }, []);

  // Fetch or retrieve API key
  useEffect(() => {
    const envKey = (process.env as unknown as { GEMINI_API_KEY?: string })?.GEMINI_API_KEY || '';
    if (envKey && envKey !== 'MY_GEMINI_API_KEY') {
      setApiKey(envKey);
      addLog('System', 'Gemini API key loaded from environment');
      return;
    }

    fetch('/api/config')
      .then((res) => res.json())
      .then((data) => {
        if (data.apiKey && data.apiKey !== 'MY_GEMINI_API_KEY') {
          setApiKey(data.apiKey);
          addLog('System', 'Gemini API key retrieved from /api/config');
        } else {
          addLog('System', 'No API key provided yet. Configure GEMINI_API_KEY in Secrets.');
        }
      })
      .catch(() => {
        addLog('System', 'Running client-side configuration.');
      });
  }, [addLog]);

  // Initialize AudioPlayer
  useEffect(() => {
    const player = new AudioPlayer({
      onPlaybackStateChange: (isPlaying) => {
        if (isPlaying) {
          setState('SPEAKING');
        } else {
          if (liveSessionRef.current?.getIsConnected()) {
            setState('LISTENING');
          } else {
            setState('IDLE');
          }
        }
      },
      onAudioLevel: (lvl) => setSpeakerLevel(lvl),
      onLog: addLog,
    });
    audioPlayerRef.current = player;

    return () => {
      player.stop();
    };
  }, [addLog]);

  // Clean shutdown
  const stopAll = useCallback(() => {
    addLog('App', 'Stopping all active audio & live sessions');
    micStreamerRef.current?.stop();
    audioPlayerRef.current?.interrupt();
    liveSessionRef.current?.disconnect();
    liveSessionRef.current = null;
    setState('IDLE');
    setMicLevel(0);
    setSpeakerLevel(0);
    setActiveAction(null);
  }, [addLog]);

  // Start Assistant session
  const startAssistant = useCallback(async () => {
    setErrorMessage(null);
    setState('CONNECTING');
    addLog('App', 'User requested to start Arushi session');

    // 1. Initialize persistent AudioContext on user gesture
    if (!audioPlayerRef.current) return;
    try {
      await audioPlayerRef.current.ensureContext();
      addLog('AudioContext', `Output AudioContext ready (state=${audioPlayerRef.current.getContextState()})`);
    } catch (err: unknown) {
      const msg = `AudioContext initialization failed: ${err instanceof Error ? err.message : String(err)}`;
      addLog('AudioContext', msg);
      setErrorMessage(msg);
      setState('ERROR');
      return;
    }

    const keyToUse = apiKey || (process.env as unknown as { GEMINI_API_KEY?: string })?.GEMINI_API_KEY || '';
    if (!keyToUse || keyToUse === 'MY_GEMINI_API_KEY') {
      const err = 'Gemini API key is missing. Set GEMINI_API_KEY in the Secrets panel.';
      setErrorMessage(err);
      addLog('App', err);
      setState('ERROR');
      return;
    }

    // 2. Create Gemini Live Session
    const session = new GeminiLiveSession({
      apiKey: keyToUse,
      voiceName: 'Aoede',
      onConnected: async () => {
        addLog('LiveSession', 'Connected to Gemini Live. Starting microphone...');
        setState('LISTENING');

        // 3. Start microphone capture
        const mic = new MicrophoneStreamer({
          onAudioChunk: (base64Chunk) => {
            session.sendAudioChunk(base64Chunk);
          },
          onAudioLevel: (lvl) => {
            setMicLevel(lvl);
          },
          onLog: addLog,
        });
        micStreamerRef.current = mic;

        const micStarted = await mic.start();
        if (!micStarted) {
          setState('ERROR');
          setErrorMessage('Could not access microphone. Please grant permission.');
        }
      },
      onDisconnected: (reason) => {
        addLog('LiveSession', `Disconnected: ${reason}`);
        micStreamerRef.current?.stop();
        setState((curr) => (curr !== 'IDLE' ? 'ERROR' : 'IDLE'));
        if (reason) setErrorMessage(reason);
      },
      onAudioData: (base64Audio) => {
        // Enqueue 24kHz PCM chunk to AudioPlayer
        audioPlayerRef.current?.playChunk(base64Audio, 24000);
      },
      onTranscript: (text, isUser) => {
        addMessage(isUser ? 'You' : 'Arushi', text);
      },
      onInterrupted: () => {
        addLog('LiveSession', 'Interruption received: Halting current audio');
        audioPlayerRef.current?.interrupt();
        setState('LISTENING');
      },
      onTurnComplete: () => {
        addLog('LiveSession', 'Turn complete');
      },
      onActionFeedback: (actionMsg) => {
        setActiveAction(actionMsg);
      },
      onLog: addLog,
    });

    liveSessionRef.current = session;
    session.connect();
  }, [apiKey, addLog, addMessage]);

  const toggleAssistant = useCallback(() => {
    if (state === 'IDLE' || state === 'ERROR') {
      startAssistant();
    } else {
      stopAll();
    }
  }, [state, startAssistant, stopAll]);

  // Requirement 15: Speaker Diagnostic Test
  const handleTestSpeaker = useCallback(async () => {
    addLog('Diagnostic', 'Running Speaker Diagnostic Test');
    try {
      await audioPlayerRef.current?.playSpeakerTestTone(440, 1.0);
    } catch (err: unknown) {
      addLog('Diagnostic', `Speaker test failed: ${err instanceof Error ? err.message : String(err)}`);
    }
  }, [addLog]);

  return (
    <div className="flex flex-col h-screen w-full max-w-md mx-auto bg-[#080914] text-white relative overflow-hidden select-none border-x border-white/5">
      {/* Top Header */}
      <header className="flex items-center justify-between px-5 py-4 border-b border-white/5 bg-slate-950/40 backdrop-blur-md z-20">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-2xl bg-gradient-to-tr from-purple-600 to-pink-500 flex items-center justify-center shadow-lg shadow-purple-500/20">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-extrabold text-base tracking-wide text-white">Arushi</h1>
              <span className="px-1.5 py-0.5 rounded-full text-[9px] font-bold tracking-wider uppercase bg-purple-500/20 text-purple-300 border border-purple-500/30">
                LIVE
              </span>
            </div>
            <p className="text-[10px] text-slate-400 font-medium">
              English • हिन्दी • Hinglish • Multi-Lingual
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <button
            onClick={() => setIsLogsOpen(true)}
            className="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-slate-300 transition border border-white/10"
            title="Inspect Live Audio Logs"
          >
            <Terminal className="w-4 h-4 text-sky-400" />
          </button>
        </div>
      </header>

      {/* Status Bar */}
      <div className="px-5 py-2 z-10">
        {state === 'ERROR' && errorMessage ? (
          <div
            onClick={toggleAssistant}
            className="flex items-center justify-center gap-2 px-3 py-1.5 rounded-full bg-red-950/60 border border-red-500/30 text-red-300 text-xs font-medium cursor-pointer"
          >
            <AlertCircle className="w-3.5 h-3.5" />
            <span className="truncate">{errorMessage} (Tap to retry)</span>
          </div>
        ) : (
          <div className="flex items-center justify-center gap-2 text-xs font-medium text-slate-400">
            <span
              className={`w-2 h-2 rounded-full ${
                state === 'LISTENING'
                  ? 'bg-emerald-400 animate-ping'
                  : state === 'SPEAKING'
                  ? 'bg-pink-400 animate-pulse'
                  : state === 'CONNECTING'
                  ? 'bg-amber-400 animate-spin'
                  : 'bg-slate-600'
              }`}
            />
            <span>
              {state === 'IDLE' && 'Tap the orb or mic button to talk'}
              {state === 'CONNECTING' && 'Connecting to Gemini Live...'}
              {state === 'LISTENING' && 'Listening... Speak in any language'}
              {state === 'SPEAKING' && 'Arushi is speaking...'}
            </span>
          </div>
        )}
      </div>

      {/* Visualizer Orb Hero Area */}
      <div className="py-6 flex items-center justify-center z-10">
        <VisualizerOrb
          state={state}
          micLevel={micLevel}
          speakerLevel={speakerLevel}
          onClick={toggleAssistant}
        />
      </div>

      {/* Suggested Voice Prompts */}
      <div className="px-4 py-1 flex items-center gap-2 overflow-x-auto no-scrollbar z-10">
        {['Kya haal hai Arushi?', 'Open WhatsApp', 'Tell me a joke', 'Hindi mein bolo', 'Open YouTube'].map(
          (chip) => (
            <button
              key={chip}
              onClick={() => {
                if (state === 'IDLE') toggleAssistant();
              }}
              className="px-3 py-1.5 rounded-full text-xs bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 whitespace-nowrap transition active:scale-95"
            >
              {chip}
            </button>
          )
        )}
      </div>

      {/* Live Transcript / Activity View */}
      <TranscriptView messages={messages} activeAction={activeAction} />

      {/* Bottom Control Bar */}
      <footer className="px-5 py-4 bg-[#0a0d1e] border-t border-white/5 flex items-center justify-between z-20">
        {/* Requirement 15: Speaker Test Button */}
        <button
          onClick={handleTestSpeaker}
          className="flex items-center gap-2 px-3 py-2 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-semibold text-sky-300 transition active:scale-95"
        >
          <Volume2 className="w-4 h-4 text-sky-400" />
          <span>Test Speaker</span>
        </button>

        {/* Big Mic Toggle Button */}
        <button
          onClick={toggleAssistant}
          className={`w-14 h-14 rounded-full flex items-center justify-center shadow-xl transition-all duration-200 active:scale-90 ${
            state === 'LISTENING' || state === 'SPEAKING'
              ? 'bg-red-500 hover:bg-red-600 shadow-red-500/30'
              : 'bg-indigo-600 hover:bg-indigo-500 shadow-indigo-600/30'
          }`}
        >
          {state === 'CONNECTING' ? (
            <RefreshCw className="w-6 h-6 animate-spin text-white" />
          ) : state === 'LISTENING' || state === 'SPEAKING' ? (
            <MicOff className="w-6 h-6 text-white" />
          ) : (
            <Mic className="w-6 h-6 text-white" />
          )}
        </button>

        {/* Logs Button */}
        <button
          onClick={() => setIsLogsOpen(true)}
          className="flex items-center gap-2 px-3 py-2 rounded-2xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-semibold text-purple-300 transition active:scale-95"
        >
          <Terminal className="w-4 h-4 text-purple-400" />
          <span>Logs</span>
        </button>
      </footer>

      {/* Diagnostics Modal */}
      <DebugLogsModal isOpen={isLogsOpen} onClose={() => setIsLogsOpen(false)} logs={logs} />
    </div>
  );
}
