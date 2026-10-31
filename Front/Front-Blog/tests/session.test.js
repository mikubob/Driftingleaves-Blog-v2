import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { acceptSession, clearSession, onSessionChange, session } from '../src/session.js'

const originalFetch = globalThis.fetch
afterEach(() => {
  clearSession()
  globalThis.fetch = originalFetch
})

test('登录成功和有效退出各通知一次，重复清理匿名会话不会触发重连循环', async () => {
  clearSession()
  let changes = 0
  const unsubscribe = onSessionChange(() => changes++)
  try {
    clearSession()
    clearSession()
    assert.equal(changes, 0)
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 'OK', msg: '成功', errors: [],
      data: { permissionCodes: [] } }))
    await acceptSession({ tokenType: 'Bearer', accessToken: 'fictional-memory-session', user: { userId: '1' } })
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
