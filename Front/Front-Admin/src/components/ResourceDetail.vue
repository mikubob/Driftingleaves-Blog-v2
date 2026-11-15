<script setup>
import { ElButton, ElDescriptions, ElDialog } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/dialog/style/css'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { request } from '../api.js'
import RequestError from './RequestError.vue'

/** 加载资源详情并显示弹窗，调用方提供展示字段。 */
const props = defineProps({
  path: { type: String, required: true },
  title: { type: String, required: true },
  width: { type: String, default: '640px' },
  labelWidth: { type: String, default: '110px' },
  loadingText: { type: String, default: '正在加载详情…' },
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
  <el-dialog :model-value="true" :title="title" :width="width" @close="$emit('close')">
    <div
      v-loading="loading"
      class="resource-detail"
      :style="{ '--detail-label-width': labelWidth }"
      :aria-busy="loading"
    >
      <p v-if="loading" role="status" class="muted-text">{{ loadingText }}</p>
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
  width: var(--detail-label-width);
}
.resource-detail :deep(.detail-text) {
  margin: 0;
  white-space: pre-wrap;
}
</style>
