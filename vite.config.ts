import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';

export default defineConfig(() => {
  const apiKey = process.env.GEMINI_API_KEY || '';

  return {
    plugins: [
      react(),
      tailwindcss(),
      {
        name: 'api-endpoints',
        configureServer(server) {
          server.middlewares.use('/api/config', (_req, res) => {
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({
              apiKey: apiKey,
              status: 'ok',
            }));
          });
        },
      },
    ],
    define: {
      'process.env.GEMINI_API_KEY': JSON.stringify(apiKey),
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      port: 3000,
      host: '0.0.0.0',
      allowedHosts: true,
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});

