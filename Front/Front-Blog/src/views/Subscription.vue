<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { request } from '../api.js'
import { can, session } from '../session.js'
import AuthForm from '../components/AuthForm.vue'
const route = useRoute()
const confirm = route.params.action === 'confirm'
const actionLabel = confirm ? '确认订阅' : '确认退订'
let ticket = new URLSearchParams(location.hash.slice(1)).get(confirm ? 'confirmTicket' : 'unsubscribeTicket')
history.replaceState(history.state, '', location.pathname)
const valid = ref(!!ticket && ticket.length <= 512)
const busy = ref(false)
const done = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    await request(confirm ? '/me/rss-subscription/confirm' : '/blog/rss-subscriptions/unsubscribe', {
      method: 'POST', body: { ticket },
    })
    done.value = true
    ticket = null
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="panel auth-panel">
    <p class="eyebrow">EMAIL SUBSCRIPTION</p>
    <h2>{{ confirm ? '确认邮箱订阅' : '取消邮件订阅' }}</h2>
    <p v-if="!valid" class="error-text" role="alert">链接缺少有效票据，请重新打开完整邮件链接。</p>
    <p v-else-if="done" class="success-text" role="status">{{ confirm ? '邮箱验证成功，已开启邮件订阅。' : '已取消邮件订阅。' }}</p>
    <template v-else>
      <template v-if="confirm && !session.user">
        <p class="muted">请先登录申请订阅的同一账号，再确认邮箱。</p>
        <AuthForm popup />
        <p class="form-hint">请保留当前页面；第三方登录将在弹窗中完成。</p>
      </template>
      <template v-else-if="!confirm || can('blog:rss:subscribe')">
        <p class="muted">{{ confirm ? `当前账号：${session.user.nickname}。点击确认完成订阅。` : '确认后，此邮箱将不再接收新文章通知。' }}</p>
        <button class="primary" :disabled="busy" @click="submit">{{ busy ? '处理中…' : actionLabel }}</button>
      </template>
      <p v-else class="error-text">当前账号没有邮件订阅权限。</p>
    </template>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>
    <p>
      <router-link to="/account" class="text-button">返回个人中心 →</router-link>
    </p>
  </section>
</template>
