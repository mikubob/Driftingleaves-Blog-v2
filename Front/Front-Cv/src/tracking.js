let fingerprint

/** 简历只生成在线统计标识，不附带采集 PV。 */
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
