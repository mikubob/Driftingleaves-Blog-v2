/** 增强后端安全 HTML 的代码展示，保留正文和标题id。 */
export function enhanceHtml(container, highlighter) {
  const document = container.ownerDocument
  for (const code of container.querySelectorAll('pre > code')) {
    const pre = code.parentElement
    const source = code.textContent
    const language = [...code.classList].find((name) => name.startsWith('language-'))?.slice(9).toLowerCase()
    if (language && highlighter.getLanguage(language)) {
      code.innerHTML = highlighter.highlight(source, { language, ignoreIllegals: true }).value
    }

    const block = document.createElement('details')
    block.className = 'reading-code'
    block.open = true
    const summary = document.createElement('summary')
    summary.setAttribute('aria-label', `${language || '纯文本'}代码，展开或收起`)
    const label = document.createElement('span')
    label.className = 'reading-code-language'
    label.textContent = language || '纯文本'
    const copy = document.createElement('button')
    copy.type = 'button'
    copy.className = 'reading-code-copy'
    copy.textContent = '复制'
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
    summary.append(label, copy)
    pre.tabIndex = 0
    pre.setAttribute('aria-label', `${language || '纯文本'}代码`)
    pre.replaceWith(block)
    block.append(summary, pre)
  }
}
