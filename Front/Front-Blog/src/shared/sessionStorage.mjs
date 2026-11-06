/** 删除迁移前保存的网页登录凭据。 */
export function removeStoredSession(key) {
  try {
    globalThis.localStorage?.removeItem(key)
  } catch {
    // 存储不可用时继续清理内存身份。
  }
}
