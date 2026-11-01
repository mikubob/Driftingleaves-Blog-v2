<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { request, validatePassword } from '../api.js'
import { acceptSession } from '../session.js'
import { useResource } from '../composables/useResource.js'
import ResourceState from './ResourceState.vue'
import CaptchaField from '../../../shared/CaptchaField.vue'
const props = defineProps({ popup: Boolean })
const register = ref(false)
const emailLogin = ref(false)
const form = reactive({ username: '', password: '', nickname: '', email: '', emailCode: '' })
const captcha = ref(null)
const busy = ref(false)
const error = ref('')
const feedback = ref('')
const submitLabel = computed(() => {
  if (busy.value) {
    return '请稍候…'
  }
  return register.value ? '创建账号 →' : '登录 →'
})
let popupWindow
let awaitingPopup = false

async function popupResult(event) {
  if (!awaitingPopup || event.origin !== location.origin || event.source !== popupWindow
    || event.data?.type !== 'dl-blog-oauth-login' || typeof event.data.ticket !== 'string'
    || !event.data.ticket || event.data.ticket.length > 512) {
    return
  }
  awaitingPopup = false
  busy.value = true
  error.value = ''
  try {
    const { data } = await request('/blog/auth/oauth/exchange', {
      method: 'POST', credentials: 'include', body: { ticket: event.data.ticket },
    })
    if (data.operation !== 'LOGIN' || !data.session) {
      throw new Error('授权流程不匹配，请重新登录。')
    }
    await acceptSession(data.session)
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
    popupWindow?.close()
  }
}
window.addEventListener('message', popupResult)
onBeforeUnmount(() => {
  window.removeEventListener('message', popupResult)
  popupWindow?.close()
})
const providers = useResource(() => true, async (signal) => {
  const { data } = await request('/blog/auth/oauth/providers', { signal })
  if (!Array.isArray(data) || !data.every((item) => typeof item.source === 'string' && typeof item.displayName === 'string')) {
    throw new Error('登录平台响应格式不正确。')
  }
  return data
})

async function submit() {
  error.value = ''
  feedback.value = ''
  if ((!emailLogin.value || register.value)
    && (!/^[A-Za-z0-9_]{3,64}$/.test(form.username.trim()) || !validatePassword(form.password))) {
    error.value = '账号为 3–64 位字母、数字或下划线；密码为 8–64 个字符且 UTF-8 不超过 72 字节。'
    return
  }
  if ((register.value || emailLogin.value)
    && (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) || !/^[0-9]{6}$/.test(form.emailCode))) {
    error.value = '请输入有效邮箱和邮件中的六位验证码。'
    return
  }
  if (register.value && (!form.nickname.trim() || [...form.nickname.trim()].length > 64)) {
    error.value = '昵称需为 1–64 个字符。'
    return
  }
  busy.value = true
  try {
    const body = { username: form.username.trim(), password: form.password }
    if (register.value) {
      body.nickname = form.nickname.trim()
      body.email = form.email.trim()
      body.emailCode = form.emailCode
      await request('/blog/auth/register', { method: 'POST', body, captcha: captcha.value.getValue() })
      register.value = false
      emailLogin.value = false
      form.emailCode = ''
      form.password = ''
      feedback.value = '注册成功，请使用刚才的账号密码登录。'
      return
    }
    const path = emailLogin.value ? '/blog/auth/email-login' : '/blog/auth/login'
    const credentials = emailLogin.value ? { email: form.email.trim(), emailCode: form.emailCode } : body
    const { data } = await request(path, { method: 'POST', body: credentials, captcha: captcha.value.getValue() })
    await acceptSession(data)
    form.password = ''
    form.emailCode = ''
  } catch (failure) {
    error.value = failure.message
  } finally {
    captcha.value?.refresh()
    busy.value = false
  }
}

/** 发送会消费图形验证码；收到邮件后使用新图片提交注册或登录。 */
async function sendEmailCode() {
  error.value = ''
  feedback.value = ''
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) || form.email.trim().length > 254) {
    error.value = '请输入有效邮箱。'
    return
  }
  busy.value = true
  form.emailCode = ''
  try {
    await request('/blog/auth/email-code', {
      method: 'POST', body: { email: form.email.trim(), purpose: register.value ? 'REGISTER' : 'LOGIN' },
      captcha: captcha.value.getValue(),
    })
    feedback.value = '验证码已发送，五分钟内有效。请填写邮件验证码和新图片中的数字；再次发送需等待一分钟。'
  } catch (failure) {
    error.value = failure.message
  } finally {
    captcha.value?.refresh()
    busy.value = false
  }
}

