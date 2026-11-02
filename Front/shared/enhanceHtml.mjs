/** 增强后端安全 HTML 的代码展示，文章回退可复用编辑器样式，保留正文和标题id。 */
export function enhanceHtml(container, highlighter, editorStyle = false) {
  const document = container.ownerDocument
  for (const code of container.querySelectorAll('pre > code')) {
    const pre = code.parentElement
    const source = code.textContent
    const displayedSource = editorStyle ? source.replace(/^\n+|\n+$/g, '') : source
    const language = [...code.classList].find((name) => name.startsWith('language-'))?.slice(9).toLowerCase()
    // 文章回退与编辑器一样自动识别语言；留言等其他内容只高亮声明的语言。
    if (language && highlighter.getLanguage(language)) {
      code.innerHTML = highlighter.highlight(displayedSource, { language, ignoreIllegals: true }).value
    } else if (editorStyle) {
      code.innerHTML = highlighter.highlightAuto(displayedSource).value
    }

    const block = document.createElement('details')
    block.className = editorStyle ? 'md-editor-code' : 'reading-code'
    block.open = true
    const summary = document.createElement('summary')
    summary.setAttribute('aria-label', `${language || '纯文本'}代码，展开或收起`)
    const label = document.createElement('span')
    label.className = editorStyle ? 'md-editor-code-lang' : 'reading-code-language'
    label.textContent = language || '纯文本'
    const copy = document.createElement('button')
    copy.type = 'button'
    copy.className = editorStyle ? 'md-editor-copy-button' : 'reading-code-copy'
    copy.textContent = editorStyle ? '复制代码' : '复制'
    copy.setAttribute('aria-label', `复制${language || '纯文本'}代码`)
    copy.setAttribute('aria-live', 'polite')
    copy.addEventListener('click', async (event) => {
      event.preventDefault()
      event.stopPropagation()
      try {
        await navigator.clipboard.writeText(source)
        copy.textContent = '已复制'
      } catch {
        copy.textContent = '复制失败，请手动选择'
      }
    })
    if (editorStyle) {
      summary.className = 'md-editor-code-head'
      const flag = document.createElement('div')
      flag.className = 'md-editor-code-flag'
      flag.append(document.createElement('span'), document.createElement('span'), document.createElement('span'))
      const action = document.createElement('div')
      action.className = 'md-editor-code-action'
      const collapse = document.createElement('span')
      collapse.className = 'md-editor-collapse-tips'
      collapse.setAttribute('aria-hidden', 'true')
      const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
      icon.setAttribute('viewBox', '0 0 24 24')
      icon.setAttribute('width', '16')
      icon.setAttribute('height', '16')
      icon.innerHTML = '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor"/><path d="m8 10 4 4 4-4" fill="none" stroke="currentColor"/>'
      collapse.append(icon)
      action.append(label, copy, collapse)
      summary.append(flag, action)

      const sourceBlock = document.createElement('span')
      sourceBlock.className = 'md-editor-code-block'
      sourceBlock.tabIndex = 0
      sourceBlock.setAttribute('aria-label', '代码，可横向滚动')
      sourceBlock.append(...code.childNodes)
      const rowNumbers = document.createElement('span')
      rowNumbers.setAttribute('rn-wrapper', '')
      rowNumbers.setAttribute('aria-hidden', 'true')
      const lineCount = displayedSource.split('\n').length
      for (let row = 0; row < lineCount; row++) {
        rowNumbers.append(document.createElement('span'))
      }
      code.append(sourceBlock, rowNumbers)
    } else {
      summary.append(label, copy)
    }
    pre.tabIndex = 0
    pre.setAttribute('aria-label', `${language || '纯文本'}代码`)
    pre.replaceWith(block)
    block.append(summary, pre)
  }
}
