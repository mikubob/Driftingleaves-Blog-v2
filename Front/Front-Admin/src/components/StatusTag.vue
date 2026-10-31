<script setup>
const props = defineProps({ value: Number, kind: { type: String, default: 'display' } })
const labels = {
  display: { 0: '已隐藏', 1: '展示中' },
  enabled: { 0: '已禁用', 1: '已启用' },
  article: { 0: '草稿', 1: '已发布', 2: '已下架' },
  review: { 0: '待审核', 1: '已通过', 2: '已驳回', 3: '已删除' },
  link: { 0: '待审核', 1: '展示中', 2: '已下架' },
  subscription: { 0: '待验证', 1: '已订阅', 2: '已退订' },
}
</script>

<template>
  <span :class="['status-tag', { 'is-active': value === 1, 'is-pending': value === 0 }]">
    <i aria-hidden="true"></i>
    {{ labels[kind]?.[value] ?? '未知状态' }}
  </span>
</template>
