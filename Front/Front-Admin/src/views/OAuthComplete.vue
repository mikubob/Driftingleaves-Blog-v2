<script setup>
import { ElButton, ElResult } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/result/style/css'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api.js'
import { firstPage, registerMenuRoutes } from '../router.js'
import { acceptSession } from '../session.js'

const router = useRouter()
const message = ref('正在完成授权…')
const failed = ref(false)

onMounted(async () => {
  const result = new URLSearchParams(location.hash.slice(1))
  const ticket = result.get('ticket')
  const operation = result.get('operation')
  history.replaceState(history.state, '', location.pathname)
  if (!ticket || !/^[A-Za-z0-9_-]{43}$/.test(ticket)) {
    failed.value = true
    message.value = result.get('error') === 'OAUTH_NOT_BOUND'
      ? '该第三方账号尚未绑定，请先登录本站并在个人资料中绑定。'
      : '第三方授权未完成，请重新发起。'
    return
  }
  // 绑定弹窗只把票据发回同源原页面，由原页面使用当前会话兑换。
  if (operation === 'BIND' && window.opener) {
    window.opener.postMessage({ type: 'drifting-leaves-oauth', ticket }, location.origin)
    message.value = '授权结果已交回原页面，可以关闭此窗口。'
    window.close()
    return
  }
  if (operation !== 'LOGIN') {
    failed.value = true
    message.value = '绑定页面已关闭，请回到后台检查绑定列表。'
    return
  }
  try {
    const { data } = await request('/admin/auth/oauth/exchange', {
      method: 'POST',
      body: { ticket },
    })
    if (data?.operation !== 'LOGIN') {
      throw new Error('授权结果与登录流程不匹配。')
    }
    await acceptSession(data.session)
    registerMenuRoutes()
    await router.replace(firstPage())
  } catch (failure) {
    failed.value = true
    message.value = failure.message
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
