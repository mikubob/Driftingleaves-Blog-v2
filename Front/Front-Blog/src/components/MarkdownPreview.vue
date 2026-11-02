<script setup>
import { computed } from 'vue'
import { MdPreview } from 'md-editor-v3'
import { ElOption, ElSelect } from 'element-plus'
import { apiRoot } from '../api.js'
import { useThemeStore } from '@/stores'
import { previewThemes, useMarkdownPreferences, useMarkdownRendering } from '../../../shared/useMarkdownPreview.js'
const props = defineProps({
  markdown: { type: String, required: true },
})
const emit = defineEmits(['rendered'])
const themeStore = useThemeStore()
const previewTheme = useMarkdownPreferences('reader')
const themeLabels = {
  default: 'Default',
  github: 'GitHub',
  vuepress: 'VuePress',
  'mk-cute': 'Mk Cute',
  'smart-blue': 'Smart Blue',
  cyanosis: 'Cyanosis',
}
const source = computed(() => props.markdown)
const { renderer, sanitize, headingId, onRendered } = useMarkdownRendering(source, value => {
  const path = value.replace(/^https?:\/\/[^/]+(?=\/api\/v1\/blog\/articles\/)/, '')
  if (/^\/api\/v1\/blog\/articles\/[1-9]\d*\/files\/[1-9]\d*\/content$/.test(path)) {
    return `${apiRoot}${path.slice('/api/v1'.length)}`
  }
  return value
})

async function contentRendered() {
  await onRendered()
  emit('rendered')
}
</script>

<template>
  <div class="markdown-preview reader-markdown">
    <div class="reader-preferences">
      <label for="reading-theme-select" class="reader-theme-label">
        <svg viewBox="0 0 20 20" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
          <path d="M3 4h14M3 10h14M3 16h14M7 2v4M13 8v4M8 14v4" />
        </svg>
        阅读主题
      </label>
      <ElSelect id="reading-theme-select" v-model="previewTheme" aria-label="阅读排版" size="small" class="reader-theme-select" :teleported="false">
        <ElOption v-for="item in previewThemes" :key="item" :label="themeLabels[item]" :value="item" />
      </ElSelect>
    </div>
    <MdPreview
      ref="renderer"
      id="public-article-preview"
      :model-value="markdown"
      :preview-theme="previewTheme"
      :theme="themeStore.isDark ? 'dark' : 'light'"
      :sanitize="sanitize"
      :md-heading-id="headingId"
      no-mermaid
      no-katex
      no-echarts
      @on-html-changed="contentRendered"
    />

  </div>
</template>

<style scoped>
.reader-preferences {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 0 0 14px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--blog-border-light);
  color: var(--blog-text2);
  font: 12px/1.5 var(--blog-sans);
}

.reader-theme-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.reader-theme-select {
  width: 138px;
  --el-border-radius-base: 8px;
  --el-fill-color-blank: var(--blog-hover);
  --el-border-color: var(--blog-border-light);
  --el-text-color-regular: var(--blog-text2);
}

.reader-theme-select :deep(.el-select__wrapper) {
  min-height: 30px;
  box-shadow: 0 0 0 1px var(--blog-border-light) inset;
}

/* 正文融入文章卡片，六种主题只改变排版和强调样式。 */
.reader-markdown :deep(.md-editor) {
  background: transparent;
  border: 0;
  --md-color: var(--blog-text);
  --md-bk-color: transparent;
}

.reader-markdown :deep(div.md-editor-preview) {
  --md-theme-color: var(--blog-text);
  --md-theme-bg-color: var(--blog-card);
  --md-theme-bg-color-inset: var(--blog-hover);
  --md-theme-border-color: var(--blog-border);
  font-size: 15.5px;
  line-height: 1.85;
  color: var(--blog-text);
}

.reader-markdown :deep(.md-editor-dark div.mk-cute-theme) {
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
}

.reader-markdown :deep(th),
.reader-markdown :deep(td) {
  min-width: 6em;
  overflow-wrap: normal;
}
</style>
