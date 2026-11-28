let onFailure = null
let pendingFailure = null

/** 注册请求错误处理，让应用显示异常页；若挂载前已触发致命错误则立即补发。 */
export function setRequestFailureHandler(handler = null) {
  onFailure = typeof handler === 'function' ? handler : null
  if (onFailure && pendingFailure) {
    const error = pendingFailure
    pendingFailure = null
    onFailure(error)
  }
}

/**
 * 报告请求错误。
 * 仅站点封禁（ACCESS_BLOCKED）或初始化会话失败触发全局异常页，
 * 页内业务请求的 5xx、429 与网络异常交由当前页面局部提示，避免卸载编辑界面丢失内容。
 */
export function reportRequestFailure(error, _path, { fatal = false } = {}) {
  if (error?.code !== 'ACCESS_BLOCKED' && !fatal) {
    return error
  }
  if (onFailure) {
    onFailure(error)
  } else {
    pendingFailure = error
  }
  return error
}
