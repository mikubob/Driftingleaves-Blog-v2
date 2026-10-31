<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api.js'
import { acceptSession } from '../session.js'
import { firstPage, registerMenuRoutes } from '../router.js'

const router = useRouter()
const message = ref('正在完成授权…')
const failed = ref(false)

onMounted(async () => {
  const ticket = new URLSearchParams(location.hash.slice(1)).get('ticket')
  history.replaceState(null, '', location.pathname)
  if (!ticket || ticket.length > 512) {
    failed.value = true
    message.value = '缺少有效授权票据，请重新发起登录或绑定。'
    return
  }
  // 绑定弹窗只把票据发回同源原页面，由保留 Token 的原页面兑换。
  if (window.opener) {
    window.opener.postMessage({ type: 'drifting-leaves-oauth', ticket }, location.origin)
    message.value = '授权结果已交回原页面，可以关闭此窗口。'
    window.close()
    return
  }
  try {
    const { data } = await request('/auth/oauth/exchange', {
      method: 'POST',
      body: { ticket },
      credentials: 'include',
    })
    if (data.operation !== 'LOGIN' || !data.session) {
      throw new Error('绑定页面已关闭或会话已丢失，请重新登录后检查绑定列表。')
    }
    await acceptSession(data.session)
    registerMenuRoutes()
    await router.replace(firstPage())
  } catch (error) {
    failed.value = true
    message.value = error.message
  }
})
</script>

<template>
  <main class="center-page">
    <el-result :icon="failed ? 'error' : 'info'" title="第三方授权" :sub-title="message">
      <template #extra>
        <router-link to="/login"><el-button>返回登录</el-button></router-link>
      </template>
    </el-result>
  </main>
</template>
