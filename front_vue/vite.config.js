import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const NGROK_HOST = 'thundering-syphilitically-veronica.ngrok-free.dev';
const API_TARGET = 'https://bright-heads-live.loca.lt';

export default defineConfig({
  plugins: [vue()],

  // dev 模式（npm run dev）用
  server: {
    port: 5173,
    host: true,
    allowedHosts: [NGROK_HOST] || localhost,
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
        secure: true, // target 是 https，建议显式写上
      },
    },
  },

  // preview 模式（npm run preview，默认 4173）用
  preview: {
    port: 4173,
    host: true,
    allowedHosts: [NGROK_HOST],
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
        secure: true,
      },
    },
  },
});
