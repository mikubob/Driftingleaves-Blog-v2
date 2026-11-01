<script setup>
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getPage, isWebUrl, request, validatePassword } from '../api.js'
import { can, clearSession, logout, session } from '../session.js'
import { articleView } from '../api/presentation.js'
import { useResource } from '../composables/useResource.js'
import { formatDate } from '../format.js'
import ArticleCard from '../components/ArticleCard.vue'
import EmailSubscription from '../components/EmailSubscription.vue'
import ListPager from '../components/ListPager.vue'
import ResourceState from '../components/ResourceState.vue'
import CaptchaField from '../../../shared/CaptchaField.vue'
const router = useRouter()
const form = reactive({ nickname: '', email: '', avatar: '', intro: '' })
const password = reactive({ current: '', next: '' })
const tab = ref('liked-articles')
const page = ref(1)
const error = ref('')
const feedback = ref('')
const busy = ref(false)
const deleting = ref(null)
const editing = ref(null)
const messageCaptcha = ref(null)
const editForm = reactive({ content: '', contentFormat: 0, notifyReply: false, revision: null })

async function signOut() {
  try {
    await logout()
    await router.push('/')
  } catch (failure) {
    error.value = failure.message
  }
}
const me = useResource(() => session.user?.userId, async (signal) => {
  if (!session.user) {
    return null
  }
  const result = await request('/blog/me', { signal })
  Object.assign(form, {
    nickname: result.data.nickname,
    email: result.data.email || '',
    avatar: result.data.avatar || '',
    intro: result.data.intro || '',
  })
  return result
})
const activity = useResource(() => [session.user?.userId, tab.value, page.value], (signal) => {
  if (!session.user) {
    return null
  }
  return getPage(`/blog/me/${tab.value}`, { page: page.value, pageSize: 10 }, signal)
})
watch(tab, () => {
  page.value = 1
  editing.value = null
  deleting.value = null
})

