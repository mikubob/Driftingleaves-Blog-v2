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

/** UTC 审计时间仅在展示时转换，保留原卡片的日期和分钟信息。 */
export function formatDateTime(value) {
  if (!value) {
    return '—'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}
