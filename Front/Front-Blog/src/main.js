import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router.js'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import './assets/styles/ali-iconfont.css'
import './assets/styles/main.scss'
import './assets/styles/account.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
router.isReady().then(() => app.mount('#app'))
