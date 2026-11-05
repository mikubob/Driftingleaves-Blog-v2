/** 只恢复未到期的 Token；身份和权限仍须从后端读取。 */
export function readStoredSession(key) {
  try {
    const stored = JSON.parse(globalThis.localStorage?.getItem(key) || 'null')
    if (stored && typeof stored.accessToken === 'string' && stored.accessToken
      && Number.isSafeInteger(stored.expiresAt) && stored.expiresAt > Date.now()) {
      return stored
    }
  } catch {
    // 浏览器禁用存储或记录损坏时，按未登录处理。
  }
  removeStoredSession(key)
  return null
}

/** 保存固定到期时间，刷新和访问都不能延长登录有效期。 */
export function writeStoredSession(key, accessToken, expiresAt) {
  try {
    globalThis.localStorage?.setItem(key, JSON.stringify({ accessToken, expiresAt }))
  } catch {
    // 存储不可用时仍允许本页登录，刷新后需要重新登录。
  }
}

/** 退出或会话失效时同时删除持久凭据。 */
export function removeStoredSession(key) {
  try {
    globalThis.localStorage?.removeItem(key)
  } catch {
    // 存储不可用时继续清理内存身份。
  }
}
