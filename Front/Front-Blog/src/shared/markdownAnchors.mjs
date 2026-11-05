/** 生成标题锚点，规则与后端一致，空标题使用 section，保留 Unicode 字母和数字。 */
const headingSlug = (text) =>
  text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{M}\p{N}_\s-]/gu, '')
    .replace(/\s/g, '-') || 'section'

/** 生成不重复的标题id并记入 used 集合，同名标题按顺序增加后缀。 */
export const uniqueHeadingId = (text, used) => {
  const base = headingSlug(text)
  let id = base
  let suffix = 0
  while (used.has(id)) id = `${base}-${++suffix}`
  used.add(id)
  return id
}

/** 查找页内锚点，使用解码后的 id/name，目标缺失或编码错误时返回 null，不拼接 CSS 选择器。 */
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
