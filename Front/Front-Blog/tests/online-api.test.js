import assert from 'node:assert/strict'
import { test, afterEach } from 'node:test'
import { getOnlineTicket } from '../src/api.js'
import { setRequestFailureHandler } from '../../shared/requestFailure.mjs'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch; setRequestFailureHandler() })

test('在线请求精确发送三个字段，响应票据在 API 边界校验', async () => {
  const body = { fingerprint: '1234567890abcdef', siteCode: 'blog', pagePath: '/' }
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/analytics/online/tickets')
    assert.equal(options.method, 'POST')
    assert.deepEqual(JSON.parse(options.body), body)
    return new Response(JSON.stringify({ code: 'OK', msg: '成功', errors: [],
      data: { ticket: 'a'.repeat(43), expiresIn: 30 } }), { status: 201 })
  }
  assert.equal((await getOnlineTicket(body)).ticket.length, 43)
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'OK', errors: [], data: {} }))
  await assert.rejects(getOnlineTicket(body), /票据/)
})

test('在线服务故障只中断人数，429 保留结构化等待秒数', async () => {
  let failures = 0
  setRequestFailureHandler(() => failures++)
  globalThis.fetch = async () => new Response('unavailable', { status: 503 })
  await assert.rejects(getOnlineTicket({}))
  assert.equal(failures, 0)
  globalThis.fetch = async () => new Response('', { status: 429, headers: { 'Retry-After': '82' } })
  await assert.rejects(getOnlineTicket({}), (error) => error.status === 429 && error.retryAfter === 82)
  assert.equal(failures, 0)
})
