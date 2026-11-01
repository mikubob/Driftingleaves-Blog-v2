import { reactive } from 'vue'
import { request, setAuthorization } from './api.js'
import { readStoredSession, removeStoredSession, writeStoredSession } from '../../shared/sessionStorage.mjs'

const storageKey = 'dl-admin-session'
export const session = reactive({
  user: null,
  capabilities: { roleCodes: [], permissionCodes: [], dataScopes: {} },
  menus: [],
  ready: false,
})

/** 清除内存身份及持久凭据，用户授权每次从后端读取。 */
export function clearSession() {
  setAuthorization('')
  removeStoredSession(storageKey)
  session.user = null
  session.capabilities = { roleCodes: [], permissionCodes: [], dataScopes: {} }
  session.menus = []
  session.ready = false
}

export function can(permission, all = false) {
  return (
    session.capabilities.permissionCodes.includes(permission) &&
    (!all || session.capabilities.dataScopes[permission] === 'ALL')
  )
}

/** 登录后重新读取权限与菜单，不能从角色名称推导管理员权限。 */
export async function acceptSession(data) {
  if (
    !data ||
    data.tokenType !== 'Bearer' ||
    typeof data.accessToken !== 'string' ||
    !data.accessToken ||
    !Number.isSafeInteger(data.expiresIn) ||
    data.expiresIn <= 0
  ) {
    throw new Error('登录响应缺少有效会话。')
  }
  const expiresAt = Date.now() + data.expiresIn * 1000
  await loadSession(data.accessToken)
  writeStoredSession(storageKey, data.accessToken, expiresAt)
}

/** 刷新后核验服务端身份、权限和菜单，保留原到期时间。 */
export async function restoreSession() {
  const stored = readStoredSession(storageKey)
  if (!stored) {
    return
  }
  try {
    await loadSession(stored.accessToken)
  } catch {
    // 读取失败时已清理会话，由路由守卫回到登录页。
  }
}

/** 登录和恢复都重新读取后台权限，避免信任浏览器保存的授权。 */
async function loadSession(accessToken) {
  setAuthorization(accessToken, clearSession)
  try {
    const [me, capabilities, menus] = await Promise.all([
      request('/admin/me'),
      request('/admin/me/capabilities'),
      request('/admin/me/menus'),
    ])
    if (
      !me.data ||
      !Array.isArray(capabilities.data?.permissionCodes) ||
      !Array.isArray(capabilities.data?.roleCodes) ||
      !capabilities.data?.dataScopes ||
      !Array.isArray(menus.data)
    ) {
      throw new Error('登录资料或权限响应格式不正确。')
    }
    if (!capabilities.data.permissionCodes.includes('admin:access')) {
      throw new Error('当前账号没有进入管理后台的权限。')
    }
    session.user = me.data
    session.capabilities = capabilities.data
    session.menus = menus.data
    session.ready = true
  } catch (error) {
    clearSession()
    throw error
  }
}

export async function logout() {
  try {
    await request('/admin/auth/logout', { method: 'POST' })
  } finally {
    clearSession()
  }
}
