import React, { useEffect, useRef } from 'react';
import { User, Sparkles, CheckCircle2, ArrowUpRight } from 'lucide-react';

export interface ChatMessage {
  id: string;
  sender: 'You' | 'Arushi' | 'Action';
  text: string;
  timestamp: string;
}

interface TranscriptViewProps {
  messages: ChatMessage[];
  activeAction: string | null;
}

export const TranscriptView: React.FC<TranscriptViewProps> = ({ messages, activeAction }) => {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, activeAction]);

  return (
    <div className="flex-1 overflow-y-auto px-4 py-2 space-y-3">
      {/* Active Action Alert Banner */}
      {activeAction && (
        <div className="flex items-center gap-3 p-3 rounded-2xl bg-indigo-950/70 border border-indigo-500/30 text-indigo-200 animate-pulse">
          <div className="w-8 h-8 rounded-full bg-indigo-600/30 flex items-center justify-center shrink-0">
            <ArrowUpRight className="w-4 h-4 text-indigo-300" />
          </div>
          <div>
            <div className="text-[10px] font-bold uppercase tracking-wider text-indigo-400">
              Executing Device Action
            </div>
            <div className="text-sm font-medium text-white">{activeAction}</div>
          </div>
        </div>
      )}

      {messages.length === 0 && !activeAction ? (
        <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-400">
          <div className="w-12 h-12 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center mb-3">
            <Sparkles className="w-6 h-6 text-purple-400" />
          </div>
          <p className="text-sm font-medium text-slate-300">Talk to Arushi naturally</p>
          <p className="text-xs text-slate-500 mt-1 max-w-xs">
            She speaks English, Hindi, Hinglish, and can open apps, make calls, or open links.
          </p>
        </div>
      ) : (
        messages.map((msg) => {
          const isArushi = msg.sender === 'Arushi';
          const isAction = msg.sender === 'Action';

          return (
            <div
              key={msg.id}
              className={`flex flex-col ${isArushi || isAction ? 'items-start' : 'items-end'}`}
            >
              <div
                className={`max-w-[88%] rounded-2xl p-3.5 shadow-sm text-sm ${
                  isAction
                    ? 'bg-emerald-950/60 border border-emerald-500/30 text-emerald-200'
                    : isArushi
                    ? 'bg-purple-950/50 border border-purple-500/20 text-slate-100 rounded-tl-sm'
                    : 'bg-indigo-600/30 border border-indigo-400/30 text-slate-100 rounded-tr-sm'
                }`}
              >
                <div className="flex items-center justify-between gap-3 mb-1 text-[11px] font-semibold">
                  <span
                    className={`flex items-center gap-1.5 ${
                      isAction
                        ? 'text-emerald-400'
                        : isArushi
                        ? 'text-pink-400'
                        : 'text-indigo-300'
                    }`}
                  >
                    {isAction ? (
                      <CheckCircle2 className="w-3.5 h-3.5" />
                    ) : isArushi ? (
                      <Sparkles className="w-3.5 h-3.5" />
                    ) : (
                      <User className="w-3.5 h-3.5" />
                    )}
                    {msg.sender}
                  </span>
                  <span className="text-[10px] text-slate-500 font-normal">{msg.timestamp}</span>
                </div>
                <div className="leading-relaxed whitespace-pre-wrap">{msg.text}</div>
              </div>
            </div>
          );
        })
      )}
      <div ref={bottomRef} />
    </div>
  );
};
