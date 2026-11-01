import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { acceptSession, clearSession, onSessionChange, restoreSession, session } from '../src/session.js'

const originalFetch = globalThis.fetch
const originalStorage = globalThis.localStorage
const user = { userId: '1', username: 'test_leaf', nickname: '后端资料', email: null,
  avatar: null, intro: null, hasPassword: true }
beforeEach(() => {
  const entries = new Map()
  globalThis.localStorage = {
    getItem: (key) => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, value),
    removeItem: (key) => entries.delete(key),
  }
})
afterEach(() => {
  clearSession()
  globalThis.fetch = originalFetch
  globalThis.localStorage = originalStorage
})

test('登录成功和有效退出各通知一次，重复清理匿名会话不会触发重连循环', async () => {
  clearSession()
  let changes = 0
  const unsubscribe = onSessionChange(() => changes++)
  try {
    clearSession()
    clearSession()
    assert.equal(changes, 0)
    globalThis.fetch = async (url) => new Response(JSON.stringify({ code: 'OK', msg: '成功', errors: [],
      data: url.endsWith('/capabilities') ? { permissionCodes: [] } : user }))
    await acceptSession({ tokenType: 'Bearer', accessToken: 'fictional-memory-session',
      expiresIn: 259200, user: { userId: '1' } })
    assert.equal(changes, 1)
    assert.equal(session.user.userId, '1')
    clearSession()
    assert.equal(changes, 2)
    assert.equal(session.user, null)
    clearSession()
    assert.equal(changes, 2)
  } finally {
    unsubscribe()
  }
})

test('博客登录保存三天凭据，刷新重新核验资料和权限，退出删除凭据', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(options.headers.get('Authorization'), 'Bearer blog-token')
    const data = url.endsWith('/capabilities')
      ? { permissionCodes: ['blog:article:like'] } : user
    return new Response(JSON.stringify({ code: 'OK', data, errors: [] }))
  }
  const started = Date.now()
  await acceptSession({ tokenType: 'Bearer', accessToken: 'blog-token', expiresIn: 259200, user: { userId: '1' } })
  const saved = localStorage.getItem('dl-blog-session')
  const stored = JSON.parse(saved)
  assert.ok(stored.expiresAt >= started + 259200000)
  assert.ok(stored.expiresAt <= Date.now() + 259200000)
  assert.deepEqual(Object.keys(stored).sort(), ['accessToken', 'expiresAt'])
  localStorage.setItem('dl-admin-session', 'independent-admin-session')

  const refreshed = await import('../src/session.js?refresh-test')
  assert.equal(refreshed.session.user, null)
  await refreshed.restoreSession()
  assert.equal(refreshed.session.user.nickname, '后端资料')
  assert.deepEqual(refreshed.session.permissions, ['blog:article:like'])
  assert.equal(localStorage.getItem('dl-blog-session'), saved)
  await refreshed.logout()
  assert.equal(localStorage.getItem('dl-blog-session'), null)
  assert.equal(localStorage.getItem('dl-admin-session'), 'independent-admin-session')
})

test('过期或损坏凭据不发送请求，后端已撤销会话时清除持久凭据', async () => {
  let requests = 0
  globalThis.fetch = async () => {
    requests += 1
    throw new Error('过期凭据不应请求后端')
  }
  for (const saved of ['invalid-json', JSON.stringify({ accessToken: 'expired', expiresAt: Date.now() })]) {
    localStorage.setItem('dl-blog-session', saved)
    await restoreSession()
    assert.equal(localStorage.getItem('dl-blog-session'), null)
    assert.equal(session.user, null)
  }
  assert.equal(requests, 0)
  localStorage.setItem('dl-blog-session', JSON.stringify({ accessToken: 'revoked', expiresAt: Date.now() + 60000 }))
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'UNAUTHENTICATED', data: null, errors: [] }), { status: 401 })
  await restoreSession()
  assert.equal(session.user, null)
  assert.equal(localStorage.getItem('dl-blog-session'), null)
})
