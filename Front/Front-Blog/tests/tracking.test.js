import assert from 'node:assert/strict'
import { test } from 'node:test'
import { trackingPayload } from '../src/tracking.js'
import { articlePath } from '../src/format.js'

test('采集只保留规范路径、去敏来源与字符串文章 ID', () => {
  const storage = new Map()
  globalThis.localStorage = {
    getItem: (key) => storage.get(key),
    setItem: (key, value) => storage.set(key, value),
  }
  globalThis.document = { referrer: 'https://example.test/source?ticket=fictional#secret' }
  const path = articlePath({ slug: 'first-leaf' })
  const data = trackingPayload(`${path}?keyword=private#heading`, '第一片落叶', '9007199254740993')
  assert.equal(path, '/articles/first-leaf')
  assert.equal(data.pagePath, '/articles/first-leaf')
  assert.equal(data.referer, 'https://example.test/source')
  assert.equal(data.articleId, '9007199254740993')
  assert.equal(data.siteCode, 'blog')
  assert.match(data.fingerprint, /^[A-Za-z0-9-]{16,150}$/)
  assert.deepEqual([...storage.keys()], ['dl-blog-visitor'])
})
