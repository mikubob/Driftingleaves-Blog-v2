<script setup>
import { ref, watch } from 'vue'
import { apiRoot } from '../api.js'
const props = defineProps({ html: { type: String, required: true }, outline: Boolean })
const emit = defineEmits(['headings'])
const container = ref(null)

// 只接收后端清理后的 HTML；跨源部署时公开附件仍从配置的 API 读取。
watch(() => [props.html, container.value], () => {
  if (!container.value) {
    return
  }
  const content = new DOMParser().parseFromString(props.html, 'text/html')
  for (const element of content.querySelectorAll('[src], a[href]')) {
    const attribute = element.tagName === 'A' ? 'href' : 'src'
    const value = element.getAttribute(attribute) || ''
    if (/^\/api\/v1\/blog\/articles\/[1-9]\d*\/files\/[1-9]\d*\/content$/.test(value)) {
      element.setAttribute(attribute, `${apiRoot}${value.slice('/api/v1'.length)}`)
    }
    if (element.tagName === 'A' && /^https?:\/\//.test(value)) {
      element.setAttribute('target', '_blank')
      element.setAttribute('rel', 'noopener noreferrer')
    }
  }
  const headings = []
  if (props.outline) {
    content.querySelectorAll('h1, h2, h3').forEach((heading, index) => {
      if (!heading.id) {
        heading.id = `section-${index}`
      }
      headings.push({ id: heading.id, text: heading.textContent, level: Number(heading.tagName[1]) })
    })
  }
  container.value.replaceChildren(...content.body.childNodes)
  emit('headings', headings)
}, { flush: 'post', immediate: true })
</script>

<template>
  <div ref="container" class="rendered-content"></div>
</template>
