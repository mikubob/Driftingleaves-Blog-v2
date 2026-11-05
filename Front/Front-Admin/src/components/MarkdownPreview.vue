<script setup>
import { ElOption, ElSelect } from 'element-plus'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import { computed } from 'vue'
import { MdPreview, MdCatalog } from 'md-editor-v3'
import { getImage } from '../api.js'
import { adminArticleUrl } from '../editorPreview.mjs'
import { previewThemes, useMarkdownPreferences, useMarkdownRendering } from '../shared/useMarkdownPreview.js'
const props = defineProps({ markdown: { type: String, default: '' } })
const previewTheme = useMarkdownPreferences('author')
const source = computed(() => props.markdown)
const { renderer, error, sanitize, headingId, onRendered } = useMarkdownRendering(source, adminArticleUrl, getImage)
</script>

<template>
  <div class="markdown-preview">
    <div class="markdown-preferences">
      <label for="saved-preview-theme">作者排版</label>
      <el-select id="saved-preview-theme" v-model="previewTheme" aria-label="作者排版" size="small">
        <el-option v-for="item in previewThemes" :key="item" :label="item" :value="item" />
      </el-select>
    </div>
    <p v-if="error" role="alert" class="error-text">{{ error }}</p>
    <MdPreview
      ref="renderer"
      id="saved-article-preview"
      :model-value="markdown"
      :preview-theme="previewTheme"
      theme="light"
      :sanitize="sanitize"
      :md-heading-id="headingId"
      no-mermaid
      no-katex
      no-echarts
      @on-html-changed="onRendered"
    />
    <details v-if="markdown">
      <summary>文章目录</summary>
      <MdCatalog editor-id="saved-article-preview" :md-heading-id="headingId" />
    </details>
  </div>
</template>
