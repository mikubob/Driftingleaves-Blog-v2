export * from '../shared/markdownAnchors.mjs'
import { uniqueHeadingId } from '../shared/markdownAnchors.mjs'

/** 为 HTML 回退内容补齐缺失标题 ID，保留服务端已有 ID 并避开重复。 */
export const ensureHeadingIds = (container) => {
  if (!container) return
  const used = new Set(
    Array.from(container.querySelectorAll('[id]'), (node) => node.id).filter(
      Boolean
    )
  )
  for (const heading of container.querySelectorAll('h1, h2, h3, h4, h5, h6')) {
    if (!heading.id) heading.id = uniqueHeadingId(heading.textContent, used)
  }
}
