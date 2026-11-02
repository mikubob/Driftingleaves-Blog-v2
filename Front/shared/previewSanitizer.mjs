/** 创建正文预览清理器，保留编辑器所需标记和静态图标。 */
export function createPreviewFilter(xss) {
  const whiteList = xss.getDefaultWhiteList()
  // 保留编辑器的排版和同步滚动标记，不能直接使用会删掉样式类的默认白名单。
  for (const attributes of Object.values(whiteList)) {
    attributes.push('class', 'data-line')
  }
  for (const tag of ['h1', 'h2', 'h3', 'h4', 'h5', 'h6']) {
    whiteList[tag].push('id')
  }
  whiteList.code.push('language')
  whiteList.pre.push('data-code-source')
  whiteList.span.push('rn-wrapper', 'aria-hidden', 'data-tips', 'data-is-icon')
  whiteList.input = ['type', 'checked', 'disabled', 'class']
  // 只允许折叠图标用到的静态 SVG，不开放事件、外链或嵌入内容。
  whiteList.svg = ['class', 'xmlns', 'viewbox', 'width', 'height', 'fill', 'stroke',
    'stroke-width', 'stroke-linecap', 'stroke-linejoin']
  whiteList.circle = ['cx', 'cy', 'r']
  whiteList.path = ['d']
  
  return new xss.FilterXSS({
    whiteList,
    stripIgnoreTag: true,
    stripIgnoreTagBody: ['script', 'style', 'iframe', 'object'],
  })
}
