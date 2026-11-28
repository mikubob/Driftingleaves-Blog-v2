<script setup>
import { ref, watch } from 'vue'
import { apiRoot } from '../api.js'
import { enhanceHtml } from '../shared/enhanceHtml.mjs'
import '../shared/reading-content.css'

let highlighterPromise = null

/** 仅在内容包含代码块时按需加载 highlight.js；失败时重置缓存并降级展示原文。 */
function loadHighlighter() {
  if (!highlighterPromise) {
    highlighterPromise = import('highlight.js/lib/common')
      .then((module) => module.default)
      .catch(() => {
        highlighterPromise = null
        return null
      })
  }
  return highlighterPromise
}

/** 接收后端清理过的 HTML，按需提取文章标题。 */
const props = defineProps({
  html: { type: String, required: true },
  outline: Boolean,
})
/** 将处理后的标题列表交给父组件。 */
const emit = defineEmits(['headings'])
const container = ref(null)
let renderToken = 0

// 只接收后端清理后的 HTML；跨源部署时公开附件仍从配置的 API 读取。
watch(() => [props.html, container.value], async () => {
  if (!container.value) {
    return
  }
  const currentToken = ++renderToken
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
  if (content.body.querySelector('pre > code')) {
    const highlighter = await loadHighlighter()
    if (currentToken !== renderToken || !container.value) {
      return
    }
    enhanceHtml(content.body, highlighter)
  }
  container.value.replaceChildren(...content.body.childNodes)
  emit('headings', headings)
}, { flush: 'post', immediate: true })
</script>

<template>
  <div
    ref="container"
    class="rendered-content"
  ></div>
</template>
