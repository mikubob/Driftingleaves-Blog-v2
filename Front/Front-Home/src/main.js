import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { installHomeMotion } from './motion.js'

installHomeMotion()
createApp(App).mount('#app')
