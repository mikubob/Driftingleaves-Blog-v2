<script setup>
import { computed, inject, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import { getPage, isWebUrl, pageNumber, request } from '@/api.js'
import { formatDateTime } from '@/format.js'
import { session } from '@/session.js'
import { useBlogStore } from '@/stores'
import { useResource } from '@/composables/useResource.js'
import SidebarCard from '@/components/SidebarCard.vue'
import ListPager from '@/components/ListPager.vue'
import { safeLink } from '@shared/links.mjs'

const ATTACHMENT_API_PATH = /^\/api\/v1\/(?:blog|admin)\/(?:articles\/[^/]+\/)?files(?:\/|$)/
const STATUS_LABELS = ['待审核', '已通过 · 展示中', '未通过 / 已下架']
const STATUS_CLASSES = ['pill-warning', 'pill-success', 'pill-danger']

const route = useRoute()
const router = useRouter()
const blogStore = useBlogStore()
const { articleTitle, articleMeta } = inject('setHero')
articleTitle.value = '友链'
articleMeta.value = '朋友们的小站'

const sheetOpen = ref(false)
const submitting = ref(false)
const deleting = ref(false)
const confirmingDelete = ref(false)
const uncertainSubmit = ref(false)
const sheetDialogRef = ref(null)
const firstInputRef = ref(null)
const previewAvatarBroken = ref(false)
const ownAvatarBroken = ref(false)
const brokenLinkAvatars = reactive({})
let previousFocus = null

const form = reactive({
  linkName: '',
  url: '',
  avatar: '',
  description: '',
})

const fieldErrors = reactive({
  linkName: '',
  url: '',
  avatar: '',
  description: '',
})

/** 分页加载公开友链列表。 */
const listing = useResource(
  () => route.query.page,
  (signal) => getPage('/blog/links', { page: pageNumber(route.query.page), pageSize: 20 }, signal)
)

/** 查询当前登录用户的友链申请，未登录时不发起请求。 */
const application = useResource(
  () => session.user?.userId,
  async (signal) => {
    if (!session.user) {
      return null
    }
    const { data } = await request('/blog/me/link-application', { signal })
    return data
  }
)

const links = computed(() =>
  listing.data?.records.map((item) => ({
    ...item,
    id: item.linkId,
    name: item.linkName,
    avatarUrl: item.avatar,
  })) || []
)

const totalFriends = computed(() => listing.data?.total ?? String(links.value.length))
const loading = computed(() => listing.loading)
const ownLink = computed(() => application.data)
const nameCount = computed(() => [...form.linkName.trim()].length)
const descCount = computed(() => [...form.description.trim()].length)

/** 汇总本站公开友链名片信息，供访客互换友链时一键复制。 */
const siteCard = computed(() => {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  const name = blogStore.site?.title || blogStore.personalInfo?.nickname || 'DriftingLeaves'
  const url = blogStore.personalInfo?.website || origin
  const avatar = blogStore.personalInfo?.avatar || (origin ? `${origin}/favicon.svg` : '')
  const description = blogStore.site?.description || blogStore.personalInfo?.description || blogStore.personalInfo?.tag || ''
  return { name, url, avatar, description }
})

/** 实时预览卡片数据，输入为空时显示占位文字。 */
const previewCard = computed(() => {
  const name = form.linkName.trim() || '友站名称预览'
  const description = form.description.trim() || '一句话介绍你的站点内容与风格'
  const rawAvatar = form.avatar.trim()
  const avatarUrl = isValidExternalUrl(rawAvatar) && !previewAvatarBroken.value ? rawAvatar : ''
  return {
    name,
    initial: [...name][0] || '?',
    description,
    avatarUrl,
  }
})

watch(() => form.avatar, () => {
  previewAvatarBroken.value = false
})

watch(() => ownLink.value?.avatar, () => {
  ownAvatarBroken.value = false
})

watch(() => session.user?.userId, () => {
  confirmingDelete.value = false
  resetForm()
  closeApplySheet(true)
})

/**
 * 校验友链地址或头像地址，拒绝控制字符、凭据及本站文件接口路径。
 * @param {string} value 待检查的 URL 字符串
 * @returns {boolean}
 */
function isValidExternalUrl(value) {
  if (typeof value !== 'string' || !value) {
    return false
  }
  if ([...value].length > 2048 || /[\u0000-\u001F\u007F-\u009F]/.test(value)) {
    return false
  }
  if (!isWebUrl(value)) {
    return false
  }
  try {
    const parsed = new URL(value)
    const decodedPath = decodeURIComponent(parsed.pathname)
    return !ATTACHMENT_API_PATH.test(parsed.pathname) && !ATTACHMENT_API_PATH.test(decodedPath)
  } catch {
    return false
  }
}

/** 清理表单字段级错误提示。 */
function clearFieldErrors() {
  fieldErrors.linkName = ''
  fieldErrors.url = ''
  fieldErrors.avatar = ''
  fieldErrors.description = ''
}

/** 重置申请表单输入与错误状态。 */
function resetForm() {
  form.linkName = ''
  form.url = ''
  form.avatar = ''
  form.description = ''
  previewAvatarBroken.value = false
  clearFieldErrors()
}

/**
 * 校验单个字段或整个申请表单。
 * @param {'linkName' | 'url' | 'avatar' | 'description'} [targetField]
 * @returns {boolean}
 */
function validateForm(targetField) {
  const name = form.linkName.trim()
  const url = form.url.trim()
  const avatar = form.avatar.trim()
  const description = form.description.trim()

  if (!targetField || targetField === 'linkName') {
    if (!name || [...name].length > 100) {
      fieldErrors.linkName = '请填写 1–100 个字符的友站名称。'
    } else {
      fieldErrors.linkName = ''
    }
  }

  if (!targetField || targetField === 'url') {
    if (!isValidExternalUrl(url)) {
      fieldErrors.url = '友链地址无效，请填写以 http:// 或 https:// 开头的外部地址。'
    } else {
      fieldErrors.url = ''
    }
  }

  if (!targetField || targetField === 'avatar') {
    if (avatar && !isValidExternalUrl(avatar)) {
      fieldErrors.avatar = '友链头像地址无效，请填写以 http:// 或 https:// 开头的外部图片地址。'
    } else {
      fieldErrors.avatar = ''
    }
  }

  if (!targetField || targetField === 'description') {
    if (description && [...description].length > 255) {
      fieldErrors.description = '友站简介不能超过 255 个字符。'
    } else {
      fieldErrors.description = ''
    }
  }

  return !fieldErrors.linkName && !fieldErrors.url && !fieldErrors.avatar && !fieldErrors.description
}

/** 打开申请友链弹窗并聚焦首个输入框。 */
async function openApplySheet() {
  if (!session.user || application.loading || ownLink.value) {
    return
  }
  previousFocus = document.activeElement
  clearFieldErrors()
  sheetOpen.value = true
  await nextTick()
  firstInputRef.value?.focus()
}

/**
 * 关闭申请友链弹窗并恢复触发前焦点。
 * @param {boolean} [force] 申请结果已知或会话变更时强制关闭
 */
function closeApplySheet(force = false) {
  if (submitting.value && !force) {
    return
  }
  sheetOpen.value = false
  if (force) {
    uncertainSubmit.value = false
  }
  if (previousFocus && typeof previousFocus.focus === 'function') {
    previousFocus.focus()
    previousFocus = null
  }
}

/** 复制本站友链信息到剪贴板。 */
async function copySiteInfo() {
  const lines = [
    `名称：${siteCard.value.name}`,
    `地址：${siteCard.value.url}`,
  ]
  if (siteCard.value.avatar) {
    lines.push(`头像：${siteCard.value.avatar}`)
  }
  if (siteCard.value.description) {
    lines.push(`简介：${siteCard.value.description}`)
  }
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    ElMessage.success('已复制本站友链信息。')
  } catch {
    ElMessage.error('浏览器未授权剪贴板访问，请手动复制本站地址。')
  }
}

