export function formatDate(value) {
  if (!value) {
    return '—'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(value))
}

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
