let onFailure = () => {}

/** 注册请求错误处理，让各端显示异常页；请求仍返回原错误，供调用方清理和提示。 */
export function setRequestFailureHandler(handler = () => {}) {
  onFailure = handler
}

/** 报告请求错误，认证限流和局部请求失败由当前界面提示。 */
export function reportRequestFailure(error, path) {
  const route = path.split('?')[0]
  if (error.status === 429 && /^\/(admin|blog)\/auth\//.test(route)) {
    return error
  }
  if (/^\/admin\/(comments|messages)\/[1-9]\d*\/conversation$/.test(route)) {
    return error
  }
  if (!/^\/(home|blog|cv)\/analytics\/(page-views|online\/tickets)$/.test(route)) {
    onFailure(error)
  }
  return error
}
