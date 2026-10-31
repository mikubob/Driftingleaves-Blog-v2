<script setup>
import { computed, inject, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { request } from '@/api.js'
import { articleView } from '@/api/presentation.js'
import { session, can } from '@/session.js'
import { useThemeStore } from '@/stores'
import { useResource } from '@/composables/useResource.js'
import { useDiscussionForm } from '@/composables/useDiscussionForm.js'
import { useArticleAnchors } from '@/composables/useArticleAnchors.js'
import { articlePath, formatDate } from '@/format.js'
import { recordPage } from '@/tracking.js'
import SafeHtml from '@/components/SafeHtml.vue'
import DiscussionList from '@/components/DiscussionList.vue'
import TableOfContents from '@/components/TableOfContents.vue'
import EmojiPicker from '@shared/EmojiPicker.vue'
const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const { articleCover, articleTitle, articleMeta } = inject('setHero')
const resource = useResource(() => route.params.slug, async (signal) => {
  const { data } = await request('/blog/articles/' + encodeURIComponent(route.params.slug), { signal })
  if (!signal.aborted) {
    articleTitle.value = data.title
    articleCover.value = data.coverUrl || ''
    articleMeta.value = formatDate(data.publishTime) + ' · ' + data.author.nickname + ' · ' + data.wordCount + ' 字 · ' + data.readingMinutes + ' 分钟'
    recordPage(articlePath(data), data.title, data.articleId)
  }
  return articleView(data)
})
const article = computed(() => resource.data)
const loading = computed(() => resource.loading)
const isDarkMode = computed(() => themeStore.isDark)
const like = useResource(() => [article.value?.id, session.user?.userId], async (signal) => {
  if (!article.value || !session.user) {
    return null
  }
  return (await request('/blog/articles/' + article.value.id + '/like', { signal })).data
})
const liked = computed(() => like.data?.liked || false)
const likeReady = computed(() => !!like.data)
const liking = ref(false)
async function toggleLike() {
  if (!session.user) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!can('blog:article:like') || liking.value) {
    return
  }
  if (!likeReady.value) {
    await like.reload()
    return
  }
  liking.value = true
  try {
    const { data } = await request('/blog/articles/' + article.value.id + '/like', { method: liked.value ? 'DELETE' : 'PUT' })
    like.data = data
    resource.data = { ...resource.data, likeCount: data.likeCount }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    liking.value = false
  }
}

