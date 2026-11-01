<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { request } from '../api.js'
import { clearSession, session } from '../session.js'
import { useResource } from '../composables/useResource.js'
import ResourceState from './ResourceState.vue'

const busy = ref(false)
const error = ref('')
const feedback = ref('')
let popup
let popupTimer
const platforms = useResource(() => session.user?.userId, async (signal) => {
  if (!session.user) {
    return null
  }
  const [providers, bindings] = await Promise.all([
    request('/blog/auth/oauth/providers', { signal }), request('/blog/me/oauth-bindings', { signal }),
  ])
  return providers.data.map((provider) => ({
    ...provider, bound: bindings.data.some((binding) => binding.source === provider.source),
  }))
})

/** 授权结束或组件卸载时释放窗口和轮询，不保留旧流程。 */
function closePopup() {
  clearInterval(popupTimer)
  popup?.close()
  popup = null
  busy.value = false
}

/** 原页面保留 Token，弹窗只能交回票据，不能自行证明绑定用户。 */
async function bind(source) {
  error.value = ''
  feedback.value = ''
  popup = window.open('about:blank', 'dl-blog-bind', 'popup,width=640,height=760')
  if (!popup) {
    error.value = '请允许本站弹窗后重试。'
    return
  }
  busy.value = true
  try {
    const { data } = await request(`/blog/auth/oauth/${encodeURIComponent(source)}/authorize`, {
      method: 'POST', credentials: 'include', body: { operation: 'BIND' },
    })
    const url = new URL(data.authorizationUrl)
    if (!['http:', 'https:'].includes(url.protocol)) {
      throw new Error('授权地址无效。')
    }
    popup.location.replace(url.href)
    popupTimer = setInterval(() => {
      if (popup?.closed) {
        closePopup()
      }
    }, 1000)
  } catch (failure) {
    error.value = failure.message
    closePopup()
  }
}

/** 只接受刚打开的同源弹窗，兑换时仍由后端核对发起绑定的原 Token。 */
async function receive(event) {
  if (!busy.value || !popup || event.origin !== location.origin || event.source !== popup
    || event.data?.type !== 'dl-blog-oauth-login' || typeof event.data.ticket !== 'string'
    || !event.data.ticket || event.data.ticket.length > 512) {
    return
  }
  closePopup()
  busy.value = true
  try {
    const { data } = await request('/blog/auth/oauth/exchange', {
      method: 'POST', credentials: 'include', body: { ticket: event.data.ticket },
    })
    if (data.operation !== 'BIND') {
      throw new Error('授权结果与绑定操作不匹配。')
    }
    feedback.value = '第三方账号已绑定，下次可直接使用此平台登录。'
    await platforms.reload()
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

async function unbind(source) {
  error.value = ''
  busy.value = true
  try {
    await request(`/blog/me/oauth-bindings/${encodeURIComponent(source)}`, { method: 'DELETE' })
    clearSession()
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

window.addEventListener('message', receive)
onBeforeUnmount(() => {
  window.removeEventListener('message', receive)
  closePopup()
})
</script>

<template>
  <section class="panel">
    <h2>第三方账号绑定</h2>
    <p class="muted">请在登录后五分钟内操作；解绑成功后需要重新登录。</p>
    <ResourceState :resource="platforms" />
    <p v-if="error" role="alert" class="error-text">{{ error }}</p>
    <p v-if="feedback" role="status" class="success-text">{{ feedback }}</p>
    <div v-for="provider in platforms.data" :key="provider.source" class="row">
      <span>{{ provider.displayName }} · {{ provider.bound ? '已绑定' : '未绑定' }}</span>
      <button v-if="provider.bound" :disabled="busy" @click="unbind(provider.source)">解除绑定</button>
      <button v-else :disabled="busy" @click="bind(provider.source)">绑定账号</button>
    </div>
    <p v-if="platforms.data && !platforms.data.length" class="muted">本站暂未启用第三方平台。</p>
  </section>
</template>
