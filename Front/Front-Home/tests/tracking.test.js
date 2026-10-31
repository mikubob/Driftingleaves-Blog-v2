import test from 'node:test'
import assert from 'node:assert/strict'
import { webcrypto } from 'node:crypto'
import { createPageView, getFingerprint } from '../src/tracking.js'

test('统计随机标识可复用，非法存储值会重新生成', () => {
  const values = new Map([['drifting-leaves-visitor', 'invalid']])
  const browser = {
    crypto: webcrypto,
    localStorage: {
      getItem: (key) => values.get(key),
      setItem: (key, value) => values.set(key, value),
    },
  }
  const fingerprint = getFingerprint(browser)
  assert.match(fingerprint, /^[a-f0-9]{32}$/)
  assert.equal(getFingerprint(browser), fingerprint)
})

test('禁用存储，包括读取 localStorage 本身抛错，仍能生成标识', () => {
  const browser = {
    crypto: webcrypto,
    get localStorage() { throw new Error('SecurityError') },
  }
  assert.match(getFingerprint(browser), /^[a-f0-9]{32}$/)
})

test('页面及来源移除 query、fragment 和凭据，标题按 Unicode 字符截断', () => {
  const data = createPageView('0123456789abcdef', '/?ticket=secret#token', '叶'.repeat(201),
    'https://user:pass@example.test/from?q=secret#fragment')
  assert.deepEqual(data, {
    fingerprint: '0123456789abcdef',
    siteCode: 'home',
    pagePath: '/',
    pageTitle: '叶'.repeat(200),
    referer: 'https://example.test/from',
    articleId: null,
  })
})

test('缺失或非网页来源记为 null', () => {
  for (const referer of ['', 'about:blank', 'javascript:alert(1)', 'invalid']) {
    const data = createPageView('0123456789abcdef', '/', '', referer)
    assert.equal(data.referer, null)
    assert.equal(data.pageTitle, null)
  }
})
