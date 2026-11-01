<script setup>
import { computed, inject, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElDialog, ElMessage } from 'element-plus'
import { useBlogStore } from '@/stores'
import { request } from '@/api.js'
import { can, session } from '@/session.js'
import { safeLink } from '@shared/links.mjs'
const router = useRouter()
const route = useRoute()
const blogStore = useBlogStore()
const online = inject('online')
const onlineStatus = computed(() => online.value.status === 'connecting'
  ? '在线人数连接中…' : '在线人数连接中断')
const report = computed(() => blogStore.report)
const info = computed(() => blogStore.personalInfo)
const showCatModal = ref(false)
const showTagModal = ref(false)
const showRssModal = ref(false)
const rssForm = ref({ email: '' })
const rssSubmitting = ref(false)
const rssEditMode = ref(false)
const rssError = ref('')
const subscription = ref(null)
const isSubscribed = computed(() => subscription.value?.status === 1)
const runDays = computed(() => {
  const start = blogStore.site.startDate
  return start ? Math.max(0, Math.floor((Date.now() - new Date(start).getTime()) / 86400000)) : null
})
watch(() => session.user?.userId, () => {
  subscription.value = null
  showRssModal.value = false
})
async function openRssModal() {
  if (!session.user) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  rssError.value = ''
  rssEditMode.value = false
  showRssModal.value = true
  rssSubmitting.value = true
  try {
    subscription.value = (await request('/blog/me/rss-subscription')).data
    rssForm.value.email = subscription.value?.email || ''
  } catch (error) {
    rssError.value = error.message
  } finally {
    rssSubmitting.value = false
  }
}
async function handleRssSubscribe() {
  if (!can('blog:rss:subscribe') || rssSubmitting.value) {
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(rssForm.value.email.trim())) {
    rssError.value = '请输入有效邮箱'
    return
  }
  rssSubmitting.value = true
  rssError.value = ''
  try {
    subscription.value = (await request('/blog/me/rss-subscription', {
      method: 'PUT', body: { email: rssForm.value.email.trim().toLowerCase() },
    })).data
    ElMessage.success(subscription.value.status === 1 ? '当前邮箱已订阅' : '确认邮件已发送，请查收邮件完成邮箱验证')
    rssEditMode.value = false
  } catch (error) {
    rssError.value = error.message
  } finally {
    rssSubmitting.value = false
  }
}
async function handleRssUnsubscribe() {
  rssSubmitting.value = true
  rssError.value = ''
  try {
    await request('/blog/me/rss-subscription', { method: 'DELETE' })
    subscription.value = null
    showRssModal.value = false
    ElMessage.success('已取消订阅')
  } catch (error) {
    rssError.value = error.message
  } finally {
    rssSubmitting.value = false
  }
}
const goArchive = () => router.push('/archive')
function goCategory(slug) {
  showCatModal.value = false
  router.push('/category/' + slug)
}
function goTag(slug) {
  showTagModal.value = false
  router.push('/tag/' + slug)
}
</script>