/** 提交本人友链申请，成功后更新页面申请状态。 */
async function submitApplication() {
  // 1. 校验登录状态与表单输入
  if (submitting.value || uncertainSubmit.value || !session.user) {
    return
  }
  if (!validateForm()) {
    return
  }

  // 2. 构造契约字段请求体并发送 POST 请求
  submitting.value = true
  const body = {
    linkName: form.linkName.trim(),
    url: form.url.trim(),
    avatar: form.avatar.trim() || null,
    description: form.description.trim() || null,
  }
  try {
    const result = await request('/blog/me/link-application', {
      method: 'POST',
      body,
    })
    application.data = result.data
    resetForm()
    closeApplySheet(true)
    ElMessage.success('友链申请已提交，正在等待审核。')
  } catch (failure) {
    // 3. 处理会话失效、状态冲突、限流与网络结果未决场景
    if (!sheetOpen.value) {
      ElMessage.error(failure.message)
      return
    }
    ElMessage.error(failure.message)
    if (failure.status === 409 && failure.code === 'STATE_CONFLICT') {
      await application.reload()
      if (application.data) {
        closeApplySheet(true)
      }
    } else if (!failure.status) {
      uncertainSubmit.value = true
    }
  } finally {
    submitting.value = false
  }
}

/** 提交超时或网络中断后，先查询本人友链申请确认是否已创建成功。 */
async function confirmSubmitResult() {
  if (submitting.value || application.loading) {
    return
  }
  await application.reload()
  if (application.data) {
    resetForm()
    closeApplySheet(true)
    ElMessage.success('已确认友链申请提交成功。')
  } else if (!application.error) {
    ElMessage.warning('暂未查到已生效的申请，请核对表单后重新提交。')
    uncertainSubmit.value = false
  } else {
    ElMessage.error(application.error)
  }
}

