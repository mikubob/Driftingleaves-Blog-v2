<script setup>
/** 展示作者昵称及当前有效身份。 */
defineProps({ record: { type: Object, required: true } })
</script>

<template>
  <span class="discussion-author">
    <span>{{ record.author?.nickname || '已删除用户' }}</span>
    <span v-if="record.articleAuthor" class="discussion-badge badge-article-author">本文作者</span>
    <span v-if="record.siteAuthor" class="discussion-badge badge-site-author">本站作者</span>
    <span v-if="record.siteAdmin" class="discussion-badge badge-site-admin">管理员</span>
  </span>
</template>

<style scoped>
.discussion-author {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.discussion-badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.8;
  white-space: nowrap;
}
.badge-article-author {
  color: #ac3980;
  background: #fce8f4;
}
.badge-site-author {
  color: #008c81;
  background: #e1f5f0;
}
.badge-site-admin {
  color: #3475be;
  background: #e7f0fd;
}
</style>