const commentTextareaRef = ref(null)
const { form: commentForm, errors, contentLength, validateField, validateForm, insertEmoji: insertCommentEmoji, resetForm } = useDiscussionForm(commentTextareaRef)
const replyTarget = ref(null)
const submitting = ref(false)
const refresh = ref(0)
function startReply(item) {
  replyTarget.value = item
  commentTextareaRef.value?.focus()
}
function cancelReply() {
  replyTarget.value = null
}
async function handleSubmitComment() {
  if (submitting.value || !validateForm()) {
    return
  }
  submitting.value = true
  try {
    const { data } = await request('/blog/articles/' + article.value.id + '/comments', {
      method: 'POST', body: { content: commentForm.value.content.trim(), parentId: replyTarget.value?.id || null },
    })
    resetForm()
    replyTarget.value = null
    refresh.value += 1
    ElMessage.success(data.status === 0 ? '评论已提交，审核通过后公开显示' : '评论已发表')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
async function copyLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

const readingProgress = ref(0)
const articleContentRef = ref(null)
const { onContentRendered, onAnchorClick } = useArticleAnchors(articleContentRef)
let progressFrame = 0
function updateReadingProgress() {
  if (progressFrame) {
    return
  }
  progressFrame = requestAnimationFrame(() => {
    progressFrame = 0
    const element = articleContentRef.value
    if (!element) {
      return
    }
    const top = window.scrollY + element.getBoundingClientRect().top
    const header = document.querySelector('.site-header')?.getBoundingClientRect().height || 58
    const start = Math.max(0, top - header - 16)
    const end = top + element.scrollHeight - window.innerHeight + 80
    readingProgress.value = Math.min(100, Math.max(0, (window.scrollY - start) / Math.max(1, end - start) * 100))
  })
}
watch(() => article.value?.contentHtml, async () => {
  await nextTick()
  onContentRendered()
  updateReadingProgress()
})
onMounted(() => {
  window.addEventListener('scroll', updateReadingProgress, { passive: true })
  window.addEventListener('resize', updateReadingProgress)
})
onUnmounted(() => {
  window.removeEventListener('scroll', updateReadingProgress)
  window.removeEventListener('resize', updateReadingProgress)
  cancelAnimationFrame(progressFrame)
})
</script>

<template>
  <div class="article-page">
    <div v-if="loading" class="loading-wrap">
      <div class="skeleton-line w60" style="height: 28px; margin-bottom: 16px" />
      <div class="skeleton-line w40" style="margin-bottom: 24px" />
      <div v-for="i in 6" :key="i" class="skeleton-line w90" />
    </div>
    <template v-else-if="article">
      <div class="reading-progress" :class="{ 'is-dark': isDarkMode }" aria-hidden="true">
        <div class="reading-progress-track">
          <div class="reading-progress-bar" :style="{ transform: `scaleX(${readingProgress / 100})` }" />
        </div>
      </div>
      <div class="article-layout">
        <!-- 左侧: 文章内容 -->
        <div class="article-main">
          <div class="article-card">
            <!-- 文章概要 -->
            <div v-if="article.summary" class="article-summary-block">
              <i class="iconfont icon-guidang" />
              <p>{{ article.summary }}</p>
            </div>
            <!-- 正文 -->
            <div ref="articleContentRef" class="article-content" @click.capture="onAnchorClick">
              <SafeHtml :html="article.contentHtml" class="fallback-content" @headings="onContentRendered" />
            </div>
            <!-- 点赞 + 转发 -->
            <div class="article-actions-inline">
              <div
                class="like-wrapper"
                :class="{ liked }"
                role="button"
                tabindex="0"
                :aria-pressed="liked"
                :aria-label="!session.user ? '登录后点赞' : liked ? '取消点赞' : '点赞'"
                :aria-disabled="liking"
                @keydown.enter.prevent="toggleLike"
                @keydown.space.prevent="toggleLike"
                @click="toggleLike"
                :title="likeReady ? '点赞' : '读取点赞状态，失败可点击重试'"
              >
                <div class="svg-container">
                  <svg viewBox="0 0 24 24" class="svg-outline" xmlns="http://www.w3.org/2000/svg">
                    <path
                      d="M17.5,1.917a6.4,6.4,0,0,0-5.5,3.3,6.4,6.4,0,0,0-5.5-3.3A6.8,6.8,0,0,0,0,8.967c0,4.547,4.786,9.513,8.8,12.88a4.974,4.974,0,0,0,6.4,0C19.214,18.48,24,13.514,24,8.967A6.8,6.8,0,0,0,17.5,1.917Zm-3.585,18.4a2.973,2.973,0,0,1-3.83,0C4.947,16.006,2,11.87,2,8.967a4.8,4.8,0,0,1,4.5-5.05A4.8,4.8,0,0,1,11,8.967a1,1,0,0,0,2,0,4.8,4.8,0,0,1,4.5-5.05A4.8,4.8,0,0,1,22,8.967C22,11.87,19.053,16.006,13.915,20.313Z"
                    >
                    </path>
                  </svg>
                  <svg viewBox="0 0 24 24" class="svg-filled" xmlns="http://www.w3.org/2000/svg">
                    <path
                      d="M17.5,1.917a6.4,6.4,0,0,0-5.5,3.3,6.4,6.4,0,0,0-5.5-3.3A6.8,6.8,0,0,0,0,8.967c0,4.547,4.786,9.513,8.8,12.88a4.974,4.974,0,0,0,6.4,0C19.214,18.48,24,13.514,24,8.967A6.8,6.8,0,0,0,17.5,1.917Z"
                    >
                    </path>
                  </svg>
                  <svg class="svg-celebrate" width="100" height="100" xmlns="http://www.w3.org/2000/svg">
                    <polygon points="10,10 20,20"></polygon>
                    <polygon points="10,50 20,50"></polygon>
                    <polygon points="20,80 30,70"></polygon>
                    <polygon points="90,10 80,20"></polygon>
                    <polygon points="90,50 80,50"></polygon>
                    <polygon points="80,80 70,70"></polygon>
                  </svg>
                </div>
                <span>{{ article.likeCount ?? 0 }}</span>
              </div>
              <button class="action-icon" @click="copyLink" title="复制链接">
                <i class="iconfont icon-zhuanfa" />
              </button>
            </div>
            <!-- 上下篇 -->
            <div v-if="article.prevArticle || article.nextArticle" class="article-nav">
              <router-link
                v-if="article.prevArticle"
                :to="`/articles/${article.prevArticle.slug}`"
                class="nav-prev"
              >
                <i class="iconfont icon-arrow-left" />
                <span>{{ article.prevArticle.title }}</span>
              </router-link>
              <div v-else />
              <router-link
                v-if="article.nextArticle"
                :to="`/articles/${article.nextArticle.slug}`"
                class="nav-next"
              >
                <span>{{ article.nextArticle.title }}</span>
                <i class="iconfont icon-arrow-right" />
              </router-link>
            </div>
          </div>
          <!-- 相关文章 -->
          <div v-if="article.relatedArticles?.length" class="related-card">
            <h3 class="section-title">相关文章</h3>
            <ul class="related-list">
              <li v-for="r in article.relatedArticles" :key="r.id">
                <router-link :to="`/articles/${r.slug}`">{{
                  r.title
                }}</router-link>
              </li>
            </ul>
          </div>
          <!-- 评论区 -->
          <div class="comment-card">
            <h3 class="section-title">
              <i class="iconfont icon-pinglun" />
              评论 (
              {{ article.commentCount }}
              )
            </h3>
            <p v-if="!session.user">
              <router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">登录后发表评论 →</router-link>
            </p>
            <p v-else-if="article.allowComment !== 1">这篇文章已关闭新评论。</p>
            <p v-else-if="!can('blog:comment:create')">当前账号没有评论权限。</p>
            <fieldset v-else class="comment-form" :disabled="submitting">
              <div v-if="replyTarget" class="reply-hint">
                回复
                <strong>{{ replyTarget.nickname }}</strong>
                <a class="cancel-reply" @click="cancelReply">&times;</a>
              </div>
              <div class="textarea-wrap">
                <textarea
                  ref="commentTextareaRef"
                  v-model="commentForm.content"
                  aria-label="评论正文"
                  placeholder="写下你的想法..."
                  :class="[
                    'form-textarea',
                    { 'form-textarea-error': errors.content }
                  ]"
                  rows="4"
                  @blur="validateField('content')"
                />
                <p v-if="errors.content" class="error-text">{{ errors.content }}</p>
                <div class="char-counter-line" :class="{ 'over-limit': contentLength > 2000 }">
                  {{ contentLength }}
                  /2000
                </div>
              </div>
              <div class="form-options">
                <div class="form-actions">
                  <EmojiPicker @select="insertCommentEmoji" />
                  <button class="form-submit" :disabled="submitting" @click="handleSubmitComment">
                    {{ submitting ? '提交中...' : '发表评论' }}
                  </button>
                </div>
              </div>
            </fieldset>
            <!-- 评论树 -->
            <p v-if="like.error" role="alert">{{ like.error }}</p>
            <DiscussionList
              :key="refresh"
              kind="comments"
              :article-id="article.id"
              :allow-reply="article.allowComment === 1 && !submitting"
              @reply="startReply"
            />
          </div>
        </div>
        <!-- 右侧: 目录 -->
        <aside class="article-sidebar">
          <TableOfContents :content-html="article.contentHtml" :progress="readingProgress" />
        </aside>
      </div>
    </template>
    <div v-else class="not-found" role="alert">
      {{ resource.error || '文章不存在' }}
      <button @click="resource.reload">重试</button>
    </div>
  </div>
</template>

<style scoped>
.article-page {
  width: 100%;
}

.reading-progress {
  position: fixed;
  top: 58px;
  left: 0;
  right: 0;
  z-index: 99;
  height: 4px;
  pointer-events: none;
}

.reading-progress-track {
  width: 100%;
  height: 100%;
  background: rgba(17, 17, 17, 0.08);
  backdrop-filter: blur(8px);
  overflow: hidden;
}

.reading-progress-bar {
  width: 100%;
  height: 100%;
  transform-origin: left center;
  background: #111111;
  box-shadow: 0 0 14px rgba(17, 17, 17, 0.18);
  transition: transform 0.12s linear;
}

.reading-progress.is-dark .reading-progress-track {
  background: rgba(255, 255, 255, 0.1) !important;
}

.reading-progress.is-dark .reading-progress-bar {
  background: #ffffff !important;
  box-shadow: 0 0 16px rgba(255, 255, 255, 0.32) !important;
}

.loading-wrap {
  padding: 20px 0;
}
.skeleton-line {
  height: 14px;
  background: #ebeef5;
  border-radius: 4px;
  margin-bottom: 10px;
}
.w60 {
  width: 60%;
}
.w40 {
  width: 40%;
}
.w90 {
  width: 90%;
}

/* 双栏布局 */
.article-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.article-main {
  flex: 1;
  min-width: 0;
}
.article-sidebar {
  width: 300px;
  max-width: 300px;
  flex-shrink: 0;
  position: sticky;
  top: 74px;
}

/* 卡片 */
.article-card,
.related-card,
.comment-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
  padding: 28px;
  margin-bottom: 16px;
}