/** 删除本人的友链申请，若原先处于展示状态则同步刷新公开列表。 */
async function removeApplication() {
  const current = ownLink.value
  if (!current || deleting.value) {
    return
  }
  const wasPublic = current.status === 1
  deleting.value = true
  try {
    await request(`/blog/me/link-application/${current.linkId}`, {
      method: 'DELETE',
    })
    application.data = null
    confirmingDelete.value = false
    ElMessage.success('友链申请已删除。')
    if (wasPublic) {
      await listing.reload()
    }
  } catch (failure) {
    if (failure.status === 404) {
      confirmingDelete.value = false
      await Promise.all([application.reload(), listing.reload()])
    }
    ElMessage.error(failure.message)
  } finally {
    deleting.value = false
  }
}

function changePage(page) {
  router.push({ query: { page: String(page) } })
}

/** 处理弹窗 Escape 关闭与 Tab 焦点环绕。 */
function onKeydown(event) {
  if (!sheetOpen.value) {
    return
  }
  if (event.key === 'Escape') {
    closeApplySheet()
    return
  }
  if (event.key !== 'Tab' || !sheetDialogRef.value) {
    return
  }
  const focusables = [...sheetDialogRef.value.querySelectorAll(
    'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
  )]
  if (!focusables.length) {
    return
  }
  const first = focusables[0]
  const last = focusables[focusables.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="links-page">
    <div class="links-layout">
      <div class="links-main">
        <div class="content-card">
          <!-- 顶部标题与申请操作栏 -->
          <div class="card-header">
            <div class="header-left">
              <i class="iconfont icon-lianjie" />
              <span>共 {{ totalFriends }} 位朋友</span>
              <span
                class="info-tip"
                tabindex="0"
                role="note"
                aria-label="每个账号可保留一条友链申请，自然日最多提交 3 次（00:00 重置），删除申请不返还次数。"
                data-tip="每个账号可保留一条友链申请，自然日最多提交 3 次（00:00 重置），删除申请不返还次数。"
              >?</span>
            </div>

            <div class="header-actions">
              <button type="button" class="btn-secondary btn-sm" @click="copySiteInfo">
                复制本站信息
              </button>
              <router-link
                v-if="!session.user"
                :to="{ path: '/login', query: { redirect: route.fullPath } }"
                class="btn-primary btn-sm"
              >
                登录后申请友链 →
              </router-link>
              <template v-else-if="application.loading">
                <span class="status-pill pill-neutral" role="status">正在查询申请…</span>
              </template>
              <template v-else-if="ownLink">
                <span :class="['status-pill', STATUS_CLASSES[ownLink.status] || 'pill-neutral']">
                  <span class="status-dot" aria-hidden="true" />
                  {{ STATUS_LABELS[ownLink.status] || '未知状态' }}
                </span>
              </template>
              <button
                v-else-if="!application.error"
                type="button"
                class="btn-primary btn-sm"
                @click="openApplySheet"
              >
                申请友链
              </button>
            </div>
          </div>

          <!-- 操作反馈与错误提示 -->
          <div v-if="session.user && application.error" role="alert" class="status-banner error-banner">
            <span>{{ application.error }}</span>
            <button type="button" class="btn-secondary btn-sm" @click="application.reload">重试</button>
          </div>

          <!-- 本人友链申请状态横幅 -->
          <section v-if="session.user && ownLink" class="own-app-banner" aria-label="我的友链申请">
            <div class="own-app-main">
              <div class="own-app-site">
                <img
                  v-if="ownLink.avatar && !ownAvatarBroken"
                  :src="ownLink.avatar"
                  :alt="ownLink.linkName"
                  class="link-avatar own-avatar"
                  loading="lazy"
                  @error="ownAvatarBroken = true"
                />
                <span v-else class="link-avatar-letter own-avatar">
                  {{ ownLink.linkName ? [...ownLink.linkName][0] : '?' }}
                </span>
                <div class="own-app-meta">
                  <div class="own-app-title-row">
                    <a
                      :href="safeLink(ownLink.url)"
                      target="_blank"
                      rel="noopener noreferrer"
                      class="own-app-name"
                    >
                      {{ ownLink.linkName }}
                    </a>
                    <span :class="['status-pill', STATUS_CLASSES[ownLink.status] || 'pill-neutral']">
                      <span class="status-dot" aria-hidden="true" />
                      {{ STATUS_LABELS[ownLink.status] || '未知状态' }}
                    </span>
                  </div>
                  <p class="own-app-sub">
                    <span>{{ ownLink.url }}</span>
                    <template v-if="ownLink.description">
                      <span> · </span>
                      <span>{{ ownLink.description }}</span>
                    </template>
                  </p>
                  <p class="own-app-time">
                    提交于 {{ formatDateTime(ownLink.createTime) }}
                  </p>
                </div>
              </div>
              <div class="own-app-actions">
                <button
                  v-if="!confirmingDelete"
                  type="button"
                  class="btn-danger-ghost btn-sm"
                  :disabled="deleting"
                  @click="confirmingDelete = true"
                >
                  删除申请
                </button>
              </div>
            </div>

            <!-- 行内删除确认 -->
            <div
              v-if="confirmingDelete"
              class="inline-confirm"
              role="group"
              aria-label="确认删除友链申请"
            >
              <span>
                确认删除当前友链申请？删除后无法恢复，且不返还今日剩余申请次数{{ ownLink.status === 1 ? '；当前公开展示的友链也将立即移除' : '' }}。
              </span>
              <div class="confirm-actions">
                <button
                  type="button"
                  class="btn-primary btn-sm"
                  :disabled="deleting"
                  @click="removeApplication"
                >
                  {{ deleting ? '删除中…' : '确认删除' }}
                </button>
                <button
                  type="button"
                  class="btn-secondary btn-sm"
                  :disabled="deleting"
                  @click="confirmingDelete = false"
                >
                  取消
                </button>
              </div>
            </div>
          </section>

          <!-- 公开友链列表 -->
          <div v-if="listing.error" role="alert" class="status-banner error-banner">
            <span>{{ listing.error }}</span>
            <button type="button" class="btn-secondary btn-sm" @click="listing.reload">重试</button>
          </div>
          <div v-if="loading" class="placeholder-grid" role="status" aria-label="正在加载友链">
            <div v-for="i in 6" :key="i" class="sk-card" />
          </div>
          <div v-else-if="links.length" class="link-grid">
            <a
              v-for="link in links"
              :key="link.id"
              :href="safeLink(link.url)"
              target="_blank"
              rel="noopener noreferrer"
              class="link-card"
            >
              <img
                v-if="link.avatarUrl && !brokenLinkAvatars[link.id]"
                :src="link.avatarUrl"
                :alt="link.name"
                class="link-avatar"
                loading="lazy"
                @error="brokenLinkAvatars[link.id] = true"
              />
              <span v-else class="link-avatar-letter">
                {{ link.name ? [...link.name][0] : '?' }}
              </span>
              <div class="link-body">
                <p class="link-name">{{ link.name }}</p>
                <p class="link-desc">{{ link.description || link.url }}</p>
              </div>
            </a>
          </div>
          <p v-else-if="!listing.error" class="empty">暂无友链</p>
          <ListPager v-if="listing.data" :data="listing.data" @change="changePage" />
        </div>
      </div>
      <SidebarCard />
    </div>

    <!-- Apple Sheet：申请友链模态窗 -->
    <div v-if="sheetOpen" class="sheet-backdrop" @click.self="closeApplySheet()">
      <div
        ref="sheetDialogRef"
        class="sheet-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="apply-sheet-title"
      >
        <div class="sheet-head">
          <div class="sheet-title-wrap">
            <h2 id="apply-sheet-title" class="sheet-title">申请友链</h2>
            <span
              class="info-tip"
              tabindex="0"
              role="note"
              aria-label="提交后进入待审核状态，审核通过后展示在公开友链列表；自然日最多提交 3 次。"
              data-tip="提交后进入待审核状态，审核通过后展示在公开友链列表；自然日最多提交 3 次。"
            >?</span>
          </div>
          <button
            type="button"
            class="sheet-close"
            :disabled="submitting"
            aria-label="关闭"
            @click="closeApplySheet()"
          >
            ✕
          </button>
        </div>

        <!-- 本站友链信息条 -->
        <div class="site-copy-bar">
          <div class="site-copy-items">
            <span>本站友链：</span>
            <span class="site-copy-chip">{{ siteCard.name }}</span>
            <span v-if="siteCard.url" class="site-copy-chip">{{ siteCard.url }}</span>
          </div>
          <button type="button" class="btn-secondary btn-sm" @click="copySiteInfo">
            复制本站信息
          </button>
        </div>

        <!-- 实时 1:1 友链卡片预览 -->
        <div class="preview-box" aria-label="友链卡片实时预览">
          <div class="preview-head">
            <span>卡片实时预览</span>
            <span class="status-pill pill-warning">
              <span class="status-dot" aria-hidden="true" />
              待审核预览
            </span>
          </div>
          <div class="link-card preview-card">
            <img
              v-if="previewCard.avatarUrl"
              :src="previewCard.avatarUrl"
              alt=""
              class="link-avatar"
              @error="previewAvatarBroken = true"
            />
            <span v-else class="link-avatar-letter">
              {{ previewCard.initial }}
            </span>
            <div class="link-body">
              <p class="link-name">{{ previewCard.name }}</p>
              <p class="link-desc">{{ previewCard.description }}</p>
            </div>
          </div>
        </div>

        <form novalidate @submit.prevent="submitApplication">
          <fieldset :disabled="submitting">
            <div class="form-grid">
              <div class="form-field">
                <div class="field-label-row">
                  <label for="link-apply-name">友站名称</label>
                  <span :class="['char-count', { 'over-limit': nameCount > 100 }]">
                    {{ nameCount }}/100
                  </span>
                </div>
                <input
                  id="link-apply-name"
                  ref="firstInputRef"
                  v-model="form.linkName"
                  :class="['form-input', { 'form-input-error': fieldErrors.linkName }]"
                  :aria-invalid="!!fieldErrors.linkName"
                  :aria-describedby="fieldErrors.linkName ? 'link-apply-name-error' : undefined"
                  maxlength="100"
                  placeholder="例如：秋日来信的小站"
                  required
                  @blur="validateForm('linkName')"
                />
                <p v-if="fieldErrors.linkName" id="link-apply-name-error" class="field-error" role="alert">
                  {{ fieldErrors.linkName }}
                </p>
              </div>

              <div class="form-field">
                <div class="field-label-row">
                  <label for="link-apply-url">友站地址</label>
                </div>
                <input
                  id="link-apply-url"
                  v-model="form.url"
                  type="url"
                  :class="['form-input', { 'form-input-error': fieldErrors.url }]"
                  :aria-invalid="!!fieldErrors.url"
                  :aria-describedby="fieldErrors.url ? 'link-apply-url-error' : undefined"
                  maxlength="2048"
                  placeholder="https://example.com"
                  required
                  @blur="validateForm('url')"
                />
                <p v-if="fieldErrors.url" id="link-apply-url-error" class="field-error" role="alert">
                  {{ fieldErrors.url }}
                </p>
              </div>
            </div>

            <div class="form-field">
              <div class="field-label-row">
                <label for="link-apply-avatar">头像地址（选填）</label>
              </div>
              <input
                id="link-apply-avatar"
                v-model="form.avatar"
                type="url"
                :class="['form-input', { 'form-input-error': fieldErrors.avatar }]"
                :aria-invalid="!!fieldErrors.avatar"
                :aria-describedby="fieldErrors.avatar ? 'link-apply-avatar-error' : undefined"
                maxlength="2048"
                placeholder="https://example.com/avatar.png"
                @blur="validateForm('avatar')"
              />
              <p v-if="fieldErrors.avatar" id="link-apply-avatar-error" class="field-error" role="alert">
                {{ fieldErrors.avatar }}
              </p>
            </div>

            <div class="form-field">
              <div class="field-label-row">
                <label for="link-apply-desc">友站简介（选填）</label>
                <span :class="['char-count', { 'over-limit': descCount > 255 }]">
                  {{ descCount }}/255
                </span>
              </div>
              <textarea
                id="link-apply-desc"
                v-model="form.description"
                rows="2"
                :class="['form-textarea', { 'form-input-error': fieldErrors.description }]"
                :aria-invalid="!!fieldErrors.description"
                :aria-describedby="fieldErrors.description ? 'link-apply-desc-error' : undefined"
                maxlength="255"
                placeholder="一句话介绍你的站点"
                @blur="validateForm('description')"
              />
              <p v-if="fieldErrors.description" id="link-apply-desc-error" class="field-error" role="alert">
                {{ fieldErrors.description }}
              </p>
            </div>

            <div class="sheet-footer">
              <button
                v-if="uncertainSubmit"
                type="button"
                class="btn-secondary"
                :disabled="submitting || application.loading"
                @click="confirmSubmitResult"
              >
                查询申请结果
              </button>
              <button
                type="button"
                class="btn-secondary"
                :disabled="submitting"
                @click="closeApplySheet()"
              >
                取消
              </button>
              <button type="submit" class="btn-primary" :disabled="submitting || uncertainSubmit">
                {{ submitting ? '提交中…' : '提交申请' }}
              </button>
            </div>
          </fieldset>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.links-page {
  --link-pill-ok: #248a3d;
  --link-pill-ok-bg: rgba(36, 138, 61, 0.1);
  --link-pill-wait: #b25000;
  --link-pill-wait-bg: rgba(255, 149, 0, 0.12);
  --link-pill-no: #d70015;
  --link-pill-no-bg: rgba(255, 59, 48, 0.1);
  width: 100%;
}

:global(html.dark) .links-page {
  --link-pill-ok: #30d158;
  --link-pill-ok-bg: rgba(48, 209, 88, 0.14);
  --link-pill-wait: #ff9f0a;
  --link-pill-wait-bg: rgba(255, 159, 10, 0.14);
  --link-pill-no: #ff453a;
  --link-pill-no-bg: rgba(255, 69, 58, 0.14);
}

.links-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.links-main {
  flex: 1;
  min-width: 0;
}

.content-card {
  background: var(--blog-card);
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--blog-border-light);
  padding: 24px 28px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 14px;
  color: var(--blog-text3);
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--blog-border-light);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-header .iconfont {
  font-size: 16px;
  color: var(--blog-text2);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* 按钮与状态胶囊 */
.btn-primary,
.btn-secondary,
.btn-danger-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 9px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  transition: background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease, opacity 0.16s ease;
}

.btn-primary {
  border: 1px solid var(--blog-text);
  background: var(--blog-text);
  color: var(--blog-card);
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.88;
}

.btn-secondary {
  border: 1px solid var(--blog-border);
  background: var(--blog-card);
  color: var(--blog-text);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--blog-hover);
}

