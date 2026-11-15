<script setup>
import { ElDescriptionsItem, ElTag } from 'element-plus'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/tag/style/css'
import { formatTime } from '../format.js'
import ImagePreview from './ImagePreview.vue'
import ResourceDetail from './ResourceDetail.vue'
import StatusTag from './StatusTag.vue'

/** 展示指定用户的资料和角色。 */
defineProps({ userId: { type: String, required: true } })
defineEmits(['close'])
</script>

<template>
  <ResourceDetail
    :path="`/admin/users/${userId}`"
    title="用户详情"
    width="680px"
    label-width="120px"
    loading-text="正在加载用户详情…"
    @close="$emit('close')"
  >
    <template #default="{ detail }">
      <el-descriptions-item label="用户 ID">{{ detail.userId }}</el-descriptions-item>
      <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
      <el-descriptions-item label="昵称">{{ detail.nickname }}</el-descriptions-item>
      <el-descriptions-item label="头像">
        <ImagePreview v-if="detail.avatar" :src="detail.avatar" alt="用户头像" />
        <span v-else>未设置</span>
      </el-descriptions-item>
      <el-descriptions-item label="联系邮箱">{{ detail.email || '未设置' }}</el-descriptions-item>
      <el-descriptions-item label="个人简介">
        <p class="user-intro">{{ detail.intro || '未填写' }}</p>
      </el-descriptions-item>
      <el-descriptions-item label="账号状态">
        <StatusTag :value="detail.status" kind="enabled" />
      </el-descriptions-item>
      <el-descriptions-item label="本站密码">{{ detail.hasPassword ? '已设置' : '未设置' }}</el-descriptions-item>
      <el-descriptions-item label="已分配角色">
        <div v-if="detail.roles.length" class="user-roles">
          <el-tag
            v-for="role in detail.roles"
            :key="role.roleId"
            :type="role.status === 1 ? 'info' : 'danger'"
          >
            {{ role.roleName }}（{{ role.roleCode }} / ID：{{ role.roleId }}）{{ role.status === 0 ? '（已禁用）' : '' }}
          </el-tag>
        </div>
        <span v-else>未分配角色</span>
      </el-descriptions-item>
      <el-descriptions-item label="最近登录">{{ formatTime(detail.lastLoginTime) }}</el-descriptions-item>
      <el-descriptions-item label="最近登录 IP">{{ detail.lastLoginIp || '—' }}</el-descriptions-item>
      <el-descriptions-item label="最近登录地域">{{ detail.lastLoginLocation || '未知' }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
    </template>
  </ResourceDetail>
</template>

<style scoped>
.user-intro {
  margin: 0;
  white-space: pre-wrap;
}
.user-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.user-roles :deep(.el-tag) {
  max-width: 100%;
  height: auto;
  min-height: 24px;
  padding: 4px 8px;
  line-height: 1.5;
  white-space: normal;
}
</style>
