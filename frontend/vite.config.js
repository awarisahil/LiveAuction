import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // sockjs-client is CommonJS and references Node's `global` at runtime;
  // map it to globalThis so it works in the browser (dev and build).
  define: { global: 'globalThis' },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
});