.btn-danger-ghost {
  border: 1px solid transparent;
  background: transparent;
  color: var(--link-pill-no);
}

.btn-danger-ghost:hover:not(:disabled) {
  background: var(--link-pill-no-bg);
}

.btn-primary:disabled,
.btn-secondary:disabled,
.btn-danger-ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-sm {
  padding: 5px 12px;
  font-size: 12.5px;
  border-radius: 8px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.pill-warning {
  background: var(--link-pill-wait-bg);
  color: var(--link-pill-wait);
}

.pill-success {
  background: var(--link-pill-ok-bg);
  color: var(--link-pill-ok);
}

.pill-danger {
  background: var(--link-pill-no-bg);
  color: var(--link-pill-no);
}

.pill-neutral {
  background: var(--blog-hover);
  border: 1px solid var(--blog-border);
  color: var(--blog-text2);
}

/* Info 气泡 */
.info-tip {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1px solid var(--blog-border);
  color: var(--blog-text3);
  font-size: 11px;
  font-weight: 600;
  cursor: help;
  background: var(--blog-card);
}

.info-tip:hover::after,
.info-tip:focus-visible::after {
  content: attr(data-tip);
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  width: max-content;
  max-width: 240px;
  padding: 7px 11px;
  border-radius: 8px;
  background: var(--blog-text);
  color: var(--blog-card);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.45;
  z-index: 140;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16);
  pointer-events: none;
  white-space: normal;
}

