import { reactive } from 'vue'
import { isId, request, setAuthorization } from './api.js'

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
    || !data.accessToken || !isId(data.user?.userId)) {
    throw new Error('登录响应缺少有效会话。')
  }
  setAuthorization(data.accessToken, clearSession)
  try {
    const { data: capabilities } = await request('/me/capabilities')
    if (!Array.isArray(capabilities?.permissionCodes)) {
      throw new Error('用户权限响应格式不正确。')
    }
    session.user = data.user
    session.permissions = capabilities.permissionCodes
    listeners.forEach((listener) => listener())
  } catch (error) {
    clearSession()
    throw error
  }
}

export async function logout() {
  try {
    await request('/auth/logout', { method: 'POST' })
  } finally {
    clearSession()
  }
}
