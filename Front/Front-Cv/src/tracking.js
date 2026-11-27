let fingerprint

/** 生成非身份用途的随机统计标识，用于简历端在线人数和访问统计。 */
export function getFingerprint() {
  if (fingerprint) {
    return fingerprint
  }
  try {
    fingerprint = localStorage.getItem('dl-cv-visitor')
    if (!/^[A-Za-z0-9-]{16,150}$/.test(fingerprint || '')) {
      fingerprint = crypto.randomUUID()
      localStorage.setItem('dl-cv-visitor', fingerprint)
    }
  } catch {
    fingerprint = crypto.randomUUID()
  }
  return fingerprint
}

/** 组装简历访问统计，去掉站内路径和来源地址的查询参数与片段。 */
export function createPageView(visitorFingerprint, pagePath, pageTitle, referer) {
  let cleanReferer = null
  try {
    const url = new URL(referer)
    if (['http:', 'https:'].includes(url.protocol)) {
      cleanReferer = `${url.origin}${url.pathname}`
    }
  } catch {
    // 无来源或非 HTTP(S) 来源按 null 上报。
  }

  return {
    fingerprint: visitorFingerprint,
    siteCode: 'cv',
    pagePath: pagePath.split(/[?#]/, 1)[0],
    pageTitle: Array.from(pageTitle || '').slice(0, 200).join('') || null,
    referer: cleanReferer,
    articleId: null,
  }
}
