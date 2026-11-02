<script setup>
// 评论和留言共用发布时的属地与设备标识，历史记录由页面统一显示未知。
defineProps({ item: { type: Object, required: true } })
</script>

<template>
  <span class="discussion-meta-item" aria-label="IP 属地">
    <svg class="discussion-meta-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z" />
      <circle cx="12" cy="10" r="3" />
    </svg>
    {{ item.location || '未知' }}
  </span>
  <span class="discussion-meta-item" aria-label="操作系统">
    <svg class="discussion-meta-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
      <rect width="20" height="14" x="2" y="3" rx="2" />
      <path d="M8 21h8M12 17v4" />
    </svg>
    {{ item.userAgentOs || '未知' }}
  </span>
  <span class="discussion-meta-item" aria-label="浏览器">
    <svg class="discussion-meta-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <circle cx="12" cy="12" r="4" />
      <path d="M21.17 8H2.83M21.17 16H2.83M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z" />
    </svg>
    {{ item.userAgentBrowser || '未知' }}
  </span>
</template>

<style scoped>
.discussion-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #b0b4bb;
}
.discussion-meta-icon {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}
</style>
