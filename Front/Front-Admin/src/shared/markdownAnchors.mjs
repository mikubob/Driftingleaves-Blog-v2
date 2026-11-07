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
  while (used.has(id)) {
    id = `${base}-${++suffix}`
  }
  used.add(id)
  return id
}