/* 文章概要 */
.article-summary-block {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 16px 20px;
  background: var(--blog-hover, #f5f7fa);
  border-radius: 6px;
  border-left: 3px solid var(--blog-text, #303133);
  margin-bottom: 24px;
  font-size: 14px;
  color: var(--blog-text2, #606266);
  line-height: 1.7;
}
.article-summary-block .iconfont {
  font-size: 16px;
  color: var(--blog-text3, #909399);
  margin-top: 2px;
  flex-shrink: 0;
}
.article-summary-block p {
  margin: 0;
}

.article-content {
  word-break: break-word;
}

.article-content :deep(h1),
.article-content :deep(h2),
.article-content :deep(h3),
.article-content :deep(h4),
.article-content :deep(h5),
.article-content :deep(h6) {
  scroll-margin-top: 90px;
}

.article-content :deep(.md-editor) {
  background: transparent !important;
  border: none;
}
.article-content :deep(.md-editor-preview-wrapper) {
  padding: 0;
}
.article-content :deep(.md-editor-preview) {
  font-size: 15.5px;
  line-height: 1.85;
  color: var(--blog-text, #333);
}

.article-content :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

/* 图片圆角 */
.article-content :deep(img) {
  max-width: 100%;
  border-radius: 6px;
  margin: 8px 0;
}
/* 回退 v-html 用的极简样式 */
.fallback-content {
  font-size: 15.5px;
  line-height: 1.85;
  color: var(--blog-text, #333);
}
.fallback-content :deep(h1),
.fallback-content :deep(h2),
.fallback-content :deep(h3),
.fallback-content :deep(h4) {
  color: var(--blog-text, #303133);
  margin: 28px 0 12px;
  font-weight: 700;
}
.fallback-content :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 14px 0;
  font-size: 13.5px;
  line-height: 1.6;
}
.fallback-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.fallback-content :deep(code) {
  background: #f5f7fa;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 14px;
}
.fallback-content :deep(img) {
  max-width: 100%;
  border-radius: 6px;
  margin: 8px 0;
}
.fallback-content :deep(blockquote) {
  margin: 14px 0;
  padding: 10px 16px;
  border-left: 3px solid #303133;
  background: #f5f7fa;
  color: #606266;
}
.fallback-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 14px 0;
  font-size: 14px;
}
.fallback-content :deep(th),
.fallback-content :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 8px 12px;
  text-align: left;
}
.fallback-content :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
}

/* 点赞+转发 */
.article-actions-inline {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}
.action-icon {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: #909399;
  padding: 4px 8px;
  border-radius: 4px;
  font-family: inherit;
  transition:
    color 0.15s,
    background 0.15s;
}
.action-icon:hover {
  color: #303133;
  background: #f5f7fa;
}
.action-icon .iconfont {
  font-size: 16px;
}

/* 点赞动画容器 */
.like-wrapper {
  --heart-color: rgb(255, 91, 137);
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #909399;
  padding: 4px 8px;
  border-radius: 4px;
  font-family: inherit;
  transition:
    color 0.15s,
    background 0.15s;
  user-select: none;
}
.like-wrapper:hover {
  color: #303133;
  background: #f5f7fa;
}
.like-wrapper.liked {
  color: var(--heart-color);
}
.like-wrapper.liked:hover {
  color: var(--heart-color);
  background: rgba(255, 91, 137, 0.08);
}
.like-wrapper .svg-container {
  position: relative;
  width: 20px;
  height: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.like-wrapper .svg-outline,
.like-wrapper .svg-filled {
  width: 20px;
  height: 20px;
  fill: var(--heart-color);
  position: absolute;
}
.like-wrapper .svg-filled {
  animation: keyframes-svg-filled 1s;
  display: none;
}
.like-wrapper .svg-celebrate {
  position: absolute;
  width: 60px;
  height: 60px;
  animation: keyframes-svg-celebrate 0.5s;
  animation-fill-mode: forwards;
  display: none;
  stroke: var(--heart-color);
  fill: var(--heart-color);
  stroke-width: 2px;
  pointer-events: none;
}
.like-wrapper.liked .svg-container .svg-filled {
  display: block;
}
.like-wrapper.liked .svg-container .svg-celebrate {
  display: block;
}

@keyframes keyframes-svg-filled {
  0% {
    transform: scale(0);
  }
  25% {
    transform: scale(1.2);
  }
  50% {
    transform: scale(1);
    filter: brightness(1.5);
  }
}

@keyframes keyframes-svg-celebrate {
  0% {
    transform: scale(0);
  }
  50% {
    opacity: 1;
    filter: brightness(1.5);
  }
  100% {
    transform: scale(1.4);
    opacity: 0;
    display: none;
  }
}

/* 上下篇 */
.article-nav {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
  font-size: 14px;
}
.nav-prev,
.nav-next {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  text-decoration: none;
  max-width: 48%;
  transition: color 0.15s;
}
.nav-prev:hover,
.nav-next:hover {
  color: #303133;
}
.nav-next {
  margin-left: auto;
  text-align: right;
}

/* 相关文章 */
.section-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}
.section-title .iconfont {
  font-size: 16px;
}
.related-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.related-list li {
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 14px;
}
.related-list a {
  color: #606266;
  text-decoration: none;
}
.related-list a:hover {
  color: #303133;
}

/* 评论表单 */
.comment-form {
  margin-bottom: 24px;
}
.reply-hint {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.cancel-reply {
  cursor: pointer;
  margin-left: 6px;
  color: #909399;
  font-size: 16px;
}
.form-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: flex-start;
}
.form-field {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.input-with-icon {
  position: relative;
  display: flex;
  align-items: center;
}
.input-icon {
  position: absolute;
  left: 10px;
  font-size: 14px;
  color: #c0c4cc;
  pointer-events: none;
  z-index: 1;
}
.input-with-icon .form-input {
  padding-left: 32px;
  width: 100%;
}
.captcha-wrap {
  min-width: 0;
}
.captcha-refresh {
  position: absolute;
  right: 8px;
  font-size: 16px;
  color: #909399;
  cursor: pointer;
  user-select: none;
  transition: color 0.15s;
}
.captcha-refresh:hover {
  color: #303133;
}
.captcha-tip {
  position: absolute;
  top: -28px;
  left: 32px;
  font-size: 12px;
  color: #303133;
  background: #fff;
  padding: 2px 10px;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  border: 1px solid #ebeef5;
  white-space: nowrap;
  z-index: 10;
  pointer-events: none;
  font-weight: 600;
}
.form-input {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  background: #fff;
  box-sizing: border-box;
}
.form-input:focus {
  border-color: #303133;
}
.form-input-error,
.form-input-error:focus {
  border-color: #f56c6c;
}
.textarea-wrap {
  margin-bottom: 8px;
}
.form-textarea {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 13px;
  resize: vertical;
  outline: none;
  font-family: inherit;
  box-sizing: border-box;
  background: #fff;
}
.form-textarea:focus {
  border-color: #303133;
}
.form-textarea-error,
.form-textarea-error:focus {
  border-color: #f56c6c;
}
.error-text {
  color: #f56c6c;
  font-size: 12px;
  margin: 4px 0 0;
  line-height: 1.4;
}
.char-counter-line {
  text-align: right;
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}
.char-counter-line.over-limit {
  color: #f56c6c;
}
.form-options {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.option-check {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
}
.option-check input[type='checkbox'] {
  accent-color: #303133;
}
.form-submit {
  background: #303133;
  color: #fff;
  border: none;
  padding: 8px 24px;
  font-size: 13px;
  cursor: pointer;
  border-radius: 6px;
  font-family: inherit;
  margin-left: auto;
  transition: background 0.15s;
}
.form-submit:hover {
  background: #000;
}
.form-submit:disabled {
  opacity: 0.5;
}

/* 评论树 */
.comment-tree {
  margin-top: 4px;
}
.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
}
.comment-item.child {
  padding: 12px 0;
  border-bottom: 1px dashed #ebeef5;
}
.comment-children {
  margin-top: 8px;
  padding-left: 4px;
  border-left: 2px solid #ebeef5;
}

/* 头像 */
.c-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
}
.c-avatar-sm {
  width: 32px;
  height: 32px;
}
.c-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #ebeef5;
}
.c-avatar-letter {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #e4e7ed;
  color: #606266;
  font-size: 16px;
  font-weight: 700;
  font-family: var(--blog-serif);
  user-select: none;
}
.c-avatar-sm .c-avatar-letter {
  font-size: 13px;
}

.c-main {
  flex: 1;
  min-width: 0;
}
.c-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.c-nick {
  font-size: 14px;
  font-weight: 700;
  color: #303133;
}
.c-badge {
  font-size: 10px;
  background: #303133;
  color: #fff;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 600;
}
.c-reply-to {
  font-size: 12px;
  color: #909399;
}
.c-pending {
  font-size: 10px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
}
.reply-icon {
  display: inline-block;
  transform: scaleX(-1);
  font-size: 12px;
}

/* 元信息（位置、系统、浏览器）— 与昵称同行 */
.c-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #b0b4bb;
}
.c-meta-icon {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}

.c-body {
  font-size: 14px;
  color: #444;
  line-height: 1.65;
}
.c-body :deep(p) {
  margin: 4px 0;
}
.c-body :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.5;
}
.c-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
  font-size: inherit;
}
.c-body :deep(code) {
  background: #f5f7fa;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 13px;
  color: #476582;
}
.c-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid #dcdfe6;
  background: #f5f7fa;
  color: #606266;
}
.c-body :deep(ul),
.c-body :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}
.c-body :deep(a) {
  color: #409eff;
  text-decoration: none;
}
.c-body :deep(a:hover) {
  text-decoration: underline;
}
.c-body :deep(h1),
.c-body :deep(h2),
.c-body :deep(h3),
.c-body :deep(h4) {
  margin: 8px 0 4px;
  font-weight: 600;
  color: #303133;
}
.c-body :deep(h1) {
  font-size: 18px;
}
.c-body :deep(h2) {
  font-size: 16px;
}
.c-body :deep(h3) {
  font-size: 15px;
}
.c-body :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}

