<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api.js'
import { acceptSession } from '../session.js'
const router = useRouter()
let ticket = new URLSearchParams(location.hash.slice(1)).get('ticket')
history.replaceState(history.state, '', location.pathname)
const message = ref('正在完成登录，请稍候…')
const failed = ref(false)

onMounted(async () => {
  if (!ticket || ticket.length > 512) {
    failed.value = true
    message.value = '缺少有效授权票据，请重新发起登录。'
    return
  }
  // 邮件确认页用登录弹窗保留原页面内存票据，原页面核对来源后兑换。
  if (window.opener) {
    window.opener.postMessage({ type: 'dl-blog-oauth-login', ticket }, location.origin)
    ticket = null
    message.value = '授权已交回原页面，可以关闭此窗口。'
    window.close()
    return
  }
  try {
    const { data } = await request('/blog/auth/oauth/exchange', {
      method: 'POST', credentials: 'include', body: { ticket },
    })
    if (data.operation !== 'LOGIN' || !data.session) {
      throw new Error('授权流程不匹配，请重新登录。')
    }
    await acceptSession(data.session)
    await router.replace('/account')
  } catch (error) {
    failed.value = true
    message.value = error.message
  } finally {
    ticket = null
  }
})
</script>

<template>
  <section class="panel auth-panel">
    <p class="eyebrow">OAUTH / SIGN IN</p>
    <h2>第三方授权</h2>
    <p :role="failed ? 'alert' : 'status'">{{ message }}</p>
    <router-link v-if="failed" to="/login" class="button">重新登录 →</router-link>
  </section>
</template>
