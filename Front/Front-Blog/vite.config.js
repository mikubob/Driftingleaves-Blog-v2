import { readFileSync } from 'node:fs'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const https = command === 'serve' && env.DEV_HTTPS_KEY && env.DEV_HTTPS_CERT ? {
    key: readFileSync(env.DEV_HTTPS_KEY),
    cert: readFileSync(env.DEV_HTTPS_CERT),
  } : undefined
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
        '@shared': fileURLToPath(new URL('./src/shared', import.meta.url)),
      },
    },
    server: {
      host: '127.0.0.1',
      cors: false,
      https,
      port: 5174,
      strictPort: true,
      proxy: {
        '/api/v1/blog/': {
          target: env.API_PROXY_TARGET || 'http://localhost:5922',
          changeOrigin: true,
          ws: true,
          configure(proxy) {
            // 在线握手只使用一次性票据，代理不转发 Cookie。
            proxy.on('proxyReqWs', (request) => request.removeHeader('cookie'))
          },
        },
      },
    },
  }
})
