import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router, { registerMenuRoutes } from './router.js'
import { restoreSession, session } from './session.js'
import './assets/styles/ali-iconfont.css'
import './assets/styles/main.css'
import './style.css'

const app = createApp(App).use(ElementPlus, { locale: zhCn })
async function bootstrap() {
  // 授权完成页先处理票据，绑定弹窗不恢复身份，也不清理原页面的持久凭据。
  if (location.pathname !== '/oauth/complete') {
    await restoreSession()
  }
  if (session.ready) {
    registerMenuRoutes()
  }
  app.use(router).mount('#app')
}
bootstrap()
