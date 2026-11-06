import { reactive } from 'vue'
import { bindSessionState, invalidateSessionRequests, isId, request } from './api.js'
import { removeStoredSession } from './shared/sessionStorage.mjs'
import { reloadSessionPage, syncSession } from './shared/sessionSync.mjs'

export const session = reactive({
  user: null,
  capabilities: { roleCodes: [], permissionCodes: [], dataScopes: {} },
  menus: [],
  ready: false,
})

/** 清除当前页面的登录信息和权限。 */
export function clearSession() {
  invalidateSessionRequests()
  session.user = null
  session.capabilities = { roleCodes: [], permissionCodes: [], dataScopes: {} }
  session.menus = []
  session.ready = false
}

const notifySessionChange = syncSession('admin', recheckSession)
bindSessionState(() => session.ready ? session.user?.userId : null, clearSession, recheckSession)

/** 撤销会话成功后清理当前页面，并通知其他标签页。 */
export function sessionRevoked() {
  clearSession()
  notifySessionChange()
}

// 启动时清理旧版持久凭据，包括直接打开 OAuth 完成页的情况。
removeStoredSession('dl-admin-session')

/** 检查后台操作权限；要求 ALL 时同时核对数据范围。 */
export function can(permission, all = false) {
  return (
    session.capabilities.permissionCodes.includes(permission) &&
    (!all || session.capabilities.dataScopes[permission] === 'ALL')
  )
}

/** 登录后重新读取权限与菜单，不能从角色名称推导管理员权限。 */
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
    const me = await request('/admin/me', { expectedUserId: null })
    const expectedUserId = me.data?.userId
    if (!isId(expectedUserId)) {
      throw new Error('服务器返回的账号资料无效。')
    }
    const [capabilities, menus] = await Promise.all([
      request('/admin/me/capabilities', { expectedUserId }),
      request('/admin/me/menus', { expectedUserId }),
    ])
    if (
      !Array.isArray(capabilities.data?.permissionCodes) ||
      !Array.isArray(capabilities.data?.roleCodes) ||
      !capabilities.data?.dataScopes ||
      !Array.isArray(menus.data)
    ) {
      throw new Error('登录资料或权限加载异常，请重新登录后重试。')
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

/** 服务端撤销成功后再清除页面上的登录状态。 */
export async function logout() {
  await request('/admin/auth/logout', { method: 'POST' })
  sessionRevoked()
}

function recheckSession() {
  invalidateSessionRequests()
  reloadSessionPage()
}
