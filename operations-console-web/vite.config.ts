import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api/operations': {
          target: env.OPERATIONS_GATEWAY_URL || 'http://127.0.0.1:18080',
          changeOrigin: false,
        },
      },
    },
    build: { sourcemap: false },
  };
});
