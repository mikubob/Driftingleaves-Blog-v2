import { createApp } from 'vue'
import { ElLoading } from 'element-plus'
import 'element-plus/es/components/loading/style/css'
import './shared/element-focus.css'
import App from './App.vue'
import router, { registerMenuRoutes } from './router.js'
import { restoreSession, session } from './session.js'
import './assets/styles/ali-iconfont.css'
import './assets/styles/main.css'
import './style.css'

const app = createApp(App)
app.directive('loading', ElLoading.directive)
async function bootstrap() {
  // OAuth 完成页先处理一次性票据。
  if (location.pathname !== '/oauth/complete') {
    await restoreSession()
  }
  if (session.ready) {
    registerMenuRoutes()
  }
  app.use(router).mount('#app')
}
bootstrap()
