<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { apiRoot, request } from '../api.js'
import { can, session } from '../session.js'
import { useResource } from '../composables/useResource.js'
import ResourceState from './ResourceState.vue'
const email = computed(() => session.user?.email?.trim().toLowerCase() || '')
const busy = ref(false)
const subscription = useResource([() => session.user?.userId, email], async (signal) => {
  if (!session.user) {
    return null
  }
  const { data } = await request('/blog/me/rss-subscription', { signal })
  return data
})
const submitLabel = computed(() => {
  if (busy.value) {
    return '处理中…'
  }
  return subscription.data?.status === 0 ? '重发确认邮件' : '申请订阅'
})

async function update(cancel = false) {
  const userId = session.user?.userId
  const requestedEmail = email.value
  busy.value = true
  try {
    const { data } = await request('/blog/me/rss-subscription', {
      method: cancel ? 'DELETE' : 'PUT',
      body: cancel ? undefined : { email: requestedEmail },
    })
    if (session.user?.userId !== userId || email.value !== requestedEmail) {
      return
    }
    subscription.data = data
    if (cancel) {
      ElMessage.success('已取消邮件订阅。')
    } else if (data.status === 1) {
      ElMessage.success('当前邮箱已订阅。')
    } else {
      ElMessage.success('确认邮件已发送，请打开邮件中的链接完成邮箱验证。')
    }
    if (cancel) {
      await subscription.reload()
    }
  } catch (failure) {
    if (session.user?.userId === userId && email.value === requestedEmail) {
      ElMessage.error(failure.message)
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="bento-card account-sub-card">
    <div class="bento-card-head">
      <h2 class="bento-card-title">邮件订阅</h2>
      <span
        v-if="subscription.data"
        :class="[
          'status-pill',
          subscription.data.status === 1
            ? 'pill-success'
            : subscription.data.status === 0
              ? 'pill-warning'
              : 'pill-neutral',
        ]"
      >
        {{ ['等待邮箱验证', '已订阅', '已退订'][subscription.data.status] }}
      </span>
    </div>
    <ResourceState :resource="subscription" />
    <template v-if="!subscription.loading && !subscription.error">
      <div class="sub-compact-body">
        <div class="sub-compact-row">
          <span class="sub-email-text">
            {{ email || '请先在个人资料中设置账号邮箱' }}
          </span>
          <div class="sub-compact-actions">
            <a :href="`${apiRoot}/blog/rss.xml`" target="_blank" rel="noopener noreferrer" class="text-button">RSS ↗</a>
            <button
              v-if="can('blog:rss:subscribe')"
              type="button"
              class="primary"
              :disabled="busy || !email"
              @click="update(false)"
            >
              {{ submitLabel }}
            </button>
            <button
              v-if="can('blog:rss:unsubscribe') && subscription.data && subscription.data.status !== 2"
              type="button"
              :disabled="busy"
              @click="update(true)"
            >
              取消订阅
            </button>
          </div>
        </div>
        <p v-if="email && subscription.data?.status === 2 && subscription.data.email !== email" class="muted" role="status">
          邮箱已更换，请重新订阅。
        </p>
        <p v-if="!can('blog:rss:subscribe') && !can('blog:rss:unsubscribe')" class="muted">
          当前账号没有管理邮件订阅的权限。
        </p>
      </div>
    </template>
  </section>
</template>
