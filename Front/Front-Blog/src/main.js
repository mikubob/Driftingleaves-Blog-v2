import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router.js'
import { restoreSession } from './session.js'
import { createPinia } from 'pinia'
import 'element-plus/es/components/base/style/css'
import './shared/element-focus.css'
import './assets/styles/ali-iconfont.css'
import './assets/styles/main.scss'
import './assets/styles/account.scss'

const app = createApp(App)
app.use(createPinia())
async function bootstrap() {
  // OAuth 完成页先处理一次性票据。
  if (location.pathname !== '/oauth/complete') {
    await restoreSession()
  }
  app.use(router)
  router.isReady().then(() => app.mount('#app'))
}
bootstrap()