async function saveProfile() {
  error.value = ''
  feedback.value = ''
  if (!form.nickname.trim() || [...form.nickname.trim()].length > 64
    || (form.avatar && !isWebUrl(form.avatar))) {
    error.value = '请填写有效昵称，头像仅支持 HTTP(S) 地址。'
    return
  }
  busy.value = true
  try {
    const result = await request('/blog/me/profile', {
      method: 'PATCH', revision: me.data.revision,
      body: {
        nickname: form.nickname.trim(), email: form.email.trim() || null,
        avatar: form.avatar || null, intro: form.intro || null,
      },
    })
    session.user = result.data
    me.data = result
    feedback.value = '个人资料已更新。'
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

async function changePassword() {
  error.value = ''
  if (!validatePassword(password.next)) {
    error.value = '新密码为 8–64 个字符，UTF-8 不超过 72 字节。'
    return
  }
  busy.value = true
  try {
    const body = { newPassword: password.next }
    if (session.user.hasPassword) {
      body.currentPassword = password.current
    }
    await request('/blog/me/password', { method: 'PUT', body })
    password.current = ''
    password.next = ''
    clearSession()
    await router.push('/login')
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

async function remove() {
  const item = deleting.value
  const path = tab.value === 'liked-articles'
    ? `/blog/articles/${item.article.articleId}/like`
    : `/blog/me/${tab.value}/${item.commentId || item.messageId}`
  busy.value = true
  error.value = ''
  try {
    await request(path, { method: 'DELETE' })
    deleting.value = null
    await activity.reload()
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

async function editMessage(item) {
  error.value = ''
  busy.value = true
  try {
    const result = await request(`/blog/me/messages/${item.messageId}`)
    editForm.content = result.data.content
    editForm.contentFormat = result.data.contentFormat
    editForm.notifyReply = result.data.notifyReply === 1
    editForm.revision = result.revision
    editing.value = item.messageId
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

async function saveMessage() {
  error.value = ''
  if (!editForm.content.trim() || [...editForm.content.trim()].length > 2000) {
    error.value = '留言需为 1–2000 个字符。'
    return
  }
  busy.value = true
  try {
    await request(`/blog/me/messages/${editing.value}`, {
      method: 'PUT', revision: editForm.revision,
      captcha: messageCaptcha.value.getValue(),
      body: {
        content: editForm.content.trim(), contentFormat: editForm.contentFormat,
        notifyReply: editForm.notifyReply ? 1 : 0,
      },
    })
    editing.value = null
    feedback.value = '留言已更新，审核状态以服务端处理为准。'
    await activity.reload()
  } catch (failure) {
    error.value = failure.message
  } finally {
    if (editing.value) {
      messageCaptcha.value?.refresh()
    }
    busy.value = false
  }
}
</script>

<template>
  <section v-if="!session.user" class="panel empty-state">
    <h2>登录后，和这里保持联系</h2>
    <p class="muted">管理资料、回看互动和订阅新的文章。</p>
    <router-link :to="{ path: '/login', query: { redirect: '/account' } }" class="button primary">去登录 →</router-link>
  </section>
  <template v-else>
    <p v-if="error" role="alert" class="error-state">{{ error }}</p>
    <p v-if="feedback" role="status" class="success-text">{{ feedback }}</p>
    <section class="panel">
      <p class="eyebrow">MY CORNER</p>
      <h2>你好，{{ session.user.nickname }}</h2>
      <button class="text-button" @click="signOut">退出登录</button>
      <ResourceState :resource="me" />
      <form v-if="me.data" @submit.prevent="saveProfile">
        <fieldset :disabled="busy">
          <div class="form-grid">
            <div>
              <label for="profile-nickname">昵称</label>
              <input id="profile-nickname" v-model="form.nickname" maxlength="64" required />
            </div>
            <div>
              <label for="profile-email">联系邮箱</label>
              <input id="profile-email" v-model="form.email" type="email" maxlength="254" />
            </div>
          </div>
          <label for="profile-avatar">头像 URL</label>
          <input
            id="profile-avatar"
            v-model="form.avatar"
            type="url"
            maxlength="2048"
            placeholder="https://"
          />
          <label for="profile-intro">个人简介</label>
          <textarea id="profile-intro" v-model="form.intro" maxlength="500" rows="3"></textarea>
          <div class="form-footer">
            <button type="button" :disabled="busy" @click="me.reload">重新获取资料</button>
            <button class="primary" :disabled="busy">保存资料</button>
          </div>
        </fieldset>
      </form>
      <details class="password-settings">
        <summary>设置 / 修改本站密码</summary>
        <form @submit.prevent="changePassword">
          <fieldset :disabled="busy">
            <template v-if="session.user.hasPassword">
              <label for="current-password">当前密码</label>
              <input
                id="current-password"
                v-model="password.current"
                type="password"
                autocomplete="current-password"
                required
              />
            </template>
            <label for="new-password">新密码</label>
            <input
              id="new-password"
              v-model="password.next"
              type="password"
              autocomplete="new-password"
              required
            />
            <p class="form-hint">修改成功后需重新登录；操作要求最近 5 分钟内完成认证。</p>
            <button :disabled="busy">保存新密码</button>
          </fieldset>
        </form>
      </details>
    </section>
    <EmailSubscription />
    <section class="panel">
      <h2>我留下的足迹</h2>
      <div class="browse-tabs">
        <button :class="{ selected: tab === 'liked-articles' }" @click="tab = 'liked-articles'">喜欢的文章</button>
        <button :class="{ selected: tab === 'comments' }" @click="tab = 'comments'">我的评论</button>
        <button :class="{ selected: tab === 'messages' }" @click="tab = 'messages'">我的留言</button>
      </div>
      <ResourceState :resource="activity" />
      <template v-if="activity.data">
        <div
          v-for="item in activity.data.records"
          :key="item.article?.articleId || item.commentId || item.messageId"
          class="activity-item"
        >
          <template v-if="tab === 'liked-articles'">
            <ArticleCard :article="articleView(item.article)" />
            <p class="muted">喜欢于 {{ formatDate(item.likedAt) }}</p>
            <button v-if="can('blog:article:like')" class="text-button" @click="deleting = item">取消喜欢</button>
          </template>
          <template v-else>
            <p class="prose-text">{{ item.content }}</p>
            <p class="muted">
              {{ formatDate(item.createTime) }}
              ·
              {{ ['待审核', '已通过', '未通过', '已删除'][item.status] }}
              <span v-if="item.isSecret === 1">· 悄悄话</span>
            </p>
            <div class="row">
              <button
                v-if="can(tab === 'comments' ? 'blog:comment:deleteOwn' : 'blog:message:deleteOwn')"
                class="text-button"
                @click="deleting = item"
              >
                删除
              </button>
              <button
                v-if="tab === 'messages' && item.status !== 3 && can('blog:message:updateOwn')"
                class="text-button"
                :disabled="busy"
                @click="editMessage(item)"
              >
                编辑留言
              </button>
            </div>
          </template>
          <form
            v-if="editing === item.messageId && tab === 'messages'"
            class="edit-message"
            @submit.prevent="saveMessage"
          >
            <fieldset :disabled="busy">
              <label for="edit-content">编辑留言</label>
              <textarea id="edit-content" v-model="editForm.content" rows="4" maxlength="2000" required></textarea>
              <CaptchaField
                :ref="field => messageCaptcha = field"
                :request="request"
                endpoint="/blog/auth/captcha"
                :disabled="busy"
              />
              <div class="message-options">
                <label>
                  格式
                  <select v-model="editForm.contentFormat">
                    <option :value="0">纯文本</option>
                    <option :value="1">Markdown</option>
                  </select>
                </label>
                <label class="checkbox">
                  <input v-model="editForm.notifyReply" type="checkbox" />
                  请求回复通知
                </label>
              </div>
              <div class="row">
                <button class="primary" :disabled="busy || !messageCaptcha?.ready">保存修改</button>
                <button type="button" :disabled="busy" @click="editing = null">取消</button>
                <button type="button" :disabled="busy" @click="editMessage(item)">重新获取详情</button>
              </div>
            </fieldset>
          </form>
        </div>
        <p v-if="!activity.data.records.length" class="empty-state muted">这里还没有你的记录。</p>
        <ListPager :data="activity.data" @change="page = $event" />
      </template>
      <div v-if="deleting" class="inline-confirm" role="group" aria-label="确认操作">
        <span>{{ tab === 'liked-articles' ? '确认取消喜欢？' : '确认删除这条内容及其全部子回复？删除后无法恢复。' }}</span>
        <button :disabled="busy" @click="remove">确认</button>
        <button :disabled="busy" @click="deleting = null">取消</button>
      </div>
    </section>
  </template>
</template>
