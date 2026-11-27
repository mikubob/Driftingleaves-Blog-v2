import { request } from './api.js'
let fingerprint
let hasTracked = false
let lastTrackedUrl = null

/** 清理来源地址，只保留 HTTP(S) 协议、主机与路径，去掉查询参数和片段。 */
function normalizeReferer(value) {
  if (!value) {
    return null
  }
  try {
    const source = new URL(value)
    if (['http:', 'https:'].includes(source.protocol)) {
      return `${source.origin}${source.pathname}`
    }
  } catch {
    return null
  }
  return null
}

/** 只保存随机统计标识，不保存会话、用户资料或页面票据。 */
export function getFingerprint() {
  if (!fingerprint) {
    try {
      fingerprint = localStorage.getItem('dl-blog-visitor')
      if (!/^[A-Za-z0-9-]{16,150}$/.test(fingerprint || '')) {
        fingerprint = crypto.randomUUID()
        localStorage.setItem('dl-blog-visitor', fingerprint)
      }
    } catch {
      fingerprint = crypto.randomUUID()
    }
  }
  return fingerprint
}

/** 组装访问统计，首次进入使用外部 document.referrer，站内 SPA 切换使用上一页路径。 */
export function trackingPayload(path, title, articleId = null, previousUrl = null) {
  const fingerprint = getFingerprint()
  const cleanPath = path.split(/[?#]/, 1)[0]
  const currentUrl = typeof window !== 'undefined' && window.location?.origin
    ? `${window.location.origin}${cleanPath}`
    : null
  let referer = null
  if (previousUrl) {
    if (previousUrl !== currentUrl) {
      referer = normalizeReferer(previousUrl)
    }
  } else if (!hasTracked && typeof document !== 'undefined') {
    referer = normalizeReferer(document.referrer)
  }
  return {
    fingerprint,
    siteCode: 'blog',
    pagePath: cleanPath,
    pageTitle: title.slice(0, 200),
    referer,
    articleId,
  }
}

/** 上报一次博客访问，统计失败不影响阅读。 */
export async function recordPage(path, title, articleId = null) {
  const payload = trackingPayload(path, title, articleId, hasTracked ? lastTrackedUrl : null)
  hasTracked = true
  if (typeof window !== 'undefined' && window.location?.origin) {
    lastTrackedUrl = `${window.location.origin}${payload.pagePath}`
  }
  try {
    await request('/blog/analytics/page-views', {
      method: 'POST', body: payload,
    })
  } catch {
    console.warn('博客浏览统计未完成；公开阅读不受影响。')
  }
}
