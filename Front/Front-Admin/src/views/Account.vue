<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { clearSession, session } from '../session.js'
import { nullable, validatePassword } from '../format.js'
import RequestError from '../components/RequestError.vue'

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const revision = ref('')
const bindings = ref([])
const providers = ref([])
const bindingBusy = ref(false)
const form = reactive({ nickname: '', email: '', avatar: '', intro: '' })
const password = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
let popup = null
let popupTimer

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [me, bindingResult, providerResult] = await Promise.all([
      request('/admin/me'),
      request('/admin/me/oauth-bindings'),
      request('/admin/auth/oauth/providers'),
    ])
    session.user = me.data
    revision.value = me.revision
    Object.assign(form, {
      nickname: me.data.nickname,
      email: me.data.email || '',
      avatar: me.data.avatar || '',
      intro: me.data.intro || '',
    })
    bindings.value = bindingResult.data
    providers.value = providerResult.data
  } catch (failure) {
    error.value = failure.message
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  saving.value = true
  formError.value = ''
  try {
    const result = await request('/admin/me/profile', {
      method: 'PATCH',
      revision: revision.value,
      body: {
        nickname: form.nickname,
        email: nullable(form.email),
        avatar: nullable(form.avatar),
        intro: nullable(form.intro),
      },
    })
    session.user = result.data
    revision.value = result.revision
    ElMessage.success('个人资料已保存')
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function changePassword(clear = false) {
  formError.value = ''
  if (
    !clear &&
    (!validatePassword(password.newPassword) || password.newPassword !== password.confirmPassword)
  ) {
    formError.value = '新密码须为 8–64 个字符且 UTF-8 不超过 72 字节，两次输入须一致。'
    return
  }
  try {
    await ElMessageBox.confirm(
      clear ? '清除密码后无法登录后台；需先通过前台第三方登录重新设置密码，确认继续？' : '修改密码后需要重新登录，确认继续？',
      '账号安全',
      { confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    const body = clear
      ? { currentPassword: password.currentPassword }
      : { newPassword: password.newPassword }
    if (!clear && session.user.hasPassword) {
      body.currentPassword = password.currentPassword
    }
    await request('/admin/me/password', { method: clear ? 'DELETE' : 'PUT', body })
    Object.assign(password, { currentPassword: '', newPassword: '', confirmPassword: '' })
    clearSession()
    ElMessage.success('安全设置已更新，请重新登录')
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

function clearPopup() {
  clearInterval(popupTimer)
  popupTimer = undefined
  popup = null
  bindingBusy.value = false
}

async function bind(provider) {
  if (bindingBusy.value) {
    return
  }
  // 先在点击事件内创建窗口，避免异步授权请求被浏览器拦截弹窗。
  popup = window.open('about:blank', 'drifting-leaves-bind', 'popup,width=640,height=760')
  if (!popup) {
    ElMessage.warning('请允许弹出授权窗口后重试。')
    return
  }
  bindingBusy.value = true
  try {
    const { data } = await request(`/admin/auth/oauth/${encodeURIComponent(provider.source)}/authorize`, {
      method: 'POST',
      body: { operation: 'BIND' },
      credentials: 'include',
    })
    const url = new URL(data.authorizationUrl)
    if (!['http:', 'https:'].includes(url.protocol)) {
      throw new Error('授权地址无效。')
    }
    popup.location.replace(url.href)
    popupTimer = setInterval(() => {
      if (!popup || popup.closed) {
        clearPopup()
      }
    }, 1000)
  } catch (failure) {
    popup?.close()
    clearPopup()
    ElMessage.error(failure.message)
  }
}

async function receiveBinding(event) {
  if (
    event.origin !== location.origin ||
    !popup ||
    event.source !== popup ||
    !bindingBusy.value ||
    event.data?.type !== 'drifting-leaves-oauth' ||
    typeof event.data.ticket !== 'string' ||
    !event.data.ticket ||
    event.data.ticket.length > 512
  ) {
    return
  }
  popup.close()
  clearPopup()
  bindingBusy.value = true
  try {
    const { data } = await request('/admin/auth/oauth/exchange', {
      method: 'POST',
      body: { ticket: event.data.ticket },
      credentials: 'include',
    })
    if (data.operation !== 'BIND') {
      throw new Error('授权结果与绑定流程不匹配。')
    }
    ElMessage.success('第三方账号已绑定')
    bindings.value = (await request('/admin/me/oauth-bindings')).data
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    bindingBusy.value = false
  }
}

async function unbind(source) {
  try {
    await ElMessageBox.confirm(
      '解绑成功后需要重新登录，且账号必须保留一种可用登录方式。',
      '解除绑定',
      { confirmButtonText: '解绑', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await request(`/admin/me/oauth-bindings/${encodeURIComponent(source)}`, { method: 'DELETE' })
    clearSession()
    ElMessage.success('已解除绑定，请重新登录')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('message', receiveBinding)
})
onBeforeUnmount(() => {
  window.removeEventListener('message', receiveBinding)
  popup?.close()
  clearPopup()
  Object.assign(password, { currentPassword: '', newPassword: '', confirmPassword: '' })
})
</script>

<template>
  <section v-loading="loading">
    <div class="page-header">
      <div>
        <p class="eyebrow">ACCOUNT / SECURITY</p>
        <h1>个人资料与安全</h1>
        <p class="muted-text">管理当前账号的联系资料、密码与第三方绑定。</p>
      </div>
    </div>
    <RequestError :message="error" @retry="load" />
    <div v-if="!error" class="settings-grid">
      <el-card shadow="never">
        <template #header><h2>个人资料</h2></template>
        <form class="edit-form" @submit.prevent="saveProfile">
          <label>
            用户名
            <el-input :model-value="session.user?.username" disabled />
          </label>
          <label>
            昵称
            <el-input v-model="form.nickname" required maxlength="64" />
          </label>
          <label>
            联系邮箱
            <el-input v-model="form.email" type="email" maxlength="254" />
            <small class="muted-text">仅作联系资料，不代表已经验证的订阅邮箱。</small>
          </label>
          <label>
            头像地址
            <el-input v-model="form.avatar" type="url" maxlength="2048" />
          </label>
          <label>
            个人简介
            <el-input v-model="form.intro" type="textarea" :rows="4" maxlength="500" />
          </label>
          <el-button type="primary" native-type="submit" :loading="saving">保存资料</el-button>
        </form>
      </el-card>
      <div class="settings-stack">
        <el-card shadow="never">
          <template #header><h2>本站密码</h2></template>
          <form class="edit-form" @submit.prevent="changePassword(false)">
            <label v-if="session.user?.hasPassword">
              当前密码
              <el-input
                v-model="password.currentPassword"
                type="password"
                show-password
                autocomplete="current-password"
                required
              />
            </label>
            <label>
              新密码
              <el-input
                v-model="password.newPassword"
                type="password"
                show-password
                autocomplete="new-password"
                required
              />
            </label>
            <label>
              确认新密码
              <el-input
                v-model="password.confirmPassword"
                type="password"
                show-password
                autocomplete="new-password"
                required
              />
            </label>
            <div class="header-actions">
              <el-button type="primary" native-type="submit" :loading="saving">
                {{ session.user?.hasPassword ? '修改密码' : '设置密码' }}
              </el-button>
              <el-button
                type="danger"
                v-if="session.user?.hasPassword"
                :disabled="saving"
                @click="changePassword(true)"
              >
                清除密码
              </el-button>
            </div>
          </form>
        </el-card>
        <el-card shadow="never">
          <template #header><h2>第三方账号</h2></template>
          <p class="muted-text">绑定等敏感操作要求最近 300 秒内认证，超时请退出并重新登录。</p>
          <div v-for="provider in providers" :key="provider.source" class="binding-row">
            <strong>{{ provider.displayName }}</strong>
            <el-button
              v-if="bindings.some((binding) => binding.source === provider.source)"
              type="danger"
              :disabled="saving || bindingBusy"
              @click="unbind(provider.source)"
            >
              解除绑定
            </el-button>
            <el-button type="primary" v-else :disabled="saving" :loading="bindingBusy" @click="bind(provider)">
              绑定账号
            </el-button>
          </div>
          <el-empty v-if="!providers.length" description="暂无可用的第三方平台" :image-size="60" />
        </el-card>
      </div>
    </div>
    <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
  </section>
</template>
