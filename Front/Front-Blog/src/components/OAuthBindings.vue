<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { request } from '../api.js'
import { session, sessionRevoked } from '../session.js'
import { useResource } from '../composables/useResource.js'
import ResourceState from './ResourceState.vue'

const busy = ref(false)
const error = ref('')
const feedback = ref('')
const confirmingUnbind = ref('')
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
      method: 'POST', body: { operation: 'BIND' },
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
      method: 'POST', body: { ticket: event.data.ticket },
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
    confirmingUnbind.value = ''
    sessionRevoked()
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
  <section class="bento-card account-oauth-card">
    <div class="bento-card-head">
      <div class="bento-card-title-wrap">
        <h2 class="bento-card-title">账号与安全</h2>
        <span
          class="info-tip"
          tabindex="0"
          role="note"
          aria-label="请在登录后五分钟内操作；解绑成功后需要重新登录。"
          data-tip="请在登录后五分钟内操作；解绑成功后需要重新登录。"
        >?</span>
      </div>
      <slot name="header-action" />
    </div>
    <ResourceState :resource="platforms" />
    <p v-if="error" role="alert" class="error-text">{{ error }}</p>
    <p v-if="feedback" role="status" class="success-text">{{ feedback }}</p>
    <div v-if="platforms.data?.length" class="bento-compact-list">
      <div v-for="provider in platforms.data" :key="provider.source" class="bento-compact-item">
        <div class="bento-compact-row">
          <span class="bento-provider-name">
            {{ provider.displayName }}
            <span :class="['status-pill', provider.bound ? 'pill-success' : 'pill-neutral']">
              {{ provider.bound ? '已绑定' : '未绑定' }}
            </span>
          </span>
          <button
            v-if="provider.bound"
            type="button"
            :disabled="busy"
            @click="confirmingUnbind = confirmingUnbind === provider.source ? '' : provider.source"
          >
            解除绑定
          </button>
          <button v-else type="button" class="primary" :disabled="busy" @click="bind(provider.source)">绑定账号</button>
        </div>
        <div v-if="confirmingUnbind === provider.source" class="inline-confirm" role="group" aria-label="确认解除绑定">
          <span>确认解除 {{ provider.displayName }} 绑定？解绑后将退出当前登录状态。</span>
          <div class="confirm-actions">
            <button type="button" :disabled="busy" @click="unbind(provider.source)">确认解绑</button>
            <button type="button" :disabled="busy" @click="confirmingUnbind = ''">取消</button>
          </div>
        </div>
      </div>
    </div>
    <p v-if="platforms.data && !platforms.data.length" class="muted">本站暂未启用第三方平台。</p>
  </section>
</template>