/* 弹窗顶部气泡向下展开，防止被弹窗滚动边界裁切。 */
.sheet-head .info-tip:hover::after,
.sheet-head .info-tip:focus-visible::after {
  top: calc(100% + 8px);
  bottom: auto;
  left: 0;
  transform: none;
}

/* 反馈条与本人友链申请横幅 */
.status-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin: 0 0 16px;
  padding: 11px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.success-banner {
  background: var(--link-pill-ok-bg);
  color: var(--link-pill-ok);
}

.error-banner {
  background: var(--link-pill-no-bg);
  color: var(--blog-error-text);
}

.own-app-banner {
  margin-bottom: 18px;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--blog-border);
  background: var(--blog-hover);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.own-app-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.own-app-site {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
  flex: 1;
}

.own-avatar {
  width: 44px;
  height: 44px;
  font-size: 17px;
}

.own-app-meta {
  min-width: 0;
  flex: 1;
}

.own-app-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.own-app-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--blog-text);
  text-decoration: none;
}

.own-app-name:hover {
  text-decoration: underline;
}

.own-app-sub {
  margin: 3px 0 0;
  font-size: 12.5px;
  color: var(--blog-text2);
  overflow-wrap: anywhere;
}

.own-app-time {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--blog-text3);
}

.inline-confirm {
  padding: 12px 14px;
  border-radius: 9px;
  background: var(--blog-card);
  border: 1px solid var(--blog-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--blog-text2);
}

