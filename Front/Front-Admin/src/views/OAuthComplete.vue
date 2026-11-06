<script setup>
import { ElButton, ElResult } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/result/style/css'
import { onMounted, ref } from 'vue'

const message = ref('正在完成授权…')
const failed = ref(false)

onMounted(async () => {
  const ticket = new URLSearchParams(location.hash.slice(1)).get('ticket')
  history.replaceState(null, '', location.pathname)
  if (!ticket || ticket.length > 512) {
    failed.value = true
    message.value = '缺少有效授权票据，请重新发起绑定。'
    return
  }
  // 绑定弹窗只把票据发回同源原页面，由原页面使用当前会话兑换。
  if (window.opener) {
    window.opener.postMessage({ type: 'drifting-leaves-oauth', ticket }, location.origin)
    message.value = '授权结果已交回原页面，可以关闭此窗口。'
    window.close()
    return
  }
  failed.value = true
  message.value = '绑定页面已关闭，请回到后台检查绑定列表。'
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
