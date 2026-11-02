<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { formatTime } from '../format.js'
import SafeHtml from './SafeHtml.vue'
import StatusTag from './StatusTag.vue'

// 评论和留言共用详情布局，手机上将信息表改为单列。
const props = defineProps({ kind: { type: String, required: true }, detail: { type: Object, required: true } })
const isComment = computed(() => props.kind === 'comment')
const media = window.matchMedia('(max-width: 640px)')
const columns = ref(media.matches ? 1 : 2)
function updateColumns(event) {
  columns.value = event.matches ? 1 : 2
}
onMounted(() => media.addEventListener('change', updateColumns))
onBeforeUnmount(() => media.removeEventListener('change', updateColumns))
</script>

<template>
  <el-descriptions class="discussion-detail" :column="columns" border>
    <el-descriptions-item label="昵称">{{ detail.author?.nickname || '已删除用户' }}</el-descriptions-item>
    <el-descriptions-item label="用户 ID">{{ detail.author?.userId || detail.userId || '—' }}</el-descriptions-item>
    <el-descriptions-item v-if="isComment" label="所属文章" :span="columns">
      {{ detail.articleTitle || '—' }}
      <span class="muted-text">（ID：{{ detail.articleId }}）</span>
    </el-descriptions-item>
    <el-descriptions-item :label="isComment ? '评论 ID' : '留言 ID'">
      {{ isComment ? detail.commentId : detail.messageId }}
    </el-descriptions-item>
    <el-descriptions-item label="回复目标 ID">{{ detail.parentId || '—' }}</el-descriptions-item>
    <el-descriptions-item label="状态"><StatusTag :value="detail.status" kind="review" /></el-descriptions-item>
    <el-descriptions-item label="IP 属地">{{ detail.location || '未知' }}</el-descriptions-item>
    <el-descriptions-item label="操作系统">{{ detail.userAgentOs || '未知' }}</el-descriptions-item>
    <el-descriptions-item label="浏览器">{{ detail.userAgentBrowser || '未知' }}</el-descriptions-item>
    <template v-if="!isComment">
      <el-descriptions-item label="可见性">{{ detail.isSecret ? '悄悄话' : '公开留言' }}</el-descriptions-item>
      <el-descriptions-item label="内容格式">{{ detail.contentFormat === 1 ? 'Markdown' : '纯文本' }}</el-descriptions-item>
    </template>
    <el-descriptions-item label="发表时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
    <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
    <template v-if="!isComment">
      <el-descriptions-item label="编辑时间">{{ formatTime(detail.editedAt) }}</el-descriptions-item>
      <el-descriptions-item label="邮件通知">{{ detail.notifyReply ? '请求回复通知' : '不接收回复通知' }}</el-descriptions-item>
    </template>
    <el-descriptions-item :label="isComment ? '评论内容' : '留言内容'" :span="columns">
      <div class="discussion-detail-content" :aria-label="isComment ? '评论内容' : '留言内容'">
        <p v-if="isComment || detail.contentFormat === 0" class="discussion-plain-content">{{ detail.content }}</p>
        <SafeHtml v-else :html="detail.htmlContent" />
      </div>
    </el-descriptions-item>
  </el-descriptions>
</template>

<style scoped>
.discussion-detail :deep(.el-descriptions__table) {
  table-layout: fixed;
}
.discussion-detail :deep(.el-descriptions__label) {
  width: 100px;
}
.discussion-detail-content {
  min-height: 96px;
  max-height: 360px;
  overflow: auto;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-light);
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.discussion-plain-content {
  white-space: pre-wrap;
}
.discussion-detail-content :deep(.rendered-content) {
  padding: 0;
}
.discussion-detail-content :deep(.rendered-content > :first-child) {
  margin-top: 0;
}
.discussion-detail-content :deep(.rendered-content > :last-child) {
  margin-bottom: 0;
}
</style>