.confirm-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 公开友链网格 */
.placeholder-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.sk-card {
  height: 84px;
  background: var(--blog-hover);
  border-radius: 10px;
}

.link-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.link-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--blog-border-light);
  border-radius: 10px;
  text-decoration: none;
  color: inherit;
  background: var(--blog-hover);
  transition:
    transform 0.2s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.2s ease,
    border-color 0.15s ease,
    background-color 0.15s ease;
}

.link-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  border-color: var(--blog-border);
  background: var(--blog-card);
}

.preview-card {
  pointer-events: none;
  background: var(--blog-card);
}

.link-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  border: 2px solid var(--blog-border-light);
}

.link-avatar-letter {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--blog-border);
  color: var(--blog-text2);
  font-size: 20px;
  font-weight: 700;
  font-family: var(--blog-serif);
  user-select: none;
}

.link-body {
  min-width: 0;
  flex: 1;
}

.link-name {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 3px;
  color: var(--blog-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.link-desc {
  font-size: 12px;
  color: var(--blog-text3);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.5;
}

.empty {
  text-align: center;
  color: var(--blog-text3);
  padding: 40px 0;
  font-size: 14px;
  margin: 0;
}

/* Apple Sheet 弹窗 */
.sheet-backdrop {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  animation: link-fade-in 0.18s ease-out;
}

.sheet-dialog {
  width: 100%;
  max-width: 520px;
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  background: var(--blog-card);
  border: 1px solid var(--blog-border);
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.24);
  animation: link-sheet-pop 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.sheet-title-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.sheet-title {
  margin: 0;
  font-family: var(--blog-serif);
  font-size: 18px;
  color: var(--blog-text);
}

.sheet-close {
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 50%;
  border: 1px solid var(--blog-border-light);
  background: var(--blog-hover);
  color: var(--blog-text2);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.site-copy-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--blog-hover);
  border: 1px solid var(--blog-border-light);
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--blog-text2);
}

