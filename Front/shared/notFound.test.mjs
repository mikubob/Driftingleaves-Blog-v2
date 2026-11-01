import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { reportNotFound } from './notFound.mjs'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

test('未知路由只发送一次无身份、无URL参数的当前端上报', async () => {
  const calls = []
  globalThis.fetch = async (...args) => {
    calls.push(args)
    return new Response(null, { status: 403 })
  }
  await reportNotFound('https://api.example.test/api/v1', 'blog')
  await reportNotFound('/api/v1', 'admin')
  assert.deepEqual(calls, [
    ['https://api.example.test/api/v1/blog/security/not-found', { method: 'POST', credentials: 'omit' }],
    ['/api/v1/admin/security/not-found', { method: 'POST', credentials: 'omit' }],
  ])
})

test('网络失败不重试也不向调用方抛出错误', async () => {
  let calls = 0
  globalThis.fetch = async () => {
    calls += 1
    throw new TypeError('模拟网络失败')
  }
  await reportNotFound('/api/v1', 'blog')
  assert.equal(calls, 1)
})
