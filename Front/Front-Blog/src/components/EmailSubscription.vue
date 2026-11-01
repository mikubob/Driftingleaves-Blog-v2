<script setup>
import { computed, ref } from 'vue'
import { apiRoot, request } from '../api.js'
import { can, session } from '../session.js'
import { useResource } from '../composables/useResource.js'
import ResourceState from './ResourceState.vue'
const email = ref('')
const busy = ref(false)
const error = ref('')
const feedback = ref('')
const subscription = useResource(() => session.user?.userId, async (signal) => {
  if (!session.user) {
    return null
  }
  const { data } = await request('/blog/me/rss-subscription', { signal })
  email.value = data?.email || ''
  return data
})
const submitLabel = computed(() => {
  if (busy.value) {
    return '处理中…'
  }
  return subscription.data?.status === 0 ? '申请 / 重发确认邮件' : '申请订阅'
})

async function update(cancel = false) {
  busy.value = true
  error.value = ''
  feedback.value = ''
  try {
    const { data } = await request('/blog/me/rss-subscription', {
      method: cancel ? 'DELETE' : 'PUT',
      body: cancel ? undefined : { email: email.value.trim().toLowerCase() },
    })
    subscription.data = data
    if (cancel) {
      feedback.value = '已取消邮件订阅。'
    } else if (data.status === 1) {
      feedback.value = '当前邮箱已订阅。'
    } else {
      feedback.value = '确认邮件已发送，请打开邮件中的链接完成邮箱验证。'
    }
    if (cancel) {
      await subscription.reload()
    }
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="panel">
    <p class="eyebrow">LETTERS FROM THE JOURNAL</p>
    <h2>订阅新的记录</h2>
    <p class="muted">有新文章时，让它去你的邮箱里坐坐。</p>
    <ResourceState :resource="subscription" />
    <template v-if="!subscription.loading && !subscription.error">
      <p v-if="subscription.data">
        当前状态：
        <strong>{{ ['等待邮箱验证', '已订阅', '已退订'][subscription.data.status] }}</strong>
        ·
        {{ subscription.data.email }}
      </p>
      <form v-if="can('blog:rss:subscribe')" @submit.prevent="update(false)">
        <fieldset :disabled="busy">
          <label for="subscription-email">订阅邮箱</label>
          <input
            id="subscription-email"
            v-model="email"
            type="email"
            maxlength="254"
            autocomplete="email"
            required
          />
          <div class="row">
            <button class="primary" :disabled="busy">{{ submitLabel }}</button>
            <button
              v-if="subscription.data && subscription.data.status !== 2"
              type="button"
              :disabled="busy"
              @click="update(true)"
            >
              取消订阅
            </button>
          </div>
        </fieldset>
      </form>
      <p v-else class="muted">当前账号没有申请邮件订阅的权限。</p>
    </template>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>
    <p v-if="feedback" class="success-text" role="status">{{ feedback }}</p>
    <p class="form-hint">
      也可以用阅读器订阅
      <a :href="`${apiRoot}/blog/rss.xml`" target="_blank" rel="noopener noreferrer" class="text-button">RSS Feed ↗</a>
      。
    </p>
  </section>
</template>