.site-copy-items {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.site-copy-chip {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--blog-card);
  border: 1px solid var(--blog-border-light);
  color: var(--blog-text);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-box {
  padding: 12px;
  border-radius: 16px;
  background: var(--blog-bg);
  border: 1px dashed var(--blog-border);
  margin-bottom: 14px;
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--blog-text3);
  margin-bottom: 8px;
}

.sheet-error {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--blog-error-text);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 12px;
}

.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12.5px;
  color: var(--blog-text2);
}

.char-count {
  font-size: 11px;
  color: var(--blog-text3);
  font-variant-numeric: tabular-nums;
}

.char-count.over-limit {
  color: var(--blog-error-text);
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--blog-border);
  border-radius: 8px;
  background: var(--blog-card);
  color: var(--blog-text);
  font-size: 13px;
  font-family: inherit;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.form-input:focus,
.form-textarea:focus {
  border-color: var(--blog-text);
  box-shadow: 0 0 0 2px rgba(48, 49, 51, 0.12);
  outline: none;
}

.form-input-error,
.form-input-error:focus {
  border-color: #f56c6c;
  box-shadow: 0 0 0 2px rgba(245, 108, 108, 0.2);
}

.form-textarea {
  resize: vertical;
}

.field-error {
  margin: 0;
  font-size: 12px;
  color: var(--blog-error-text);
  line-height: 1.4;
}

