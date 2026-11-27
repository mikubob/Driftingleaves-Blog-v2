<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { getPage, isWebUrl, request } from '../api.js'
import { validatePassword } from '../shared/form.js'
import { can, logout, session, sessionRevoked } from '../session.js'
import { useResource } from '../composables/useResource.js'
import { articlePath, formatDate, formatDateTime } from '../format.js'
import EmailSubscription from '../components/EmailSubscription.vue'
import OAuthBindings from '../components/OAuthBindings.vue'
import ListPager from '../components/ListPager.vue'
import ResourceState from '../components/ResourceState.vue'
import RollingNumber from '../components/RollingNumber.vue'
import SafeHtml from '../components/SafeHtml.vue'

const router = useRouter()
const form = reactive({ nickname: '', email: '', emailCode: '', avatar: '', intro: '' })
const password = reactive({ current: '', next: '' })
const tab = ref('liked-articles')
const statusFilter = ref(null)
const page = ref(1)
const busy = ref(false)
const sendingEmailCode = ref(false)
const deleting = shallowRef(null)
const editOpen = ref(false)
const passwordOpen = ref(false)
const editFirstInput = ref(null)
const passwordFirstInput = ref(null)
const cachedCounts = reactive({ 'liked-articles': '0', comments: '0', messages: '0' })
let previousFocus = null

const STATUS_LABELS = ['待审核', '已通过', '未通过']
const STATUS_CLASSES = ['pill-warning', 'pill-success', 'pill-danger']
const TAB_INDEX = { 'liked-articles': 0, comments: 1, messages: 2 }

const profileAvatar = computed(() => me.data?.data?.avatar || session.user?.avatar || '')
const profileNickname = computed(() => me.data?.data?.nickname || session.user?.nickname || '')
const profileInitial = computed(() => [...profileNickname.value][0] || 'U')
const normalizedEmail = computed(() => form.email.trim().toLowerCase())
const needsEmailCode = computed(() => normalizedEmail.value !== ''
  && normalizedEmail.value !== (me.data?.data?.email || '').toLowerCase())

async function signOut() {
  try {
    await logout()
    await router.push('/')
  } catch (failure) {
    error.value = failure.message
  }
}

/** 加载当前登录用户的完整个人资料。 */
const me = useResource(() => session.user?.userId, async (signal) => {
  if (!session.user) {
    return null
  }
  const result = await request('/blog/me', { signal })
  Object.assign(form, {
    nickname: result.data.nickname,
    email: result.data.email || '',
    emailCode: '',
    avatar: result.data.avatar || '',
    intro: result.data.intro || '',
  })
  return result
})

/** 并发获取三类足迹总数，用于顶部 Bento 统计卡片展示。 */
const summaryCounts = useResource(() => session.user?.userId, async (signal) => {
  if (!session.user) {
    return null
  }
  const [likes, comments, messages] = await Promise.all([
    getPage('/blog/me/liked-articles', { page: 1, pageSize: 1 }, signal),
    getPage('/blog/me/comments', { page: 1, pageSize: 1 }, signal),
    getPage('/blog/me/messages', { page: 1, pageSize: 1 }, signal),
  ])
  return {
    'liked-articles': likes.total ?? '0',
    comments: comments.total ?? '0',
    messages: messages.total ?? '0',
  }
})

/** 按当前标签、状态筛选和页码加载足迹列表。 */
const activity = useResource(
  () => [session.user?.userId, tab.value, statusFilter.value, page.value],
  (signal) => {
    if (!session.user) {
      return null
    }
    const query = { page: page.value, pageSize: 10 }
    if (tab.value !== 'liked-articles' && statusFilter.value !== null) {
      query.status = statusFilter.value
    }
    return getPage(`/blog/me/${tab.value}`, query, signal)
  }
)

