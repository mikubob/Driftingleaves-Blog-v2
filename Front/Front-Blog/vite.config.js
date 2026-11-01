import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      dedupe: ['vue'],
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
        '@shared': fileURLToPath(new URL('./src/shared', import.meta.url)),
      },
    },
    server: {
      fs: { allow: ['..'] },
      host: '127.0.0.1',
      port: 5174,
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
