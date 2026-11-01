import { reactive } from 'vue'
import { isId, request, setAuthorization } from './api.js'
import { readStoredSession, removeStoredSession, writeStoredSession } from '../../shared/sessionStorage.mjs'

const storageKey = 'dl-blog-session'
const listeners = new Set()

/** 仅通知当前页面的连接，不跨标签页传播会话。 */
export function onSessionChange(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export const session = reactive({ user: null, permissions: [] })

export function clearSession() {
  const hadSession = session.user !== null
  setAuthorization('')
  removeStoredSession(storageKey)
  session.user = null
  session.permissions = []
  // 匿名状态重复收到失效响应时，不再触发立即重连。
  if (hadSession) {
    listeners.forEach((listener) => listener())
  }
}

export function can(permission) {
  return session.permissions.includes(permission)
}

/** 登录成功后读取真实权限，不从角色名推导前台操作资格。 */
export async function acceptSession(data) {
  if (!data || data.tokenType !== 'Bearer' || typeof data.accessToken !== 'string'
    || !data.accessToken || !isId(data.user?.userId)
    || !Number.isSafeInteger(data.expiresIn) || data.expiresIn <= 0) {
    throw new Error('登录响应缺少有效会话。')
  }
  const expiresAt = Date.now() + data.expiresIn * 1000
  await loadSession(data.accessToken)
  writeStoredSession(storageKey, data.accessToken, expiresAt)
}

/** 刷新后先向后端核验身份和权限，再恢复登录展示。 */
export async function restoreSession() {
  const stored = readStoredSession(storageKey)
  if (!stored) {
    return
  }
  try {
    await loadSession(stored.accessToken)
  } catch {
    // 读取失败时已清理会话，页面继续按未登录状态加载。
  }
}

/** 登录和刷新共用后端核验，不从浏览器存储恢复用户资料或授权。 */
async function loadSession(accessToken) {
  setAuthorization(accessToken, clearSession)
  try {
    const [me, capabilities] = await Promise.all([
      request('/blog/me'),
      request('/blog/me/capabilities'),
    ])
    if (!isId(me.data?.userId) || !Array.isArray(capabilities.data?.permissionCodes)) {
      throw new Error('用户资料或权限响应格式不正确。')
    }
    session.user = me.data
    session.permissions = capabilities.data.permissionCodes
    listeners.forEach((listener) => listener())
  } catch (error) {
    clearSession()
    throw error
  }
}

export async function logout() {
  try {
    await request('/blog/auth/logout', { method: 'POST' })
  } finally {
    clearSession()
  }
}
