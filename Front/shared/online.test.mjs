import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createOnlineConnection, onlineSocketUrl, validateOnlineTicket } from './online.mjs'

const ticket = { ticket: 'a'.repeat(43), expiresIn: 30 }
const data = { onlineCount: '3', userCount: '1', guestCount: '2', serverTime: '2026-11-01T00:00:00.000Z' }
const flush = async () => { await Promise.resolve(); await Promise.resolve() }

function fixture(requestTicket = async () => ticket) {
  const timers = new Map()
  const listeners = new Map()
  const sockets = []
  const states = []
  let id = 0
  const browser = {
    navigator: { onLine: true }, location: { href: 'https://blog.example.test/account' },
    setTimeout(fn, delay) { timers.set(++id, { fn, delay }); return id },
    clearTimeout(id) { timers.delete(id) },
    setInterval(fn, delay) { timers.set(++id, { fn, delay, interval: true }); return id },
    clearInterval(id) { timers.delete(id) },
    addEventListener(name, fn) { listeners.set(name, fn) },
    removeEventListener(name) { listeners.delete(name) },
    WebSocket: class {
      constructor(url) { this.url = url; this.sent = []; sockets.push(this) }
      send(text) { this.sent.push(JSON.parse(text)) }
      close(code) { this.closed = code }
      message(type, extra = {}) { this.onmessage?.({ data: JSON.stringify({ type, code: 'OK', msg: '成功', errors: [], data: null, ...extra }) }) }
    },
  }
  let invalidations = 0
  const connection = createOnlineConnection({ apiRoot: 'https://api.example.test/api/v1', requestTicket,
    payload: () => ({ siteCode: 'blog', fingerprint: '1234567890abcdef', pagePath: '/' }), browser,
    onState: (value) => states.push(value), onSessionInvalid: () => { invalidations++ },
  })
  function fire(delay) {
    const entry = [...timers].find(([, task]) => task.delay >= delay && task.delay < delay + 300)
    assert.ok(entry, `缺少 ${delay}ms 计时器`)
    if (!entry[1].interval) timers.delete(entry[0])
    entry[1].fn()
  }
  return { connection, browser, sockets, states, timers, listeners, fire, invalidations: () => invalidations }
}

test('票据、地址和字符串快照满足三站协议', async () => {
  assert.equal(onlineSocketUrl('/api/v1/home', { href: 'https://home.example.test/' }), 'wss://home.example.test/api/v1/home/ws/online')
  assert.equal(onlineSocketUrl('http://api.example.test/api/v1/blog', { href: 'https://blog.example.test/' }), 'ws://api.example.test/api/v1/blog/ws/online')
  assert.throws(() => validateOnlineTicket({ ...ticket, expiresIn: 31 }))
  assert.throws(() => validateOnlineTicket({ ticket: 'invalid', expiresIn: 30 }))
  const f = fixture()
  f.connection.start()
  await flush()
  const socket = f.sockets[0]
  assert.equal(socket.url, 'wss://api.example.test/api/v1/blog/ws/online')
  socket.onopen()
  assert.deepEqual(socket.sent, [{ type: 'join', ticket: ticket.ticket }])
  socket.message('online.count', { data })
  assert.equal(f.states.at(-1).status, 'live')
  assert.deepEqual(f.states.at(-1).snapshot, data)
  socket.message('online.count', { data: { ...data, userCount: 1 } })
  assert.equal(f.states.at(-1).status, 'interrupted')
  assert.equal(f.timers.size, 0)
  f.connection.stop()
})

test('每次重连重签票据，心跳等待 pong，卸载清理所有计时器与监听', async () => {
  let requests = 0
  const f = fixture(async () => { requests++; return ticket })
  f.connection.start()
  await flush()
  f.sockets[0].message('online.count', { data })
  f.fire(25000)
  assert.deepEqual(f.sockets[0].sent.at(-1), { type: 'ping' })
  f.sockets[0].message('pong')
  assert.equal([...f.timers.values()].some((item) => item.delay === 10000), false)
  f.fire(25000)
  f.fire(10000)
  assert.equal(f.states.at(-1).status, 'interrupted')
  assert.equal(f.sockets[0].closed, 1000)
  f.fire(1000)
  await flush()
  assert.equal(requests, 2)
  f.connection.stop()
  assert.equal(f.timers.size, 0)
  assert.equal(f.listeners.size, 0)
})

test('身份切换丢弃旧票据及回调，离线暂停，往返缓存恢复', async () => {
  const pending = []
  const f = fixture(() => new Promise((resolve) => pending.push(resolve)))
  f.connection.start()
  f.connection.restart()
  pending[0](ticket)
  await flush()
  assert.equal(f.sockets.length, 0)
  pending[1](ticket)
  await flush()
  const stale = f.sockets[0].onmessage
  f.connection.restart()
  stale({ data: JSON.stringify({ type: 'online.count', code: 'OK', msg: '', errors: [], data }) })
  assert.equal(f.states.at(-1).status, 'connecting')
  f.browser.navigator.onLine = false
  f.listeners.get('offline')()
  assert.equal(f.timers.size, 0)
  f.browser.navigator.onLine = true
  f.listeners.get('online')()
  f.listeners.get('pagehide')()
  assert.equal(f.timers.size, 0)
  f.listeners.get('pageshow')({ persisted: true })
  assert.equal(f.states.at(-1).status, 'connecting')
  f.connection.stop()
})

test('限流尊重 Retry-After，协议错误停止重试，失效身份通知调用方', async () => {
  const f = fixture(async () => { throw { status: 429, retryAfter: 87 } })
  f.connection.start()
  await flush()
  f.fire(87000)
  f.connection.stop()
  const g = fixture()
  g.connection.start()
  await flush()
  g.sockets[0].message('error', { code: 'UNAUTHENTICATED' })
  assert.equal(g.invalidations(), 1)
  g.fire(1000)
  await flush()
  g.sockets[1].message('error', { code: 'VALIDATION_ERROR' })
  assert.equal(g.timers.size, 0)
  assert.equal(g.states.at(-1).status, 'interrupted')
  g.connection.stop()
})
