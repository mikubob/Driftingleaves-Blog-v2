let onFailure = () => {}

/** 各端在入口接入异常页，请求层仍抛出原错误，保留调用方的清理和反馈。 */
export function setRequestFailureHandler(handler = () => {}) {
  onFailure = handler
}

/** 统计失败沿用静默处理，不中断已成功加载的公开内容。 */
export function reportRequestFailure(error, path) {
  if (!/^\/(home|blog|cv)\/analytics\/(page-views|online\/tickets)$/.test(path.split('?')[0])) {
    onFailure(error)
  }
  return error
}