watch(
  () => summaryCounts.data,
  (next) => {
    if (!next) {
      return
    }
    cachedCounts['liked-articles'] = String(next['liked-articles'] ?? '0')
    cachedCounts.comments = String(next.comments ?? '0')
    cachedCounts.messages = String(next.messages ?? '0')
  },
  { immediate: true }
)

watch(
  () => activity.data,
  (next) => {
    if (next && (tab.value === 'liked-articles' || statusFilter.value === null)) {
      cachedCounts[tab.value] = String(next.total ?? '0')
    }
  }
)

watch(tab, () => {
  statusFilter.value = null
  page.value = 1
  deleting.value = null
})

watch(statusFilter, () => {
  page.value = 1
  deleting.value = null
})

function closeSheets() {
  editOpen.value = false
  passwordOpen.value = false
  if (previousFocus && typeof previousFocus.focus === 'function') {
    previousFocus.focus()
    previousFocus = null
  }
}

async function openEditSheet() {
  previousFocus = document.activeElement
  if (me.data?.data) {
    Object.assign(form, {
      nickname: me.data.data.nickname,
      email: me.data.data.email || '',
      emailCode: '',
      avatar: me.data.data.avatar || '',
      intro: me.data.data.intro || '',
    })
  }
  editOpen.value = true
  await nextTick()
  editFirstInput.value?.focus()
}

async function openPasswordSheet() {
  previousFocus = document.activeElement
  password.current = ''
  password.next = ''
  passwordOpen.value = true
  await nextTick()
  passwordFirstInput.value?.focus()
}

function onKeydown(event) {
  if (event.key !== 'Escape') {
    return
  }
  closeSheets()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

/** 向当前填写的新邮箱发送换绑验证码。 */
async function sendEmailChangeCode() {
  if (busy.value || sendingEmailCode.value || !needsEmailCode.value) {
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail.value)) {
    ElMessage.error('请输入有效的新邮箱。')
    return
  }
  sendingEmailCode.value = true
  try {
    await request('/blog/me/email-change-code', {
      method: 'POST',
      body: { email: normalizedEmail.value },
    })
    form.emailCode = ''
    ElMessage.success('验证码已发送到新邮箱。')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    sendingEmailCode.value = false
  }
}

