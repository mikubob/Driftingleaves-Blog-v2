import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      cors: false,
      port: 5173,
      strictPort: true,
      proxy: {
        '/api/v1/home/': {
          target: env.API_PROXY_TARGET || 'http://localhost:5922',
          changeOrigin: true,
          ws: true,
          configure(proxy) {
            // 在线握手只转发票据流程所需的请求，不转发同一回环主机上的 Cookie。
            proxy.on('proxyReqWs', (request) => request.removeHeader('cookie'))
          },
        },
      },
    },
  }
})