async function authorize(provider) {
  if (props.popup) {
    popupWindow?.close()
    popupWindow = window.open('about:blank', 'dl-blog-login', 'width=560,height=720')
    if (!popupWindow) {
      error.value = '浏览器阻止了登录弹窗，请允许本站弹窗后重试。'
      return
    }
  }
  busy.value = true
  error.value = ''
  try {
    const { data } = await request(`/blog/auth/oauth/${encodeURIComponent(provider.source)}/authorize`, {
      method: 'POST', credentials: 'include', body: { operation: 'LOGIN' }, captcha: captcha.value.getValue(),
    })
    if (props.popup) {
      awaitingPopup = true
      popupWindow.location = data.authorizationUrl
    } else {
      location.assign(data.authorizationUrl)
    }
  } catch (failure) {
    error.value = failure.message
    popupWindow?.close()
  } finally {
    captcha.value?.refresh()
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-form">
    <div class="auth-tabs">
      <button :class="{ selected: !register }" :disabled="busy" @click="register = false">登录</button>
      <button :class="{ selected: register }" :disabled="busy" @click="register = true">注册账号</button>
    </div>
    <div v-if="!register" class="auth-tabs">
      <button :class="{ selected: !emailLogin }" :disabled="busy" @click="emailLogin = false">账号密码</button>
      <button :class="{ selected: emailLogin }" :disabled="busy" @click="emailLogin = true">邮箱验证码</button>
    </div>
    <form @submit.prevent="submit">
      <fieldset :disabled="busy">
        <template v-if="register || !emailLogin">
          <label for="auth-username">用户名</label>
          <input
            id="auth-username"
            v-model="form.username"
            autocomplete="username"
            pattern="[A-Za-z0-9_]{3,64}"
            maxlength="64"
            required
            placeholder="字母、数字或下划线"
          />
        </template>
        <template v-if="register">
          <label for="auth-nickname">昵称</label>
          <input
            id="auth-nickname"
            v-model="form.nickname"
            maxlength="64"
            autocomplete="nickname"
            required
            placeholder="希望大家怎样称呼你"
          />
        </template>
        <template v-if="register || !emailLogin">
          <label for="auth-password">密码</label>
          <input
            id="auth-password"
            v-model="form.password"
            type="password"
            :autocomplete="register ? 'new-password' : 'current-password'"
            required
            placeholder="8–64 个字符"
          />
        </template>
        <template v-if="register || emailLogin">
          <label for="auth-email">邮箱</label>
          <input id="auth-email" v-model="form.email" type="email" autocomplete="email" maxlength="254" required />
          <label for="auth-email-code">邮箱验证码</label>
          <input id="auth-email-code" v-model="form.emailCode" inputmode="numeric" autocomplete="one-time-code"
            pattern="[0-9]{6}" maxlength="6" required placeholder="邮件中的六位数字" />
        </template>
        <CaptchaField ref="captcha" :request="request" endpoint="/blog/auth/captcha" :disabled="busy" />
        <button v-if="register || emailLogin" type="button" :disabled="busy || !captcha?.ready" @click="sendEmailCode">发送邮箱验证码</button>
        <p v-if="error" role="alert" class="error-text">{{ error }}</p>
        <p v-if="feedback" role="status" class="success-text">{{ feedback }}</p>
        <button class="primary full-width" :disabled="busy || !captcha?.ready">{{ submitLabel }}</button>
      </fieldset>
    </form>
    <div v-if="!register">
      <div class="auth-divider">或使用已绑定的第三方账号</div>
      <ResourceState :resource="providers" />
      <div class="provider-buttons">
        <button
          v-for="provider in providers.data"
          :key="provider.source"
          :disabled="busy || !captcha?.ready"
          @click="authorize(provider)"
        >
          {{ provider.displayName }}
          ↗
        </button>
      </div>
      <p v-if="providers.data && !providers.data.length" class="muted">本站暂未启用第三方登录。</p>
    </div>
    <p class="form-hint">登录状态仅保存在当前页面，刷新后需要重新登录。</p>
  </div>
</template>
