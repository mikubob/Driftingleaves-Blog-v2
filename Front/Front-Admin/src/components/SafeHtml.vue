<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { getImage } from '../api.js'

const props = defineProps({ html: { type: String, default: '' } })
const container = ref(null)
const error = ref('')
let controller
let objectUrls = []

function cleanup() {
  controller?.abort()
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
  objectUrls = []
}

// HTML 仅接收后端清理后的正文，私密图片替换为当前页面临时 URL。
watch(
  () => props.html,
  async (html) => {
    cleanup()
    error.value = ''
    const current = new AbortController()
    controller = current
    const document = new DOMParser().parseFromString(html, 'text/html')
    for (const image of document.querySelectorAll('img')) {
      const source = image.getAttribute('src') || ''
      if (source.startsWith('/api/v1/admin/')) {
        image.removeAttribute('src')
        try {
          const url = await getImage(source, current.signal)
          if (current.signal.aborted) {
            URL.revokeObjectURL(url)
            return
          }
          objectUrls.push(url)
          image.src = url
        } catch (failure) {
          if (failure.name === 'AbortError') {
            return
          }
          error.value = failure.message
        }
      }
    }
    // 首次立即监听可能早于容器挂载，等本轮 DOM 更新后再填正文。
    await nextTick()
    if (!current.signal.aborted && container.value) {
      container.value.replaceChildren(...document.body.childNodes)
    }
  },
  { immediate: true, flush: 'post' },
)

onBeforeUnmount(cleanup)
</script>

<template>
  <p v-if="error" role="alert" class="error-text">{{ error }}</p>
  <div ref="container" class="rendered-content"></div>
</template>
