import { reactive } from 'vue'
import { bindSessionState, invalidateSessionRequests, isId, request } from './api.js'
import { removeStoredSession } from './shared/sessionStorage.mjs'
import { reloadSessionPage, syncSession } from './shared/sessionSync.mjs'

const listeners = new Set()

/** 仅通知当前页面的连接，不跨标签页传播会话。 */
export function onSessionChange(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export const session = reactive({ user: null, permissions: [] })

/** 清除博客登录信息，并在从登录变为匿名时通知在线连接。 */
export function clearSession() {
  const hadSession = session.user !== null
  invalidateSessionRequests()
  session.user = null
  session.permissions = []
  // 匿名状态重复收到失效响应时，不再触发立即重连。
  if (hadSession) {
    listeners.forEach((listener) => listener())
  }
}

const notifySessionChange = syncSession('blog', recheckSession)
bindSessionState(() => session.user?.userId, clearSession, recheckSession)

/** 撤销会话成功后清理当前页面，并通知其他标签页。 */
export function sessionRevoked() {
  clearSession()
  notifySessionChange()
}

// 启动时清理旧版持久凭据，包括直接打开 OAuth 完成页的情况。
removeStoredSession('dl-blog-session')

/** 检查当前登录用户是否拥有指定的博客操作权限。 */
export function can(permission) {
  return session.permissions.includes(permission)
}

/** 登录成功后读取真实权限，不从角色名推导前台操作资格。 */
export async function acceptSession(data) {
  if (!isId(data?.user?.userId) || !Number.isSafeInteger(data.expiresIn) || data.expiresIn <= 0) {
    throw new Error('登录响应缺少有效会话。')
  }
  invalidateSessionRequests()
  notifySessionChange()
  await loadSession()
}

/** 刷新后向后端核验身份和权限。 */
export async function restoreSession() {
  try {
    await loadSession()
  } catch {
    // 未登录时继续启动，网络和服务错误由请求层报告。
  }
}

/** 登录和刷新共用同一套服务端核验。 */
async function loadSession() {
  try {
    const me = await request('/blog/me', { expectedUserId: null })
    const expectedUserId = me.data?.userId
    if (!isId(expectedUserId)) {
      throw new Error('服务器返回的账号资料无效。')
    }
    const capabilities = await request('/blog/me/capabilities', { expectedUserId })
    if (!Array.isArray(capabilities.data?.permissionCodes)) {
      throw new Error('用户资料或权限加载异常，请重新登录后重试。')
    }
    session.user = me.data
    session.permissions = capabilities.data.permissionCodes
    listeners.forEach((listener) => listener())
  } catch (error) {
    clearSession()
    throw error
  }
}

/** 服务端撤销成功后再清除页面上的登录状态。 */
export async function logout() {
  await request('/blog/auth/logout', { method: 'POST' })
  sessionRevoked()
}

function recheckSession() {
  invalidateSessionRequests()
  // 邮件页已从地址栏清除票据，重新核验身份时保留当前页的票据。
  if (globalThis.window?.location.pathname.startsWith('/subscription/')) {
    void restoreSession()
    return
  }
  reloadSessionPage()
}
