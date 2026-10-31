import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    server: {
      fs: { allow: ['..'] },
      host: '127.0.0.1',
      port: 5175,
      strictPort: true,
      proxy: {
        '/api/v1': {
          target: env.API_PROXY_TARGET || 'http://localhost:5922',
          changeOrigin: true,
          ws: true,
        },
      },
    },
  }
})