<template>
  <aside class="sidebar">
    <!-- 个人信息卡片 -->
    <div class="side-card info-card">
      <div class="info-avatar-wrap">
        <img v-if="info.avatar" :src="info.avatar" class="info-avatar" loading="lazy" />
      </div>
      <h3 class="info-name">{{ info.nickname || blogStore.site.title || 'DriftingLeaves' }}</h3>
      <p v-if="info.tag" class="info-tag">{{ info.tag }}</p>
      <p v-if="info.location" class="info-location">
        <i class="iconfont icon-position" />
        {{ info.location }}
      </p>
      <!-- 数据统计栏 -->
      <div class="info-stats">
        <div class="info-stat" role="link" tabindex="0" @keydown.enter="goArchive" @click="goArchive">
          <span class="stat-num">{{ report.articleTotalCount ?? 0 }}</span>
          <span class="stat-label">文章</span>
        </div>
        <div
          class="info-stat"
          role="button"
          tabindex="0"
          @keydown.enter="showCatModal = true"
          @click="showCatModal = true"
        >
          <span class="stat-num">{{ report.categoryTotalCount ?? 0 }}</span>
          <span class="stat-label">分类</span>
        </div>
        <div
          class="info-stat"
          role="button"
          tabindex="0"
          @keydown.enter="showTagModal = true"
          @click="showTagModal = true"
        >
          <span class="stat-num">{{ report.tagTotalCount ?? 0 }}</span>
          <span class="stat-label">标签</span>
        </div>
      </div>
      <!-- 社交链接 -->
      <div class="info-social">
        <a
          v-if="info.github"
          :href="safeLink(info.github)"
          target="_blank"
          rel="noopener"
          class="social-link"
          title="GitHub"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path
              d="M12 .3a12 12 0 00-3.8 23.38c.6.12.83-.26.83-.57L9 21.07c-3.34.72-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.08-.74.08-.73.08-.73 1.2.08 1.84 1.24 1.84 1.24 1.07 1.83 2.81 1.3 3.5 1 .1-.78.42-1.3.76-1.6-2.67-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.13-.3-.54-1.52.12-3.18 0 0 1-.33 3.3 1.23a11.5 11.5 0 016.02 0c2.28-1.56 3.29-1.23 3.29-1.23.66 1.66.25 2.88.12 3.18a4.65 4.65 0 011.23 3.22c0 4.61-2.81 5.63-5.48 5.93.43.37.81 1.1.81 2.22l-.01 3.29c0 .31.22.69.83.57A12 12 0 0012 .3"
            />
          </svg>
        </a>
        <a v-if="info.email" :href="`mailto:${info.email}`" class="social-link" title="Email">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <rect width="20" height="16" x="2" y="4" rx="2" />
            <path d="m22 7-8.97 5.7a1.94 1.94 0 01-2.06 0L2 7" />
          </svg>
        </a>
        <button
          type="button"
          class="social-link"
          :class="{ subscribed: isSubscribed }"
          :title="isSubscribed ? '已订阅 RSS' : 'RSS 订阅'"
          @click="openRssModal"
        >
          <i class="iconfont icon-rssdingyue" style="font-size: 18px" />
        </button>
      </div>
    </div>
    <!-- 站点统计卡片 -->
    <div class="side-card stats-card">
      <h4 class="card-title">
        <i class="iconfont icon-eye" />
        站点统计
      </h4>
      <div class="stats-grid">
        <div class="sg-item">
          <span class="sg-label">今日浏览</span>
          <span class="sg-val">{{ report.viewTodayCount ?? 0 }}</span>
        </div>
        <div class="sg-item">
          <span class="sg-label">总浏览量</span>
          <span class="sg-val">{{ report.viewTotalCount ?? 0 }}</span>
        </div>
        <div class="sg-item">
          <span class="sg-label">总访客量</span>
          <span class="sg-val">{{ report.visitorTotalCount ?? 0 }}</span>
        </div>
        <div class="sg-item">
          <span class="sg-label">稳定运行</span>
          <span class="sg-val">{{ runDays ?? '--' }} 天</span>
        </div>
      </div>
      <div class="stats-grid online-stats" role="status" aria-live="polite" aria-label="三站实时在线人数">
        <span class="online-scope">三站合计</span>
        <template v-if="online.status === 'live'">
          <div class="sg-item">
            <span class="sg-label">当前在线</span>
            <span class="sg-val">{{ online.snapshot.onlineCount }} 人</span>
          </div>
          <div class="sg-item">
            <span class="sg-label">登录用户</span>
            <span class="sg-val">{{ online.snapshot.userCount }} 人</span>
          </div>
          <div class="sg-item">
            <span class="sg-label">未登录访客</span>
            <span class="sg-val">{{ online.snapshot.guestCount }} 人</span>
          </div>
        </template>
        <p v-else class="sg-item">{{ onlineStatus }}</p>
      </div>
    </div>
    <slot />
    <!-- ===== 分类弹窗 ===== -->
    <el-dialog v-model="showCatModal" title="所有分类" width="420px" :append-to-body="true">
      <div class="modal-list">
        <div
          v-for="cat in blogStore.categories"
          :key="cat.id"
          class="modal-item"
          @click="goCategory(cat.slug)"
        >
          <i class="iconfont icon-folder" />
          <span class="modal-name">{{ cat.name }}</span>
        </div>
        <div v-if="!blogStore.categories.length" class="modal-empty">暂无分类</div>
      </div>
    </el-dialog>
    <!-- ===== 标签弹窗 ===== -->
    <el-dialog v-model="showTagModal" title="所有标签" width="420px" :append-to-body="true">
      <div class="modal-tag-cloud">
        <span v-for="tag in blogStore.tags" :key="tag.id" class="modal-tag" @click="goTag(tag.slug)">
          <i class="iconfont icon-biaoqian" />
          {{ tag.name }}
        </span>
        <div v-if="!blogStore.tags.length" class="modal-empty">暂无标签</div>
      </div>
    </el-dialog>
    <!-- ===== RSS 订阅弹窗 ===== -->
    <el-dialog
      v-model="showRssModal"
      :title="isSubscribed && !rssEditMode ? '已订阅 RSS' : 'RSS 订阅'"
      width="380px"
      :append-to-body="true"
    >
      <p v-if="rssError" role="alert">{{ rssError }}</p>
      <p v-if="subscription?.status === 0" role="status">等待邮箱验证，请查收邮件；可重新提交以重发确认邮件。</p>
      <p v-if="!can('blog:rss:subscribe')">当前账号没有邮件订阅权限。</p>
      <!-- 已订阅，查看状态 -->
      <div v-if="isSubscribed && !rssEditMode" class="rss-status">
        <div class="rss-status-row">
          <span class="rss-label">昵称</span>
          <span class="rss-value">{{ session.user?.nickname }}</span>
        </div>
        <div class="rss-status-row">
          <span class="rss-label">邮箱</span>
          <span class="rss-value">{{ rssForm.email }}</span>
        </div>
        <div class="rss-actions">
          <button class="rss-edit-btn" @click="rssEditMode = true">编辑</button>
          <button class="rss-unsub-btn" :disabled="rssSubmitting" @click="handleRssUnsubscribe">
            {{ rssSubmitting ? '处理中...' : '取消订阅' }}
          </button>
        </div>
      </div>
      <!-- 未订阅 / 编辑模式 -->
      <div v-else class="rss-form">
        <input
          :disabled="rssSubmitting"
          maxlength="254"
          v-model="rssForm.email"
          aria-label="订阅邮箱"
          type="email"
          placeholder="邮箱 *"
          class="rss-form-input"
        />
        <div class="rss-form-btns">
          <button v-if="rssEditMode" class="rss-cancel-btn" @click="rssEditMode = false">取消</button>
          <button
            class="rss-submit-btn"
            :disabled="rssSubmitting || !can('blog:rss:subscribe')"
            @click="handleRssSubscribe"
          >
            {{ rssSubmitting ? '提交中...' : rssEditMode ? '保存' : '订阅' }}
          </button>
        </div>
      </div>
    </el-dialog>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 100%;
  max-width: 280px;
  flex-shrink: 0;
  position: sticky;
  top: 74px;
}
.side-card {
  background: var(--blog-card);
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--blog-border-light);
  padding: 20px;
  margin-bottom: 16px;
}

