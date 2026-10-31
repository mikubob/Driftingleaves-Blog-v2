import { request } from './api.js'
let fingerprint

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

export function trackingPayload(path, title, articleId = null) {
  const fingerprint = getFingerprint()
  let referer = null
  if (document.referrer) {
    const source = new URL(document.referrer)
    if (['http:', 'https:'].includes(source.protocol)) {
      referer = `${source.origin}${source.pathname}`
    }
  }
  return {
    fingerprint,
    siteCode: 'blog',
    pagePath: path.split(/[?#]/, 1)[0],
    pageTitle: title.slice(0, 200),
    referer,
    articleId,
  }
}

export async function recordPage(path, title, articleId = null) {
  try {
    await request('/analytics/page-views', {
      method: 'POST', body: trackingPayload(path, title, articleId),
    })
  } catch {
    console.warn('博客浏览统计未完成；公开阅读不受影响。')
  }
}
