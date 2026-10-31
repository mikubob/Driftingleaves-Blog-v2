/** 外部数据链接仅允许 HTTP(S) 和相对路径；非法或可执行协议返回 undefined，使锚点不可导航。 */
export const safeLink = (value) => {
  if (typeof value !== 'string' || !value.trim()) return undefined
  try {
    const url = new URL(value, 'https://local.invalid')
    return ['http:', 'https:'].includes(url.protocol) ? value : undefined
  } catch {
    return undefined
  }
}
