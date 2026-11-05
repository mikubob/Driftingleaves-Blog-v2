<script setup>
import { ElButton, ElDescriptions, ElDescriptionsItem, ElDialog, ElTag } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/tag/style/css'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { request } from '../api.js'
import { formatTime } from '../format.js'
import ImagePreview from './ImagePreview.vue'
import RequestError from './RequestError.vue'
import StatusTag from './StatusTag.vue'

/** 查询并展示用户详情，关闭弹窗时取消请求。 */
const props = defineProps({ userId: { type: String, required: true } })
defineEmits(['close'])
const detail = ref(null)
const loading = ref(false)
const error = ref('')
const controller = new AbortController()

/** 获取最新资料，失败时保留错误和重试入口。 */
async function load() {
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    const result = await request(`/admin/users/${props.userId}`, { signal: controller.signal })
    if (!controller.signal.aborted) {
      detail.value = result.data
    }
  } catch (failure) {
    if (!controller.signal.aborted) {
      error.value = failure.message
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => controller.abort())
</script>

<template>
  <el-dialog :model-value="true" title="用户详情" width="680px" @close="$emit('close')">
    <div v-loading="loading" class="user-detail" :aria-busy="loading">
      <p v-if="loading" role="status" class="muted-text">正在加载用户详情…</p>
      <RequestError :message="error" @retry="load" />
      <el-descriptions v-if="detail" :column="1" border>
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
      </el-descriptions>
    </div>
    <template #footer>
      <el-button @click="$emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.user-detail {
  min-height: 100px;
  overflow-wrap: anywhere;
}
.user-detail :deep(.el-descriptions__table) {
  table-layout: fixed;
}
.user-detail :deep(.el-descriptions__label) {
  width: 120px;
}
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
