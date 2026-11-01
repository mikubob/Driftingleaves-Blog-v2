import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router.js'
import { restoreSession } from './session.js'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import './assets/styles/ali-iconfont.css'
import './assets/styles/main.scss'
import './assets/styles/account.scss'

const app = createApp(App)
app.use(createPinia())
async function bootstrap() {
  // 授权完成页先处理票据，不能让旧会话恢复失败打断新的登录或绑定弹窗。
  if (location.pathname !== '/oauth/complete') {
    await restoreSession()
  }
  app.use(router)
  router.isReady().then(() => app.mount('#app'))
}
bootstrap()
