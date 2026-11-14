import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { installCvMotion } from './motion.js'

installCvMotion()
createApp(App).mount('#app')
