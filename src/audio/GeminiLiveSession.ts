import { ActionResult, executeDeviceAction } from '../actions/deviceActions';

export interface GeminiLiveOptions {
  apiKey: string;
  voiceName?: string;
  model?: string;
  onConnected?: () => void;
  onDisconnected?: (reason: string) => void;
  onAudioData?: (base64Audio: string) => void;
  onTranscript?: (text: string, isUser: boolean) => void;
  onInterrupted?: () => void;
  onTurnComplete?: () => void;
  onActionFeedback?: (actionMsg: string | null) => void;
  onLog?: (tag: string, message: string) => void;
}

export class GeminiLiveSession {
  private ws: WebSocket | null = null;
  private apiKey: string;
  private voiceName: string;
  private model: string;
  private isConnected = false;
  private options: GeminiLiveOptions;
  private onLog: (tag: string, message: string) => void;

  constructor(options: GeminiLiveOptions) {
    this.options = options;
    this.apiKey = options.apiKey;
    this.voiceName = options.voiceName || 'Aoede';
    this.model = options.model || 'models/gemini-2.0-flash-exp';
    this.onLog = options.onLog || ((tag, msg) => console.log(`[${tag}]`, msg));
  }

  public connect(): void {
    if (this.isConnected || this.ws) {
      this.onLog('LiveSession', 'Session already active');
      return;
    }

    if (!this.apiKey || this.apiKey === 'MY_GEMINI_API_KEY') {
      const err = 'Missing valid Gemini API key. Ensure GEMINI_API_KEY is configured in .env or Secrets.';
      this.onLog('LiveSession', err);
      this.options.onDisconnected?.(err);
      return;
    }

    const host = 'generativelanguage.googleapis.com';
    const path = '/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent';
    const url = `wss://${host}${path}?key=${this.apiKey}`;

    this.onLog('LiveSession', `Connecting to Gemini Live WebSocket: ${host} (model=${this.model}, voice=${this.voiceName})`);

    try {
      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        this.isConnected = true;
        this.onLog('LiveSession', 'Gemini Live WebSocket connected! Sending setup payload...');
        this.sendSetup();
        this.options.onConnected?.();
      };

      this.ws.onmessage = async (event: MessageEvent) => {
        try {
          let text = '';
          if (typeof event.data === 'string') {
            text = event.data;
          } else if (event.data instanceof Blob) {
            text = await event.data.text();
          } else if (event.data instanceof ArrayBuffer) {
            text = new TextDecoder().decode(event.data);
          }

          if (text) {
            this.handleMessage(text);
          }
        } catch (err: unknown) {
          this.onLog('LiveSession', `Error parsing message: ${err instanceof Error ? err.message : String(err)}`);
        }
      };

      this.ws.onclose = (event: CloseEvent) => {
        this.isConnected = false;
        this.ws = null;
        this.onLog('LiveSession', `Gemini Live session closed (code=${event.code}, reason=${event.reason || 'None'})`);
        this.options.onDisconnected?.(event.reason || `Session closed (code ${event.code})`);
      };

      this.ws.onerror = (event: Event) => {
        this.onLog('LiveSession', 'WebSocket error encountered.');
        console.error('Gemini Live WS Error:', event);
      };
    } catch (err: unknown) {
      this.onLog('LiveSession', `Failed to open WebSocket: ${err instanceof Error ? err.message : String(err)}`);
      this.options.onDisconnected?.(err instanceof Error ? err.message : String(err));
    }
  }

  private sendSetup(): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const setupPayload = {
      setup: {
        model: this.model,
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: {
                voiceName: this.voiceName,
              },
            },
          },
        },
        systemInstruction: {
          parts: [
            {
              text:
                'You are Arushi, a young, confident, witty, playful, and emotionally responsive virtual assistant. ' +
                'Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. ' +
                'Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user\'s emotions and conversation. ' +
                'Automatically understand and respond in the language the user is speaking, including Hindi, English, Hinglish, Marathi, ' +
                'Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Urdu, and other languages. ' +
                'Keep responses natural, engaging, and concise enough for real-time voice conversation. ' +
                'You can execute safe supported device actions through available tools: openWhatsApp, openApp, openUrl, makeCall, and callContact. ' +
                'Never claim that an action was completed unless the application actually executed it. ' +
                'Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality.',
            },
          ],
        },
        tools: [
          {
            functionDeclarations: [
              {
                name: 'openWhatsApp',
                description:
                  'Opens WhatsApp application or chat when requested by the user in any language (e.g. "Open WhatsApp", "WhatsApp kholo", "WhatsApp open karo").',
                parameters: {
                  type: 'OBJECT',
                  properties: {},
                },
              },
              {
                name: 'openApp',
                description:
                  'Opens a supported application such as YouTube, Instagram, WhatsApp, Maps, Chrome, Calculator, or Calendar.',
                parameters: {
                  type: 'OBJECT',
                  properties: {
                    appName: {
                      type: 'STRING',
                      description: 'The name of the app to open (e.g. YouTube, Instagram, Maps, Chrome, Calculator, Calendar)',
                    },
                  },
                  required: ['appName'],
                },
              },
              {
                name: 'openUrl',
                description: 'Opens a validated web URL in the user\'s browser.',
                parameters: {
                  type: 'OBJECT',
                  properties: {
                    url: {
                      type: 'STRING',
                      description: 'The full web URL to open starting with https://',
                    },
                  },
                  required: ['url'],
                },
              },
              {
                name: 'makeCall',
                description: 'Opens the phone dialer with a specified phone number.',
                parameters: {
                  type: 'OBJECT',
                  properties: {
                    phoneNumber: {
                      type: 'STRING',
                      description: 'The phone number to dial',
                    },
                  },
                  required: ['phoneNumber'],
                },
              },
              {
                name: 'callContact',
                description: 'Initiates a phone call to a named contact (e.g. "Mom", "Mummy", "Dad", "Rahul").',
                parameters: {
                  type: 'OBJECT',
                  properties: {
                    contactName: {
                      type: 'STRING',
                      description: 'The contact name to search for and call',
                    },
                  },
                  required: ['contactName'],
                },
              },
            ],
          },
        ],
      },
    };

    this.onLog('LiveSession', 'Sending Gemini Live Setup message with 5 safe device tools');
    this.ws.send(JSON.stringify(setupPayload));
  }

  public sendAudioChunk(base64Chunk: string): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const payload = {
      realtimeInput: {
        mediaChunks: [
          {
            mimeType: 'audio/pcm;rate=16000',
            data: base64Chunk,
          },
        ],
      },
    };

    try {
      this.ws.send(JSON.stringify(payload));
    } catch (err: unknown) {
      this.onLog('LiveSession', `Error sending audio chunk: ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  private async handleMessage(jsonText: string): Promise<void> {
    try {
      const data = JSON.parse(jsonText);

      // 1. Server Content
      if (data.serverContent) {
        const sc = data.serverContent;

        // User interruption signal
        if (sc.interrupted) {
          this.onLog('LiveSession', 'Server signal: User interrupted Arushi');
          this.options.onInterrupted?.();
        }

        // Model audio/text parts
        if (sc.modelTurn && Array.isArray(sc.modelTurn.parts)) {
          for (const part of sc.modelTurn.parts) {
            // Audio inline data
            if (part.inlineData && part.inlineData.data) {
              const mime = part.inlineData.mimeType || 'audio/pcm';
              this.onLog('LiveSession', `Received native audio from Gemini (${mime}, length=${part.inlineData.data.length})`);
              this.options.onAudioData?.(part.inlineData.data);
            }

            // Transcript text
            if (part.text) {
              this.onLog('LiveSession', `Received transcript: "${part.text}"`);
              this.options.onTranscript?.(part.text, false);
            }
          }
        }

        if (sc.turnComplete) {
          this.onLog('LiveSession', 'Gemini turn complete');
          this.options.onTurnComplete?.();
        }
      }

      // 2. Tool Calls
      if (data.toolCall && Array.isArray(data.toolCall.functionCalls)) {
        for (const call of data.toolCall.functionCalls) {
          const { id, name, args = {} } = call;
          this.onLog('LiveSession', `Received tool call: ${name} (id=${id}) with args: ${JSON.stringify(args)}`);

          const actionFeedback = this.getActionSummary(name, args);
          this.options.onActionFeedback?.(actionFeedback);
          this.options.onTranscript?.(`[Executing: ${actionFeedback}]`, false);

          const result: ActionResult = await executeDeviceAction(name, args);

          this.onLog('LiveSession', `Tool ${name} executed -> success=${result.success}: ${result.message || result.error}`);
          this.sendToolResponse(id, result);
          this.options.onActionFeedback?.(null);
        }
      }
    } catch (err: unknown) {
      this.onLog('LiveSession', `Error parsing message JSON: ${err instanceof Error ? err.message : String(err)}`);
    }
  }

  private sendToolResponse(callId: string, output: ActionResult): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const payload = {
      toolResponse: {
        functionResponses: [
          {
            id: callId,
            response: {
              output: output,
            },
          },
        ],
      },
    };

    this.onLog('LiveSession', `Sending tool response for call ${callId}: ${JSON.stringify(output)}`);
    this.ws.send(JSON.stringify(payload));
  }

  private getActionSummary(name: string, args: Record<string, unknown>): string {
    switch (name) {
      case 'openWhatsApp':
        return 'Opening WhatsApp...';
      case 'openApp':
        return `Opening ${args.appName || 'application'}...`;
      case 'openUrl':
        return `Opening link ${args.url || ''}...`;
      case 'makeCall':
        return `Calling ${args.phoneNumber || ''}...`;
      case 'callContact':
        return `Calling contact ${args.contactName || ''}...`;
      default:
        return `Executing ${name}...`;
    }
  }

  public disconnect(): void {
    if (this.ws) {
      this.onLog('LiveSession', 'Closing Gemini Live WebSocket...');
      this.isConnected = false;
      try {
        this.ws.close(1000, 'User stopped session');
      } catch {
        // ignore
      }
      this.ws = null;
    }
  }

  public getIsConnected(): boolean {
    return this.isConnected;
  }
}
