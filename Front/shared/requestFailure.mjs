let onFailure = () => {}

/** 注册请求错误处理，让各端显示异常页；请求仍返回原错误，供调用方清理和提示。 */
export function setRequestFailureHandler(handler = () => {}) {
  onFailure = handler
}

/** 报告请求错误，统计请求失败时不弹出错误页，不中断已加载的公开内容。 */
export function reportRequestFailure(error, path) {
  if (!/^\/(home|blog|cv)\/analytics\/(page-views|online\/tickets)$/.test(path.split('?')[0])) {
    onFailure(error)
  }
  return error
}
