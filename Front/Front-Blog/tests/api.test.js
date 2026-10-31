import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import {
  apiRoot, getPage, isId, isWebUrl, pageNumber, queryString, request,
  setAuthorization, validatePassword,
} from '../src/api.js'

const originalFetch = globalThis.fetch
let calls

function respond(data, status = 200, headers = {}, code = 'OK') {
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return new Response(JSON.stringify({ code, msg: '测试响应', data, errors: [] }), {
      status, headers: { 'Content-Type': 'application/json', ...headers },
    })
  }
}

beforeEach(() => {
  calls = []
  setAuthorization('')
})
afterEach(() => {
  globalThis.fetch = originalFetch
  setAuthorization('')
})

test('字符串 BIGINT、计数和分页不丢精度', async () => {
  const id = '9223372036854775807'
  assert.equal(isId(id), true)
  assert.equal(isId(9007199254740992), false)
  assert.equal(isId('9223372036854775808'), false)
  respond({ page: 1, pageSize: 10, total: id, totalPages: id, records: [{ userId: id }] })
  const data = await getPage('/test', { page: 1, authorId: id })
  assert.equal(data.records[0].userId, id)
  assert.equal(data.total, id)
  assert.equal(calls[0].url, `${apiRoot}/test?page=1&authorId=${id}`)
})

test('拒绝数字 ID、错误页结构及不完整文章响应', async () => {
  respond({ userId: 12 })
  await assert.rejects(request('/test'), /ID/)
  respond({ page: 1, pageSize: 10, total: 1, totalPages: '1', records: [] })
  await assert.rejects(getPage('/test'), /分页/)
  respond({ page: 1, pageSize: 10, total: '1', totalPages: '1', records: [{ articleId: '1' }] })
  await assert.rejects(getPage('/blog/articles'), /数据格式/)
})

test('凭据错误保留会话，身份失效才清除；Token 只经 Authorization 发送', async () => {
  let expired = false
  setAuthorization('fictional-memory-token', () => { expired = true })
  respond(null, 401, {}, 'AUTH_FAILED')
  await assert.rejects(request('/auth/login', { method: 'POST', body: { password: '  密码  ' } }))
  assert.equal(expired, false)
  assert.equal(calls[0].options.headers.get('Authorization'), 'Bearer fictional-memory-token')
  assert.equal(JSON.parse(calls[0].options.body).password, '  密码  ')
  respond(null, 401, {}, 'UNAUTHENTICATED')
  await assert.rejects(request('/me'))
  assert.equal(expired, true)
  respond(null)
  await request('/test')
  assert.equal(calls.at(-1).options.headers.has('Authorization'), false)
})

test('版本头原样传递；冲突与限流不重放写请求', async () => {
  respond(null, 412)
  await assert.rejects(request('/test', { method: 'PATCH', revision: '"quoted-version"', body: { intro: null } }), /重新加载/)
  assert.equal(calls.length, 1)
  assert.equal(calls[0].options.headers.get('If-Revision'), '"quoted-version"')
  assert.deepEqual(JSON.parse(calls[0].options.body), { intro: null })
  await assert.rejects(request('/test', { revision: 'unquoted' }), /有效编辑版本/)
  assert.equal(calls.length, 1)
  respond(null, 429, { 'Retry-After': '13' }, 'RATE_LIMITED')
  await assert.rejects(request('/test', { method: 'POST', body: {} }), /13 秒/)
  assert.equal(calls.length, 2)
})

test('公开附件封面支持规范路径，拒绝后台文件和危险外链', async () => {
  const path = '/api/v1/blog/articles/9007199254740993/files/2/content'
  respond({ coverUrl: path })
  assert.equal((await request('/test')).data.coverUrl, `${apiRoot}/blog/articles/9007199254740993/files/2/content`)
  for (const coverUrl of ['javascript:alert(1)', '//example.test/a', '/api/v1/admin/files/2/content']) {
    respond({ coverUrl })
    await assert.rejects(request('/test'), /链接/)
  }
  assert.equal(isWebUrl('https://example.test/image.webp'), true)
  assert.equal(isWebUrl('https://user:secret@example.test/'), false)
  assert.equal(isWebUrl(null), false)
})

test('OAuth 请求启用 Cookie，并保留服务端授权地址', async () => {
  respond({ authorizationUrl: 'https://example.test/authorize?state=fictional', expiresIn: 300 })
  await request('/auth/oauth/EXAMPLE/authorize', {
    method: 'POST', credentials: 'include', body: { operation: 'LOGIN' },
  })
  assert.equal(calls[0].options.credentials, 'include')
  assert.deepEqual(JSON.parse(calls[0].options.body), { operation: 'LOGIN' })
})

test('密码按字符及 UTF-8 字节校验，不 trim；筛选仅序列化已提供值', () => {
  assert.equal(validatePassword(' 123456 '), true)
  assert.equal(validatePassword('叶'.repeat(24)), true)
  assert.equal(validatePassword('叶'.repeat(25)), false)
  assert.equal(validatePassword('a'.repeat(65)), false)
  assert.equal(pageNumber(['2']), 1)
  assert.equal(pageNumber('-2'), 1)
  assert.equal(pageNumber('999999'), 100000)
  assert.equal(queryString({ page: 2, authorId: '9007199254740993', keyword: '', month: null }), '?page=2&authorId=9007199254740993')
})

test('网络错误、非 JSON 和 AbortError 保留失败语义', async () => {
  globalThis.fetch = async () => { throw new Error('offline') }
  await assert.rejects(request('/test'), /连接服务器/)
  globalThis.fetch = async () => new Response('bad gateway', { status: 502 })
  await assert.rejects(request('/test'), /HTTP 502/)
  const abort = new DOMException('aborted', 'AbortError')
  globalThis.fetch = async () => { throw abort }
  await assert.rejects(request('/test'), (error) => error === abort)
})
