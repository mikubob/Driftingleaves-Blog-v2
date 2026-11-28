import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { config } from 'md-editor-v3'
import highlighter from 'highlight.js/lib/common'
import xss from 'xss'
import { createPreviewFilter } from './previewSanitizer.mjs'
import { uniqueHeadingId } from './markdownAnchors.mjs'
import 'md-editor-v3/lib/style.css'
import './markdown-preview.css'
import './markdown-code.css'

export const previewThemes = ['default', 'github', 'vuepress', 'mk-cute', 'smart-blue', 'cyanosis']
const filter = createPreviewFilter(xss)

// 使用本地高亮资源，普通换行与后端 CommonMark 保持一致。
config({
  editorExtensions: { highlight: { instance: highlighter } },
  markdownItConfig(md) {
    md.set({ html: true, breaks: false, linkify: true })
    md.disable(['strikethrough'])
    // 内置高亮会裁掉首尾换行，另存代码原文供复制使用。
    for (const name of ['fence', 'code_block']) {
      const render = md.renderer.rules[name]
      md.renderer.rules[name] = (tokens, index, options, environment, renderer) => {
        const html = render(tokens, index, options, environment, renderer)
        const source = encodeURIComponent(tokens[index].content)
        return html.replace('<pre', `<pre data-code-source="${source}"`)
      }
    }
  },
  markdownItPlugins: plugins => plugins.filter(plugin => ['image', 'taskList', 'heading', 'code'].includes(plugin.type)),
})

/** 保存本机偏好，监听随页面存活，关闭预览弹窗后仍能继续保存。 */
function storedPreference(key) {
  let stored
  try {
    stored = localStorage.getItem(key)
  } catch {
    // 浏览器禁用存储时仍允许本页切换。
  }
  const preference = ref(previewThemes.includes(stored) ? stored : 'github')
  watch(preference, value => {
    try {
      localStorage.setItem(key, value)
    } catch {
      // 浏览器禁用存储时只保留本页偏好。
    }
  })
  return preference
}

const authorTheme = storedPreference('dl-author-markdown-theme')
const readerTheme = storedPreference('dl-reader-markdown-theme')

/** 作者与读者使用各自的排版，不写入文章。 */
export function useMarkdownPreferences(role) {
  return role === 'author' ? authorTheme : readerTheme
}

/** 清理 Markdown 渲染结果，并按路径复用需要鉴权的图片临时地址。 */
export function useMarkdownRendering(source, mapUrl, loadImage, resetKey = null) {
  const renderer = ref(null)
  const error = ref('')
  // 按后端路径缓存每张私密图片的加载状态与 ObjectURL，避免编辑正文时反复重新拉取。
  const images = new Map()
  const usedHeadings = new Set()

  function headingId({ text, index }) {
    if (index === 1) {
      usedHeadings.clear()
    }
    return uniqueHeadingId(text, usedHeadings)
  }

  /** 释放单个图片缓存项的请求与临时地址。 */
  function releaseImage(entry) {
    entry.controller.abort()
    if (entry.url) {
      URL.revokeObjectURL(entry.url)
    }
  }

  /** 回收全部已缓存的图片请求与临时地址。 */
  function cleanup() {
    for (const entry of images.values()) {
      releaseImage(entry)
    }
    images.clear()
  }

  watch(source, (value) => {
    error.value = ''
    if (!value) {
      cleanup()
    }
  }, { flush: 'sync' })
  if (resetKey) {
    watch(resetKey, () => {
      cleanup()
      error.value = ''
      // 例如新建草稿保存后 id 变更但正文未变，需触发重渲染以重建私密图片临时地址。
      if (source.value) {
        nextTick(() => renderer.value?.rerender())
      }
    }, { flush: 'sync' })
  }
  onBeforeUnmount(cleanup)

  function sanitize(html) {
    const document = new DOMParser().parseFromString(filter.process(html), 'text/html')
    // 原始 HTML 标题也使用相同去重规则，目录与正文都读取最终 id。
    const used = new Set()
    for (const heading of document.querySelectorAll('h1,h2,h3,h4,h5,h6')) {
      heading.id = uniqueHeadingId(heading.textContent, used)
    }
    const referenced = new Set()
    for (const element of document.querySelectorAll('img[src], a[href]')) {
      const attribute = element.tagName === 'A' ? 'href' : 'src'
      const value = mapUrl(element.getAttribute(attribute))
      if (element.tagName === 'A') {
        element.setAttribute('href', value)
        element.setAttribute('rel', 'nofollow noopener noreferrer')
        continue
      }
      if (!loadImage || !value.startsWith('/api/v1/admin/')) {
        element.setAttribute('src', value)
        continue
      }
      referenced.add(value)
      // 清理函数同步返回，私密图片拿到 Blob 后才允许浏览器加载。
      element.removeAttribute('src')
      const cached = images.get(value)
      const canRetryFailed = cached?.failedAt && Date.now() - cached.failedAt > 5000
      if (cached?.url) {
        element.src = cached.url
      } else if (!cached || canRetryFailed) {
        const entry = { controller: new AbortController(), url: null, failedAt: 0 }
        images.set(value, entry)
        loadImage(value, entry.controller.signal).then(async url => {
          if (entry.controller.signal.aborted || images.get(value) !== entry) {
            URL.revokeObjectURL(url)
            return
          }
          entry.url = url
          await nextTick()
          renderer.value?.rerender()
        }).catch(failure => {
          if (!entry.controller.signal.aborted && images.get(value) === entry) {
            entry.failedAt = Date.now()
            error.value = failure.message
          }
        })
      }
    }
    // 仅回收本次渲染已不再引用的图片，保留仍在正文中的图片缓存。
    for (const [key, entry] of images.entries()) {
      if (!referenced.has(key)) {
        releaseImage(entry)
        images.delete(key)
      }
    }
    return document.body.innerHTML
  }

  async function onRendered() {
    await nextTick()
    const root = renderer.value?.$el
    for (const copy of root?.querySelectorAll('.md-editor-copy-button') || []) {
      copy.setAttribute('role', 'button')
      copy.setAttribute('tabindex', '0')
      copy.setAttribute('aria-label', '复制代码')
      copy.setAttribute('aria-live', 'polite')
      copy.setAttribute('data-processed', 'true')
      copy.onclick = async event => {
        event.preventDefault()
        event.stopPropagation()
        const pre = copy.closest('.md-editor-code').querySelector('pre')
        try {
          const source = pre.hasAttribute('data-code-source')
            ? decodeURIComponent(pre.getAttribute('data-code-source')) : pre.textContent
          await navigator.clipboard.writeText(source)
          copy.textContent = '已复制'
        } catch {
          copy.textContent = '复制失败，请手动选择'
        }
      }
      copy.onkeydown = event => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          copy.click()
        }
      }
    }
    for (const block of root?.querySelectorAll('.md-editor-code-block') || []) {
      block.tabIndex = 0
      block.setAttribute('aria-label', '代码，可横向滚动')
    }
  }

  return { renderer, error, sanitize, headingId, onRendered }
}