.sheet-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--blog-border-light);
}

@keyframes link-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes link-sheet-pop {
  from { opacity: 0; transform: scale(0.95) translateY(8px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

@media (max-width: 1220px) {
  .links-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(236px, 280px);
    align-items: start;
  }
}

@media (max-width: 960px) {
  .links-layout {
    flex-direction: column;
    display: flex;
  }
  .links-main {
    width: 100%;
  }
}

@media (max-width: 600px) {
  .content-card {
    padding: 16px;
  }
  .link-grid,
  .placeholder-grid,
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .link-card {
    overflow: hidden;
    max-width: 100%;
  }
  .link-body {
    overflow: hidden;
  }
  .link-desc {
    white-space: normal;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
  .sheet-dialog {
    padding: 18px 16px;
  }
}

@supports (corner-shape: squircle) {
  .content-card,
  .own-app-banner,
  .inline-confirm,
  .status-banner,
  .link-card,
  .sk-card,
  .sheet-dialog,
  .site-copy-bar,
  .site-copy-chip,
  .preview-box,
  .form-input,
  .form-textarea,
  .btn-primary,
  .btn-secondary,
  .btn-danger-ghost {
    corner-shape: squircle;
  }

  /* 友链正圆头像、圆点、提示图标与关闭按钮保持正圆。 */
  .link-avatar,
  .link-avatar-letter,
  .status-dot,
  .info-tip,
  .sheet-close {
    corner-shape: round;
  }
}
</style>
