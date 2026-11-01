/** 与后端相同的标题 slug；空标题使用 section，保留 Unicode 字母与数字。 */
const headingSlug = (text) =>
  text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{M}\p{N}_\s-]/gu, '')
    .replace(/\s/g, '-') || 'section'

/** 在当前文档 used 集合中分配并登记唯一标题 ID；同名按顺序递增后缀。 */
export const uniqueHeadingId = (text, used) => {
  const base = headingSlug(text)
  let id = base
  let suffix = 0
  while (used.has(id)) id = `${base}-${++suffix}`
  used.add(id)
  return id
}

/** 在容器内按解码后的 id/name 查找片段目标；缺失/非法编码返回 null，不拼 CSS 选择器。 */
export const findFragmentTarget = (container, hash) => {
  if (!container || !hash.startsWith('#') || hash === '#') return null
  let id
  try {
    id = decodeURIComponent(hash.slice(1))
  } catch {
    return null
  }
  // 直接比较 ID，避免中文、点和括号被当成 CSS 选择器。
  return Array.from(container.querySelectorAll('[id], a[name]')).find(
    (node) => node.id === id || node.getAttribute('name') === id
  )
}

