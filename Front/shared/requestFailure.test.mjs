import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { setRequestFailureHandler } from './requestFailure.mjs'
import * as blog from '../Front-Blog/src/api.js'
import * as admin from '../Front-Admin/src/api.js'
import * as home from '../Front-Home/src/api.js'
import * as cv from '../Front-Cv/src/api.js'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
  setRequestFailureHandler()
})

for (const [name, read] of [
  ['Blog', () => blog.request('/blog/profile')],
  ['Admin', () => admin.request('/admin/me')],
  ['Home', () => home.getProfile()],
  ['Cv', () => cv.getProfile()],
]) {
  test(`${name}：网络、服务异常和非 JSON 限流触发异常页且请求只执行一次`, async () => {
    const failures = []
    setRequestFailureHandler((error) => failures.push(error))
    for (const response of [
      () => { throw new TypeError('fetch failed') },
      () => new Response('<html>Bad Gateway</html>', { status: 502 }),
      () => new Response(JSON.stringify({ code: 'SERVICE_UNAVAILABLE', msg: '服务暂不可用' }), { status: 503 }),
      () => new Response('Too Many Requests', { status: 429, headers: { 'Retry-After': '17' } }),
      () => new Response('invalid JSON', { status: 200 }),
      () => new Response(JSON.stringify({ code: 'ACCESS_BLOCKED', msg: '访问暂时受限' }), { status: 403 }),
    ]) {
      let calls = 0
      globalThis.fetch = async () => {
        calls += 1
        return response()
      }
      const previous = failures.length
      await assert.rejects(read(), (error) => error === failures.at(-1))
      assert.equal(failures.length, previous + 1)
      assert.equal(calls, 1)
    }
    assert.match(failures[2].message, /服务暂不可用/)
    assert.match(failures[3].message, /17 秒/)
  })

  test(`${name}：取消、校验、鉴权和版本冲突不触发异常页`, async () => {
    const failures = []
    setRequestFailureHandler((error) => failures.push(error))
    globalThis.fetch = async () => { throw new DOMException('取消读取', 'AbortError') }
    await assert.rejects(read(), { name: 'AbortError' })
    globalThis.fetch = async () => new Response(new ReadableStream({
      start(controller) {
        controller.error(new DOMException('取消响应体读取', 'AbortError'))
      },
    }))
    await assert.rejects(read(), { name: 'AbortError' })
    for (const status of [400, 401, 403, 404, 409, 412, 428]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ code: 'BUSINESS_ERROR', msg: '业务错误' }), { status })
      await assert.rejects(read())
    }
    assert.deepEqual(failures, [])
  })
}

test('统计采集失败保持静默且不自动重放写请求', async () => {
  const failures = []
  setRequestFailureHandler((error) => failures.push(error))
  for (const status of [429, 503]) {
    let calls = 0
    globalThis.fetch = async () => {
      calls += 1
      return new Response('gateway error', { status })
    }
    await assert.rejects(home.recordPageView({}))
    await assert.rejects(blog.request('/blog/analytics/page-views', { method: 'POST', body: {} }))
    assert.equal(calls, 2)
  }
  assert.deepEqual(failures, [])
})
