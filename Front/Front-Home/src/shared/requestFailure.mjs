let onFailure = () => {}

/** 注册请求错误处理，让各端显示异常页；请求仍返回原错误，供调用方清理和提示。 */
export function setRequestFailureHandler(handler = () => {}) {
  onFailure = handler
}

/** 报告请求错误，统计及后台对话分页失败由原界面处理，保留已加载内容。 */
export function reportRequestFailure(error, path) {
  const route = path.split('?')[0]
  if (/^\/admin\/(comments|messages)\/[1-9]\d*\/conversation$/.test(route)) {
    return error
  }
  if (!/^\/(home|blog|cv)\/analytics\/(page-views|online\/tickets)$/.test(route)) {
    onFailure(error)
  }
  return error
}