.c-date {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
  flex-shrink: 0;
}
/* 底部：hover操作在右下角 */
.c-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
.c-actions {
  display: flex;
  gap: 10px;
  opacity: 0;
  transition: opacity 0.15s;
}
.comment-item:hover .c-actions {
  opacity: 1;
}
.c-actions a {
  font-size: 12px;
  color: #909399;
  cursor: pointer;
  text-decoration: none;
}
.c-actions a:hover {
  color: #303133;
}
.c-actions a.danger:hover {
  color: #c00;
}
.c-edit-wrap {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 4px 0;
}
.inline-btn {
  background: none;
  border: 1px solid #e4e7ed;
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  font-family: inherit;
}
.inline-btn:hover {
  border-color: #303133;
}
.empty-comment {
  padding: 32px 0;
  text-align: center;
  color: #909399;
  font-size: 14px;
}
.not-found {
  padding: 80px 0;
  text-align: center;
  color: #909399;
  font-size: 16px;
}

@media (max-width: 960px) {
  .article-layout {
    flex-direction: column;
  }
  .article-main {
    width: 100%;
    max-width: 100%;
    overflow-x: hidden;
  }
  .article-sidebar {
    width: 100%;
    position: static;
  }
}
@media (max-width: 600px) {
  .article-card,
  .related-card,
  .comment-card {
    padding: 16px;
  }
  .article-nav {
    flex-direction: column;
  }
  .form-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

<style>
/* 代码块容器 */
.article-content .md-editor-code {
  max-width: 100% !important;
  width: 100% !important;
  box-sizing: border-box !important;
}
/* pre 限制宽度 */
.article-content .md-editor-code > pre {
  max-width: 100% !important;
  box-sizing: border-box !important;
}

.article-content .md-editor-code > pre > code {
  max-width: 100% !important;
  box-sizing: border-box !important;
  overflow-x: auto !important;
  display: block !important;
}
/* md-editor-code-block */
.article-content .md-editor-code .md-editor-code-block {
  width: auto !important;
  min-width: 0 !important;
}
/* summary 不浮在导航栏上方 */
.article-content .md-editor-code > .md-editor-code-head {
  z-index: 1 !important;
  position: relative !important;
}
</style>