/* 个人信息卡片 */
.info-card {
  text-align: center;
}
.info-avatar-wrap {
  margin-bottom: 12px;
}
.info-avatar {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #ebeef5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.info-name {
  font-size: 17px;
  font-weight: 700;
  margin: 0 0 4px;
  color: #303133;
}
.info-tag {
  font-size: 12px;
  color: #909399;
  margin: 0 0 6px;
}
.info-location {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 12px;
  color: #b0b0b0;
  margin: 0 0 14px;
}
.info-location .iconfont {
  font-size: 13px;
}

.info-stats {
  display: flex;
  justify-content: center;
  gap: 0;
  padding: 12px 0;
  margin: 0 -20px;
}
.info-stat {
  flex: 1;
  text-align: center;
  cursor: pointer;
  transition: background 0.15s;
  padding: 4px 0;
  border-radius: 4px;
}
.info-stat:hover {
  background: #f5f7fa;
}
.info-stat + .info-stat {
  border-left: 1px solid #ebeef5;
}
.stat-num {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  font-family: var(--blog-serif);
}
.stat-label {
  font-size: 11px;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-social {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 14px;
}
.social-link {
  border: 0;
  background: transparent;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  color: #606266;
  cursor: pointer;
  background: none;
  transition:
    color 0.15s,
    background 0.15s;
}
.social-link:hover {
  color: #303133;
  background: #f5f7fa;
}
.social-link.subscribed {
  color: #303133;
}

/* 在线连接由主布局管理，侧栏切换只读取同一份快照。 */
.online-stats {
  border-top: 1px solid var(--blog-border-light, #ebeef5);
  margin-top: 12px;
  padding-top: 12px;
}
.online-scope {
  color: var(--blog-text3, #909399);
  font-size: 12px;
}
.online-stats p {
  margin: 0;
}

/* 站点统计 */
.card-title {
  font-size: 14px;
  font-weight: 700;
  margin: 0 0 12px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-title .iconfont {
  font-size: 15px;
}
.stats-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sg-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
}
.sg-icon {
  margin-right: 4px;
  font-size: 10px;
}
.sg-label {
  flex: 1;
}
.sg-val {
  font-weight: 600;
  color: #303133;
  font-family: var(--blog-serif);
}

.modal-list {
  max-height: 400px;
  overflow-y: auto;
}
.modal-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.12s;
  font-size: 14px;
  color: #303133;
}
.modal-item:hover {
  background: #f5f7fa;
}
.modal-item .iconfont {
  font-size: 15px;
  color: #909399;
}
.modal-name {
  flex: 1;
}
.modal-count {
  font-size: 12px;
  color: #909399;
}
.modal-empty {
  text-align: center;
  color: #909399;
  padding: 20px;
  font-size: 14px;
}

.modal-tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
  padding: 4px 0;
}
.modal-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 16px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.15s;
}
.modal-tag:hover {
  color: #303133;
  border-color: #303133;
  background: #f5f7fa;
}
.modal-tag .iconfont {
  font-size: 12px;
}
.tag-count {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 1px 6px;
  font-size: 11px;
  color: #909399;
  margin-left: 2px;
}

