/** 检查外部链接，只允许 HTTP(S) 和相对路径；非法地址返回 undefined，使链接不可点击。 */
export const safeLink = (value) => {
  if (typeof value !== 'string' || !value.trim()) {
    return undefined
  }
  try {
    const url = new URL(value, 'https://local.invalid')
    return ['http:', 'https:'].includes(url.protocol) ? value : undefined
  } catch {
    return undefined
  }
}
