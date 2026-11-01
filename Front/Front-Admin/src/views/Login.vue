<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api.js'
import CaptchaField from '../../../shared/CaptchaField.vue'
import { acceptSession } from '../session.js'
import { firstPage, registerMenuRoutes } from '../router.js'
import { validatePassword } from '../format.js'
import { User, Lock, ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const captcha = ref(null)
const busy = ref(false)
const error = ref('')
const providers = ref([])
const providerError = ref('')

async function loadProviders() {
  providerError.value = ''
  try {
    const { data } = await request('/admin/auth/oauth/providers')
    if (
      !Array.isArray(data) ||
      !data.every(
        (provider) =>
          typeof provider.source === 'string' && typeof provider.displayName === 'string',
      )
    ) {
      throw new Error('第三方平台响应格式不正确。')
    }
    providers.value = data
  } catch (failure) {
    providerError.value = failure.message
  }
}

async function login() {
  if (busy.value) {
    return
  }
  error.value = ''
  if (!/^[A-Za-z0-9_]{3,64}$/.test(form.username) || !validatePassword(form.password)) {
    error.value =
      '用户名须为 3–64 位字母、数字或下划线；密码为 8–64 个字符且 UTF-8 不超过 72 字节。'
    return
  }
  busy.value = true
  try {
    const { data } = await request('/admin/auth/login', {
      method: 'POST',
      body: { username: form.username, password: form.password },
      captcha: captcha.value.getValue(),
    })
    form.password = ''
    await acceptSession(data)
    registerMenuRoutes()
    await router.replace(firstPage())
  } catch (failure) {
    error.value = failure.message
  } finally {
    captcha.value?.refresh()
    busy.value = false
  }
}

async function oauth(provider) {
  busy.value = true
  error.value = ''
  try {
    const { data } = await request(`/admin/auth/oauth/${encodeURIComponent(provider.source)}/authorize`, {
      method: 'POST',
      body: { operation: 'LOGIN' },
      captcha: captcha.value.getValue(),
      credentials: 'include',
    })
    const url = new URL(data.authorizationUrl)
    if (!['http:', 'https:'].includes(url.protocol)) {
      throw new Error('第三方授权地址无效。')
    }
    window.location.assign(url.href)
  } catch (failure) {
    captcha.value?.refresh()
    error.value = failure.message
    busy.value = false
  }
}

onMounted(loadProviders)
</script>

<template>
  <main class="login-page">
    <div class="login-decoration" aria-hidden="true">
      <span>D</span>
      <span class="decoration-line"></span>
      <small>A SPACE FOR YOUR IDEAS.</small>
    </div>
    <section class="login-panel">
      <div class="login-box">
        <div class="brand-symbol login-brand">
          D
          <span>↗</span>
        </div>
        <p class="eyebrow">DRIFTING LEAVES / ADMIN</p>
        <h1>管理控制台</h1>
        <p class="login-subtitle">欢迎回来，开始管理你的站点。</p>
        <form class="login-form" @submit.prevent="login">
          <label for="username">用户名</label>
          <el-input
            id="username"
            v-model="form.username"
            size="large"
            autocomplete="username"
            placeholder="请输入用户名"
            :disabled="busy"
          >
            <template #prefix><el-icon aria-hidden="true"><User /></el-icon></template>
          </el-input>
          <label for="password">登录密码</label>
          <el-input
            id="password"
            v-model="form.password"
            size="large"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入登录密码"
            :disabled="busy"
          >
            <template #prefix><el-icon aria-hidden="true"><Lock /></el-icon></template>
          </el-input>
          <CaptchaField ref="captcha" :request="request" endpoint="/admin/auth/captcha" :disabled="busy" />
          <p v-if="error" class="error-text" role="alert">{{ error }}</p>
          <el-button
            class="login-submit"
            type="primary"
            size="large"
            native-type="submit"
            :loading="busy"
            :disabled="!captcha?.ready"
          >
            登 录
            <el-icon aria-hidden="true"><ArrowRight /></el-icon>
          </el-button>
        </form>
        <template v-if="providers.length">
          <el-divider>或使用第三方账号</el-divider>
          <div class="oauth-buttons">
            <el-button
              v-for="provider in providers"
              :key="provider.source"
              :disabled="busy || !captcha?.ready"
              @click="oauth(provider)"
            >
              {{ provider.displayName }}
            </el-button>
          </div>
        </template>
        <p v-if="providerError" class="muted-text">
          第三方登录暂不可用
          <button class="text-button" @click="loadProviders">重试</button>
        </p>
        <p class="login-note">
          <el-icon aria-hidden="true"><Lock /></el-icon>
          安全会话仅在当前页面有效
        </p>
      </div>
      <footer>© {{ new Date().getFullYear() }} Drifting Leaves</footer>
    </section>
  </main>
</template>