@media (max-width: 1220px) {
  .sidebar {
    max-width: none;
  }
}

@media (max-width: 960px) {
  .sidebar {
    width: 100%;
    max-width: none;
    position: static;
  }
}

/* RSS 订阅弹窗 */
.rss-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rss-form-input {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  box-sizing: border-box;
}
.rss-form-input:focus {
  border-color: #303133;
}
.rss-form-btns {
  display: flex;
  gap: 8px;
}
.rss-submit-btn {
  flex: 1;
  padding: 8px 0;
  font-size: 13px;
  border: none;
  background: #303133;
  color: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s;
}
.rss-submit-btn:hover {
  background: #000;
}
.rss-submit-btn:disabled {
  opacity: 0.5;
}
.rss-cancel-btn {
  padding: 8px 20px;
  font-size: 13px;
  border: 1px solid #e4e7ed;
  background: #fff;
  color: #606266;
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.15s;
}
.rss-cancel-btn:hover {
  border-color: #303133;
}

/* RSS 已订阅状态 */
.rss-status {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rss-status-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.rss-label {
  color: #909399;
  min-width: 36px;
}
.rss-value {
  color: #303133;
  font-weight: 500;
}
.rss-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
.rss-edit-btn {
  flex: 1;
  padding: 8px 0;
  font-size: 13px;
  border: 1px solid #e4e7ed;
  background: #fff;
  color: #303133;
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.15s;
}
.rss-edit-btn:hover {
  border-color: #303133;
}
.rss-unsub-btn {
  flex: 1;
  padding: 8px 0;
  font-size: 13px;
  border: none;
  background: #f5f7fa;
  color: #909399;
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}
.rss-unsub-btn:hover {
  background: #fee;
  color: #c00;
}
.rss-unsub-btn:disabled {
  opacity: 0.5;
}
</style>
