import React, { useRef, useEffect } from 'react';
import { Terminal, X, Copy, Check } from 'lucide-react';

interface DebugLogsModalProps {
  isOpen: boolean;
  onClose: () => void;
  logs: string[];
}

export const DebugLogsModal: React.FC<DebugLogsModalProps> = ({ isOpen, onClose, logs }) => {
  const [copied, setCopied] = React.useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isOpen) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, isOpen]);

  if (!isOpen) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(logs.join('\n'));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div className="w-full max-w-2xl h-[85vh] bg-[#0c1021] border border-white/10 rounded-3xl flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-white/10 bg-white/5">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-sky-500/20 text-sky-400 flex items-center justify-center">
              <Terminal className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-white">Audio Pipeline & Live Logs</h2>
              <p className="text-[11px] text-slate-400">
                Detailed inspection of AudioContext, PCM chunks, WebSocket & tools
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs text-slate-300 font-medium transition"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? 'Copied' : 'Copy'}
            </button>
            <button
              onClick={onClose}
              className="p-1.5 rounded-xl hover:bg-white/10 text-slate-400 hover:text-white transition"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Log Viewer */}
        <div className="flex-1 p-4 overflow-y-auto bg-black/40 font-mono text-[11px] leading-relaxed space-y-1.5">
          {logs.length === 0 ? (
            <div className="text-slate-500 text-center py-10">No logs generated yet.</div>
          ) : (
            logs.map((log, idx) => {
              const isError = log.includes('error') || log.includes('Error') || log.includes('fail');
              const isSuccess = log.includes('connected') || log.includes('started') || log.includes('granted');
              const isInterrupted = log.includes('interrupted') || log.includes('Interrupted');
              const isAction = log.includes('Tool') || log.includes('Action') || log.includes('Executing');

              return (
                <div
                  key={idx}
                  className={`break-all py-0.5 px-1.5 rounded ${
                    isError
                      ? 'text-red-400 bg-red-950/30'
                      : isSuccess
                      ? 'text-emerald-300 bg-emerald-950/20'
                      : isInterrupted
                      ? 'text-amber-300 bg-amber-950/20'
                      : isAction
                      ? 'text-purple-300 bg-purple-950/20'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {log}
                </div>
              );
            })
          )}
          <div ref={bottomRef} />
        </div>
      </div>
    </div>
  );
};
