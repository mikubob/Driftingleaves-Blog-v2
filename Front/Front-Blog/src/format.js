/** 把 UTC 时间显示为本地日期，空值使用占位符。 */
export function formatDate(value) {
  if (!value) {
    return '—'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(value))
}

/** 用文章 slug 生成详情路径，对路径中的 slug 做编码。 */
export function articlePath(article) {
  return `/articles/${encodeURIComponent(article.slug)}`
}

/** 显示时间时把 UTC 转成本地时间，保留卡片上的日期和分钟。 */
export function formatDateTime(value) {
  if (!value) {
    return '—'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
