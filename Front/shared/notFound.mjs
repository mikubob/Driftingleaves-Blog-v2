/** 只上报 SPA 未知路由；不用业务请求层，避免上报失败再触发错误页或自动重试。 */
export async function reportNotFound(apiRoot, site) {
  try {
    await fetch(`${apiRoot}/${site}/security/not-found`, { method: 'POST', credentials: 'omit' })
  } catch {
    // 上报失败不影响当前错误页，也不再发送新请求。
  }
}
