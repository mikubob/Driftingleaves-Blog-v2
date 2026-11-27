let onFailure = () => {}

/** 注册请求错误处理，让各端显示异常页；请求仍返回原错误，供调用方清理和提示。 */
export function setRequestFailureHandler(handler = () => {}) {
  onFailure = handler
}

/** 报告简历请求错误，普通统计失败在当前页面处理，IP 封禁显示异常页。 */
export function reportRequestFailure(error, path) {
  const route = path.split('?')[0]
  if (error.code === 'ACCESS_BLOCKED' || !/^\/cv\/analytics\/(page-views|online\/tickets)$/.test(route)) {
    onFailure(error)
  }
  return error
}
