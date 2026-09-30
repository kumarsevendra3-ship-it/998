import React from 'react';
import { Mic, MicOff, RefreshCw, AlertTriangle, Sparkles, Volume2 } from 'lucide-react';

export type AssistantState = 'IDLE' | 'CONNECTING' | 'LISTENING' | 'SPEAKING' | 'ERROR';

interface VisualizerOrbProps {
  state: AssistantState;
  micLevel: number;
  speakerLevel: number;
  onClick: () => void;
}

export const VisualizerOrb: React.FC<VisualizerOrbProps> = ({
  state,
  micLevel,
  speakerLevel,
  onClick,
}) => {
  // Determine scale and color schemes based on state
  const reactiveScale =
    state === 'LISTENING'
      ? 1 + micLevel * 0.35
      : state === 'SPEAKING'
      ? 1 + speakerLevel * 0.45
      : 1;

  const getGradient = () => {
    switch (state) {
      case 'IDLE':
        return 'from-indigo-600 via-purple-600 to-cyan-500 shadow-indigo-500/25';
      case 'CONNECTING':
        return 'from-pink-600 via-purple-600 to-blue-500 shadow-purple-500/30 animate-spin-slow';
      case 'LISTENING':
        return 'from-emerald-500 via-teal-500 to-cyan-500 shadow-emerald-500/30';
      case 'SPEAKING':
        return 'from-pink-500 via-rose-500 to-purple-600 shadow-pink-500/40';
      case 'ERROR':
        return 'from-red-600 via-orange-600 to-amber-700 shadow-red-500/30';
    }
  };

  const getOuterHalo = () => {
    switch (state) {
      case 'IDLE':
        return 'border-indigo-500/20 bg-indigo-500/5';
      case 'CONNECTING':
        return 'border-pink-500/30 bg-pink-500/10 animate-pulse';
      case 'LISTENING':
        return 'border-emerald-500/30 bg-emerald-500/10';
      case 'SPEAKING':
        return 'border-pink-500/40 bg-pink-500/15';
      case 'ERROR':
        return 'border-red-500/30 bg-red-500/10';
    }
  };

  return (
    <div className="relative flex items-center justify-center cursor-pointer select-none" onClick={onClick}>
      {/* Outer Ripple 1 */}
      <div
        className={`absolute rounded-full border transition-all duration-300 ease-out pointer-events-none ${getOuterHalo()}`}
        style={{
          width: '280px',
          height: '280px',
          transform: `scale(${state === 'LISTENING' || state === 'SPEAKING' ? reactiveScale * 1.25 : 1})`,
          opacity: state === 'IDLE' ? 0.3 : 0.8,
        }}
      />

      {/* Mid Wave Ring */}
      <div
        className="absolute rounded-full border border-white/10 pointer-events-none transition-transform duration-200"
        style={{
          width: '240px',
          height: '240px',
          transform: `scale(${state === 'LISTENING' || state === 'SPEAKING' ? reactiveScale * 1.12 : 1})`,
        }}
      />

      {/* Dynamic Sound Wave Bars (shown when speaking or listening) */}
      {(state === 'LISTENING' || state === 'SPEAKING') && (
        <div className="absolute flex items-center justify-center gap-1.5 pointer-events-none -bottom-8">
          {[0.8, 1.4, 2.1, 1.2, 0.7].map((factor, idx) => {
            const level = state === 'LISTENING' ? micLevel : speakerLevel;
            const height = Math.max(6, Math.min(32, level * 40 * factor));
            return (
              <span
                key={idx}
                className={`w-1 rounded-full transition-all duration-100 ${
                  state === 'SPEAKING' ? 'bg-pink-400 shadow-sm shadow-pink-500' : 'bg-emerald-400 shadow-sm shadow-emerald-500'
                }`}
                style={{ height: `${height}px` }}
              />
            );
          })}
        </div>
      )}

      {/* Main Core Glowing Orb */}
      <div
        className={`relative z-10 w-44 h-44 rounded-full bg-gradient-to-br ${getGradient()} p-1 shadow-2xl flex items-center justify-center transition-transform duration-150 active:scale-95`}
        style={{
          transform: `scale(${reactiveScale})`,
        }}
      >
        <div className="w-full h-full rounded-full bg-slate-950/40 backdrop-blur-sm flex flex-col items-center justify-center border border-white/20">
          {state === 'IDLE' && (
            <div className="flex flex-col items-center text-white/90">
              <Mic className="w-10 h-10 text-white drop-shadow" />
              <span className="text-[11px] font-semibold tracking-wider uppercase mt-1 text-white/70">
                Tap to talk
              </span>
            </div>
          )}

          {state === 'CONNECTING' && (
            <div className="flex flex-col items-center text-white">
              <RefreshCw className="w-10 h-10 animate-spin text-pink-300" />
              <span className="text-[11px] font-semibold tracking-wider uppercase mt-1 text-pink-200">
                Connecting
              </span>
            </div>
          )}

          {state === 'LISTENING' && (
            <div className="flex flex-col items-center text-emerald-200">
              <Mic className="w-11 h-11 text-emerald-300 drop-shadow animate-pulse" />
              <span className="text-[11px] font-semibold tracking-wider uppercase mt-1 text-emerald-200">
                Listening...
              </span>
            </div>
          )}

          {state === 'SPEAKING' && (
            <div className="flex flex-col items-center text-pink-100">
              <Volume2 className="w-11 h-11 text-pink-300 drop-shadow animate-bounce" />
              <span className="text-[11px] font-semibold tracking-wider uppercase mt-1 text-pink-200">
                Arushi Speaking
              </span>
            </div>
          )}

          {state === 'ERROR' && (
            <div className="flex flex-col items-center text-red-200">
              <AlertTriangle className="w-10 h-10 text-red-300" />
              <span className="text-[11px] font-semibold tracking-wider uppercase mt-1 text-red-200">
                Tap to Retry
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
