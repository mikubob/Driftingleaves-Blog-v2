const fingerprints = new WeakMap()

/** 生成非身份用途的随机统计标识；浏览器禁止存储时仅在当前页面使用。 */
export function getFingerprint(browser) {
  if (fingerprints.has(browser)) {
    return fingerprints.get(browser)
  }
  const key = 'drifting-leaves-visitor'
  try {
    const saved = browser.localStorage.getItem(key)
    if (/^[A-Za-z0-9-]{16,150}$/.test(saved || '')) {
      fingerprints.set(browser, saved)
      return saved
    }
  } catch {
    // 隐私模式可能禁用持久存储，继续使用当前页面的随机标识。
  }

  const bytes = browser.crypto.getRandomValues(new Uint8Array(16))
  const fingerprint = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  try {
    browser.localStorage.setItem(key, fingerprint)
  } catch {
    // 存储失败不妨碍公开阅读或当前浏览的采集。
  }
  fingerprints.set(browser, fingerprint)
  return fingerprint
}

/** 采集只包含站内路径与脱敏来源，不发送查询参数、fragment 或用户身份。 */
export function createPageView(fingerprint, pagePath, pageTitle, referer) {
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
    fingerprint,
    siteCode: 'home',
    pagePath: pagePath.split(/[?#]/, 1)[0],
    pageTitle: Array.from(pageTitle).slice(0, 200).join('') || null,
    referer: cleanReferer,
    articleId: null,
  }
}
