<script setup>
import { ElButton, ElDescriptions, ElDialog } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/dialog/style/css'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { request } from '../api.js'
import RequestError from './RequestError.vue'

/** 共用分类、标签和友链的详情加载，调用页面提供各自展示字段。 */
const props = defineProps({
  path: { type: String, required: true },
  title: { type: String, required: true },
})
defineEmits(['close'])
const detail = ref(null)
const loading = ref(false)
const error = ref('')
const controller = new AbortController()

/** 查询最新详情，失败时清空旧资料并显示重试入口。 */
async function load() {
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    const result = await request(props.path, { signal: controller.signal })
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
  <el-dialog :model-value="true" :title="title" width="640px" @close="$emit('close')">
    <div v-loading="loading" class="resource-detail" :aria-busy="loading">
      <p v-if="loading" role="status" class="muted-text">正在加载详情…</p>
      <RequestError :message="error" @retry="load" />
      <el-descriptions v-if="detail" :column="1" border>
        <slot :detail="detail" />
      </el-descriptions>
    </div>
    <template #footer>
      <el-button @click="$emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.resource-detail {
  min-height: 100px;
  overflow-wrap: anywhere;
}
.resource-detail :deep(.el-descriptions__table) {
  table-layout: fixed;
}
.resource-detail :deep(.el-descriptions__label) {
  width: 110px;
}
.resource-detail :deep(.detail-text) {
  margin: 0;
  white-space: pre-wrap;
}
</style>
