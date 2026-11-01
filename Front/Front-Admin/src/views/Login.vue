<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../api.js'
import CaptchaField from '../../../shared/CaptchaField.vue'
import { acceptSession } from '../session.js'
import { firstPage, registerMenuRoutes } from '../router.js'
import { validatePassword } from '../format.js'
import { User, Lock, ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()
const form = reactive({ username: '', password: '', email: '', emailCode: '' })
const captcha = ref(null)
const busy = ref(false)
const error = ref('')
const feedback = ref('')

async function login() {
  if (busy.value) {
    return
  }
  error.value = ''
  feedback.value = ''
  if (!/^[A-Za-z0-9_]{3,64}$/.test(form.username) || !validatePassword(form.password)) {
    error.value =
      '用户名须为 3–64 位字母、数字或下划线；密码为 8–64 个字符且 UTF-8 不超过 72 字节。'
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) || !/^[0-9]{6}$/.test(form.emailCode)) {
    error.value = '请输入账号绑定的邮箱和邮件中的六位验证码。'
    return
  }
  busy.value = true
  try {
    const { data } = await request('/admin/auth/login', {
      method: 'POST',
      body: { username: form.username, password: form.password, email: form.email.trim(), emailCode: form.emailCode },
      captcha: captcha.value.getValue(),
    })
    form.password = ''
    form.emailCode = ''
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

/** 先核对账密与账号邮箱，发送成功后仍需填写新图形验证码完成登录。 */
async function sendEmailCode() {
  error.value = ''
  feedback.value = ''
  if (!/^[A-Za-z0-9_]{3,64}$/.test(form.username) || !validatePassword(form.password)
    || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) || form.email.trim().length > 254) {
    error.value = '请先填写正确的用户名、密码和账号绑定的邮箱。'
    return
  }
  busy.value = true
  form.emailCode = ''
  try {
    await request('/admin/auth/email-code', {
      method: 'POST',
      body: { username: form.username, password: form.password, email: form.email.trim() },
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
        <p class="login-subtitle">使用账号密码、邮箱验证码和图形验证码登录。</p>
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
          <label for="email">绑定邮箱</label>
          <el-input id="email" v-model="form.email" type="email" size="large" autocomplete="email"
            maxlength="254" placeholder="请输入账号绑定的邮箱" :disabled="busy" />
          <label for="email-code">邮箱验证码</label>
          <el-input id="email-code" v-model="form.emailCode" size="large" inputmode="numeric"
            autocomplete="one-time-code" maxlength="6" placeholder="邮件中的六位数字" :disabled="busy" />
          <CaptchaField ref="captcha" :request="request" endpoint="/admin/auth/captcha" :disabled="busy" />
          <el-button :disabled="busy || !captcha?.ready" @click="sendEmailCode">发送邮箱验证码</el-button>
          <p v-if="error" class="error-text" role="alert">{{ error }}</p>
          <p v-if="feedback" class="success-text" role="status">{{ feedback }}</p>
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
        <p class="login-note">
          <el-icon aria-hidden="true"><Lock /></el-icon>
          安全会话仅在当前页面有效
        </p>
      </div>
      <footer>© {{ new Date().getFullYear() }} Drifting Leaves</footer>
    </section>
  </main>
</template>