/** 提交个人资料修改并保留并发版本校验。 */
async function saveProfile() {
  if (busy.value || sendingEmailCode.value) {
    return
  }
  if (!me.data?.revision) {
    ElMessage.warning('资料尚未加载完成，请稍后重试。')
    return
  }
  if (!form.nickname.trim() || [...form.nickname.trim()].length > 64
    || (form.avatar && !isWebUrl(form.avatar))) {
    ElMessage.error('请填写有效昵称，头像仅支持 HTTP(S) 地址。')
    return
  }
  busy.value = true
  try {
    const result = await request('/blog/me/profile', {
      method: 'PATCH', revision: me.data.revision,
      body: {
        nickname: form.nickname.trim(), email: form.email.trim() || null,
        emailCode: needsEmailCode.value ? form.emailCode : undefined,
        avatar: form.avatar || null, intro: form.intro || null,
      },
    })
    session.user = result.data
    me.data = result
    closeSheets()
    ElMessage.success('个人资料已更新。')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

/** 修改或设置本站登录密码，成功后跳转登录页重新认证。 */
async function changePassword() {
  if (!validatePassword(password.next)) {
    ElMessage.error('新密码为 8–64 个字符，UTF-8 不超过 72 字节。')
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
    closeSheets()
    sessionRevoked()
    await router.push('/login')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

/** 取消喜欢或删除本人评论、留言，并同步刷新列表与总数。 */
async function remove() {
  const item = deleting.value
  const path = tab.value === 'liked-articles'
    ? `/blog/articles/${item.article.articleId}/like`
    : `/blog/me/${tab.value}/${item.commentId || item.messageId}`
  busy.value = true
  try {
    await request(path, { method: 'DELETE' })
    deleting.value = null
    await Promise.all([activity.reload(), summaryCounts.reload()])
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

/** 拼接评论或留言的设备与属地信息。 */
function deviceMetaText(item) {
  const parts = []
  if (item.editedAt) {
    parts.push('已编辑')
  }
  if (item.location) {
    parts.push(item.location)
  }
  if (item.userAgentOs) {
    parts.push(item.userAgentOs)
  }
  if (item.userAgentBrowser) {
    parts.push(item.userAgentBrowser)
  }
  return parts.join(' · ')
}

/** 生成跳转到文章具体评论的路由对象。 */
function commentTarget(item) {
  const query = {}
  if (item.page) {
    query.discussionPage = item.page
  }
  if (item.rootParentId) {
    query.rootParentId = item.rootParentId
  }
  return {
    path: articlePath({ slug: item.articleSlug }),
    query,
    hash: `#comment-${item.commentId}`,
  }
}

/** 生成跳转到留言板具体留言的路由对象。 */
function messageTarget(item) {
  const query = {}
  if (item.page) {
    query.discussionPage = item.page
  }
  if (item.rootParentId) {
    query.rootParentId = item.rootParentId
  }
  return {
    path: '/message',
    query,
    hash: `#message-${item.messageId}`,
  }
}
</script>

<template>
  <section v-if="!session.user" class="panel empty-state">
    <h2>请先登录</h2>
    <router-link :to="{ path: '/login', query: { redirect: '/account' } }" class="button primary">去登录 →</router-link>
  </section>

  <div v-else class="account-page">
    <!-- 顶部 Bento 双栏工作台 -->
    <section class="account-bento-grid">
      <div class="bento-card profile-card">
        <div>
          <div class="profile-top">
            <div class="profile-id">
              <div class="profile-avatar-wrap">
                <img v-if="profileAvatar" :src="profileAvatar" :alt="profileNickname" class="profile-avatar-img" />
                <span v-else class="profile-avatar-letter">{{ profileInitial }}</span>
              </div>
              <div class="profile-identity">
                <h1 class="profile-name">{{ profileNickname }}</h1>
                <p class="profile-sub">
                  <span v-if="session.user.username">@{{ session.user.username }}</span>
                  <span v-if="session.user.username && (me.data?.data?.email || session.user.email)"> · </span>
                  <span v-if="me.data?.data?.email || session.user.email">{{ me.data?.data?.email || session.user.email }}</span>
                </p>
              </div>
            </div>
            <div class="profile-actions">
              <button type="button" @click="openEditSheet">编辑资料</button>
              <button type="button" @click="signOut">退出</button>
            </div>
          </div>

          <ResourceState :resource="me" />
          <p v-if="me.data" class="profile-bio">
            {{ me.data.data.intro || '这个人很安静，还没有写个人简介。' }}
          </p>
        </div>

        <div class="stats-row">
          <div class="stat-box">
            <div class="stat-num">
              <RollingNumber :value="cachedCounts['liked-articles']" />
            </div>
            <div class="stat-label">喜欢的文章</div>
          </div>
          <div class="stat-box">
            <div class="stat-num">
              <RollingNumber :value="cachedCounts.comments" />
            </div>
            <div class="stat-label">发表的评论</div>
          </div>
          <div class="stat-box">
            <div class="stat-num">
              <RollingNumber :value="cachedCounts.messages" />
            </div>
            <div class="stat-label">留言板足迹</div>
          </div>
        </div>
      </div>

      <div class="account-bento-stack">
        <OAuthBindings>
          <template #header-action>
            <button type="button" @click="openPasswordSheet">
              {{ session.user.hasPassword ? '修改密码' : '设置密码' }}
            </button>
          </template>
        </OAuthBindings>
        <EmailSubscription />
      </div>
    </section>

    <!-- 底部足迹舞台：分段切换 + 状态筛选 + 瀑布流/时间线 -->
    <section class="bento-card footprints-stage">
      <div class="stage-header">
        <div class="stage-title-wrap">
          <h2 class="stage-title">我留下的足迹</h2>
          <div
            class="segmented"
            role="tablist"
            aria-label="足迹分类"
            :style="{ '--seg-index': TAB_INDEX[tab] ?? 0 }"
          >
            <button
              type="button"
              role="tab"
              :aria-selected="tab === 'liked-articles'"
              :class="{ active: tab === 'liked-articles' }"
              @click="tab = 'liked-articles'"
            >
              喜欢的文章
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="tab === 'comments'"
              :class="{ active: tab === 'comments' }"
              @click="tab = 'comments'"
            >
              我的评论
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="tab === 'messages'"
              :class="{ active: tab === 'messages' }"
              @click="tab = 'messages'"
            >
              我的留言
            </button>
          </div>
        </div>

        <div v-if="tab !== 'liked-articles'" class="filter-pills" role="group" aria-label="审核状态筛选">
          <button
            type="button"
            :class="['filter-pill', { active: statusFilter === null }]"
            @click="statusFilter = null"
          >
            全部
          </button>
          <button
            type="button"
            :class="['filter-pill', { active: statusFilter === 1 }]"
            @click="statusFilter = 1"
          >
            已通过
          </button>
          <button
            type="button"
            :class="['filter-pill', { active: statusFilter === 0 }]"
            @click="statusFilter = 0"
          >
            待审核
          </button>
          <button
            type="button"
            :class="['filter-pill', { active: statusFilter === 2 }]"
            @click="statusFilter = 2"
          >
            未通过
          </button>
        </div>
      </div>

      <div v-if="activity.error" class="error-state" role="alert">
        <p>{{ activity.error }}</p>
        <button class="text-button" @click="activity.reload">重新加载 ↻</button>
      </div>

      <Transition v-else name="footprint-fade" mode="out-in">
        <!-- 加载中骨架屏，保持与真实列表一致的占位高度与布局 -->
        <div v-if="activity.loading" :key="`loading-${tab}`" class="footprint-skeleton" role="status" aria-label="正在载入">
          <div v-if="tab === 'liked-articles'" class="likes-masonry">
            <div v-for="i in 2" :key="i" class="like-masonry-card sk-footprint-card">
              <div class="sk-cover-box" />
              <div class="like-masonry-body">
                <div class="sk-line sk-w75" />
                <div class="sk-line sk-w55" />
              </div>
            </div>
          </div>
          <div v-else class="footprint-timeline">
            <div v-for="i in 3" :key="i" class="tl-item sk-footprint-card">
              <span class="tl-dot" aria-hidden="true"></span>
              <div class="sk-line sk-w40" />
              <div class="sk-line sk-w85" />
              <div class="sk-line sk-w55" />
            </div>
          </div>
        </div>

        <div v-else-if="activity.data" :key="`${tab}-${statusFilter}-${page}`">
          <p v-if="!activity.data.records.length" class="empty-state muted">这里还没有你的记录。</p>

          <!-- 喜欢的文章：双列瀑布流（有封面显示封面，无封面自然收紧） -->
          <div v-else-if="tab === 'liked-articles'" class="likes-masonry">
            <article
              v-for="item in activity.data.records"
              :key="item.article.articleId"
              class="like-masonry-card"
            >
              <router-link
                v-if="item.article.coverUrl"
                :to="articlePath(item.article)"
                class="like-masonry-cover"
              >
                <img :src="item.article.coverUrl" :alt="item.article.title" loading="lazy" />
              </router-link>
              <div class="like-masonry-body">
                <router-link :to="articlePath(item.article)" class="like-masonry-link">
                  <h3 class="like-masonry-title">{{ item.article.title }}</h3>
                  <p v-if="item.article.summary" class="like-masonry-summary">{{ item.article.summary }}</p>
                  <div class="like-masonry-meta">
                    <span v-if="item.article.category?.categoryName">{{ item.article.category.categoryName }}</span>
                    <span v-if="item.article.category?.categoryName">·</span>
                    <span>{{ item.article.viewCount ?? 0 }} 阅读</span>
                    <span>·</span>
                    <span>{{ item.article.commentCount ?? 0 }} 评论</span>
                  </div>
                </router-link>
                <div class="like-masonry-foot">
                  <span>喜欢于 {{ formatDate(item.likedAt) }}</span>
                  <button
                    v-if="can('blog:article:like')"
                    type="button"
                    class="btn-unlike"
                    @click="deleting = item"
                  >
                    取消喜欢
                  </button>
                </div>
                <div
                  v-if="deleting === item"
                  class="inline-confirm"
                  role="group"
                  aria-label="确认取消喜欢"
                >
                  <span>确认取消喜欢这篇文章？</span>
                  <div class="confirm-actions">
                    <button type="button" :disabled="busy" @click="remove">确认</button>
                    <button type="button" :disabled="busy" @click="deleting = null">取消</button>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <!-- 我的评论 / 我的留言：杂志时间线 -->
          <div v-else class="footprint-timeline">
            <article
              v-for="item in activity.data.records"
              :key="item.commentId || item.messageId"
              class="tl-item"
            >
              <span class="tl-dot" aria-hidden="true"></span>
              <div class="tl-head">
                <div class="tl-badges">
                  <template v-if="tab === 'comments'">
                    <router-link
                      v-if="item.articleSlug"
                      :to="commentTarget(item)"
                      class="article-link-pill"
                      :title="item.articleTitle || `文章 #${item.articleId}`"
                    >
                      <span class="article-link-title">{{ item.articleTitle ? `《${item.articleTitle}》` : `文章 #${item.articleId}` }}</span>
                      <span aria-hidden="true">↗</span>
                    </router-link>
                    <span v-else class="status-pill pill-tag">文章 #{{ item.articleId }}</span>
                  </template>
                  <template v-else>
                    <router-link :to="messageTarget(item)" class="article-link-pill">
                      <span class="article-link-title">留言板</span>
                      <span aria-hidden="true">↗</span>
                    </router-link>
                    <span v-if="item.isSecret === 1" class="status-pill pill-tag">悄悄话</span>
                  </template>
                  <span :class="['status-pill', STATUS_CLASSES[item.status] || 'pill-neutral']">
                    {{ STATUS_LABELS[item.status] || '未知状态' }}
                  </span>
                  <span v-if="tab === 'messages' && item.notifyReply === 1" class="status-pill pill-tag">
                    已开启邮件通知
                  </span>
                </div>
                <span class="tl-time">{{ formatDateTime(item.createTime) }}</span>
              </div>

              <div v-if="tab === 'comments' && item.parentId" class="tl-quote">
                回复评论 #{{ item.parentId }}
              </div>
              <div v-else-if="tab === 'messages' && (item.replyTarget || item.parentId)" class="tl-quote">
                回复 {{ item.replyTarget?.nickname ? `@${item.replyTarget.nickname}` : `#${item.parentId}` }}
              </div>

              <SafeHtml v-if="tab === 'messages' && item.htmlContent" :html="item.htmlContent" class="tl-content" />
              <p v-else class="tl-content">{{ item.content }}</p>

              <div class="tl-foot">
                <span>{{ deviceMetaText(item) || '—' }}</span>
                <button
                  v-if="can(tab === 'comments' ? 'blog:comment:deleteOwn' : 'blog:message:deleteOwn')"
                  type="button"
                  class="btn-unlike"
                  @click="deleting = item"
                >
                  删除
                </button>
              </div>

              <div
                v-if="deleting === item"
                class="inline-confirm"
                role="group"
                aria-label="确认删除"
              >
                <span>确认删除这条内容及其全部子回复？删除后无法恢复。</span>
                <div class="confirm-actions">
                  <button type="button" :disabled="busy" @click="remove">确认删除</button>
                  <button type="button" :disabled="busy" @click="deleting = null">取消</button>
                </div>
              </div>
            </article>
          </div>

          <ListPager :data="activity.data" @change="page = $event" />
        </div>
      </Transition>
    </section>

    <!-- Apple Sheet：编辑个人资料弹窗 -->
    <div v-if="editOpen" class="sheet-backdrop" @click.self="closeSheets">
      <div class="sheet-dialog" role="dialog" aria-modal="true" aria-labelledby="edit-sheet-title">
        <div class="sheet-head">
          <h2 id="edit-sheet-title" class="sheet-title">编辑个人资料</h2>
          <button type="button" class="sheet-close" aria-label="关闭" @click="closeSheets">✕</button>
        </div>
        <form @submit.prevent="saveProfile">
          <fieldset :disabled="busy || sendingEmailCode">
            <div class="form-grid">
              <div>
                <label for="profile-nickname">昵称</label>
                <input id="profile-nickname" ref="editFirstInput" v-model="form.nickname" maxlength="64" required />
              </div>
              <div>
                <label for="profile-email">绑定邮箱</label>
                <input
                  id="profile-email"
                  v-model.trim="form.email"
                  type="email"
                  maxlength="254"
                  :required="Boolean(me.data?.data?.email)"
                  @input="form.emailCode = ''"
                />
              </div>
            </div>
            <template v-if="needsEmailCode">
              <label for="profile-email-code">新邮箱验证码</label>
              <div class="form-grid">
                <input
                  id="profile-email-code"
                  v-model="form.emailCode"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  maxlength="6"
                  pattern="[0-9]{6}"
                  required
                />
                <button type="button" @click="sendEmailChangeCode">
                  {{ sendingEmailCode ? '正在发送…' : '发送验证码到新邮箱' }}
                </button>
              </div>
            </template>
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
            <div class="sheet-footer">
              <button type="button" :disabled="busy || me.loading" @click="me.reload">重新获取资料</button>
              <button type="button" :disabled="busy" @click="closeSheets">取消</button>
              <button class="primary" :disabled="busy || me.loading">保存资料</button>
            </div>
          </fieldset>
        </form>
      </div>
    </div>

    <!-- Apple Sheet：设置 / 修改密码弹窗 -->
    <div v-if="passwordOpen" class="sheet-backdrop" @click.self="closeSheets">
      <div class="sheet-dialog" role="dialog" aria-modal="true" aria-labelledby="password-sheet-title">
        <div class="sheet-head">
          <div class="sheet-title-wrap">
            <h2 id="password-sheet-title" class="sheet-title">
              {{ session.user.hasPassword ? '修改本站密码' : '设置本站密码' }}
            </h2>
            <span
              class="info-tip"
              tabindex="0"
              role="note"
              aria-label="修改成功后需重新登录；操作要求最近 5 分钟内完成认证。"
              data-tip="修改成功后需重新登录；操作要求最近 5 分钟内完成认证。"
            >?</span>
          </div>
          <button type="button" class="sheet-close" aria-label="关闭" @click="closeSheets">✕</button>
        </div>
        <form @submit.prevent="changePassword">
          <fieldset :disabled="busy">
            <template v-if="session.user.hasPassword">
              <label for="current-password">当前密码</label>
              <input
                id="current-password"
                ref="passwordFirstInput"
                v-model="password.current"
                type="password"
                autocomplete="current-password"
                required
              />
            </template>
            <label for="new-password">新密码</label>
            <input
              id="new-password"
              :ref="session.user.hasPassword ? undefined : (el) => { passwordFirstInput = el }"
              v-model="password.next"
              type="password"
              autocomplete="new-password"
              placeholder="8–64 个字符"
              required
            />
            <div class="sheet-footer">
              <button type="button" :disabled="busy" @click="closeSheets">取消</button>
              <button class="primary" :disabled="busy">保存新密码</button>
            </div>
          </fieldset>
        </form>
      </div>
    </div>
  </div>
</template>
