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
import { installBlogMotion } from './utils/motion.js'
import { useBlogStore } from './stores'

installBlogMotion()

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
async function bootstrap() {
  // 公开站点数据与会话恢复并行发起，避免首屏头像和统计被串行阻塞。
  void useBlogStore(pinia).init()
  // OAuth 完成页先处理一次性票据。
  if (location.pathname !== '/oauth/complete') {
    await restoreSession()
  }
  app.use(router)
  router.isReady().then(() => app.mount('#app'))
}
bootstrap()
