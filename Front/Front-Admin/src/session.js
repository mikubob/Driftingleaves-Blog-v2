import { reactive } from 'vue'
import { request, setAuthorization } from './api.js'

export const session = reactive({
  user: null,
  capabilities: { roleCodes: [], permissionCodes: [], dataScopes: {} },
  menus: [],
  ready: false,
})

/** 清除当前内存身份，不持久化 Token 或用户授权。 */
export function clearSession() {
  setAuthorization('')
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
    !data.accessToken
  ) {
    throw new Error('登录响应缺少有效会话。')
  }
  setAuthorization(data.accessToken, clearSession)
  try {
    const [me, capabilities, menus] = await Promise.all([
      request('/me'),
      request('/me/capabilities'),
      request('/me/menus'),
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
    await request('/auth/logout', { method: 'POST' })
  } finally {
    clearSession()
  }
}
