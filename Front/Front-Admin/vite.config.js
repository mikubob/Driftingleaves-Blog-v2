import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      port: 5176,
      strictPort: true,
      proxy: {
        '/api/v1': {
          target: env.API_PROXY_TARGET || 'http://localhost:5922',
          changeOrigin: true,
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          onlyExplicitManualChunks: true,
          // 编辑器核心与图表只随对应路由加载，语言包保留按需导入。
          manualChunks(id) {
            if (id.includes('/node_modules/') && /\/@codemirror\/(?:state|view)\//.test(id)) {
              return 'editor-core'
            }
            if (id.includes('/node_modules/zrender/')) {
              return 'chart-renderer'
            }
            if (id.includes('/node_modules/echarts/')) {
              return 'charts'
            }
          },
        },
      },
    },
  }
})
