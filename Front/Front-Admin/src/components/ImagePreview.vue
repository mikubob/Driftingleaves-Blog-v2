<script setup>
import { computed, ref, watch } from 'vue'
import { getImage } from '../api.js'
import { adminArticleUrl } from '../editorPreview.mjs'

/** 预览外链图片和后台附件，切换地址或卸载时释放临时地址。 */
const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '图片预览' },
})
const imageUrl = ref('')
const error = ref('')
const source = computed(() => {
  const value = props.src.trim()
  const path = adminArticleUrl(value).replace(
    /^(?:https?:\/\/[^/]+)?\/api\/v1\/blog\/music\/files\/([1-9]\d*)\/content$/,
    '/api/v1/admin/files/$1/content',
  )
  if (/^\/api\/v1\/admin\/(?:files\/[1-9]\d*|articles\/[1-9]\d*\/files\/[1-9]\d*)\/content$/.test(path)) {
    return path
  }
  return /^https?:\/\//i.test(value) ? value : ''
})

watch(source, async (value, previous, onCleanup) => {
  imageUrl.value = ''
  error.value = ''
  if (!value) {
    return
  }
  if (!value.startsWith('/api/v1/admin/')) {
    imageUrl.value = value
    return
  }

  // 站内附件通过现有接口携带 Token，外链直接由浏览器加载。
  const controller = new AbortController()
  let objectUrl = ''
  onCleanup(() => {
    controller.abort()
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl)
    }
  })
  try {
    const url = await getImage(value, controller.signal)
    if (controller.signal.aborted) {
      URL.revokeObjectURL(url)
      return
    }
    objectUrl = url
    imageUrl.value = url
  } catch (failure) {
    if (!controller.signal.aborted) {
      error.value = failure.message
    }
  }
}, { immediate: true })
</script>

<template>
  <span v-if="source" class="image-preview">
    <img
      v-if="imageUrl && !error"
      :src="imageUrl"
      :alt="alt"
      @error="error = '图片加载失败，请检查地址或访问权限。'"
    />
    <span v-else role="status">{{ error || '图片加载中…' }}</span>
  </span>
</template>

<style scoped>
.image-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 128px;
  max-width: 100%;
  height: 96px;
  padding: 6px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fafafa;
  color: #909399;
  font-size: 12px;
  text-align: center;
  overflow-wrap: anywhere;
}

.image-preview img {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
</style>
