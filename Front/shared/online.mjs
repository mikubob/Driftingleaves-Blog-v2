/** 票据只在内存中使用一次，不能把 HTTP accessToken 交给 WebSocket。 */
export function validateOnlineTicket(data) {
  if (!data || typeof data.ticket !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(data.ticket)
    || data.expiresIn !== 30) {
    throw Object.assign(new Error('在线票据响应格式不正确。'), { code: 'VALIDATION_ERROR' })
  }
  return data
}

/** 地址跟随 API 部署来源，保留 /api/v1 前缀且不包含身份参数。 */
export function onlineSocketUrl(apiRoot, location) {
  const url = new URL(`${apiRoot}/ws/online`, location.href)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.href
}

/** 只有完整快照进入页面状态，计数始终保留十进制字符串。 */
function snapshot(data) {
  const count = (value) => typeof value === 'string' && /^(0|[1-9]\d*)$/.test(value)
  if (!data || !count(data.onlineCount) || !count(data.userCount) || !count(data.guestCount)
    || BigInt(data.onlineCount) !== BigInt(data.userCount) + BigInt(data.guestCount)
    || typeof data.serverTime !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(data.serverTime)
    || Number.isNaN(Date.parse(data.serverTime))) {
    throw new Error('在线快照格式不正确。')
  }
  return data
}

/** 每个挂载布局只创建一个实例；身份切换、离线和卸载都会隔离旧异步回调。 */
export function createOnlineConnection({ apiRoot, requestTicket, payload, onState = () => {},
  onSessionInvalid = () => {}, browser = window }) {
  let active = false
  let suspended = false
  let generation = 0
  let socket
  let controller
  let reconnectTimer
  let heartbeatTimer
  let pongTimer
  let joinTimer
  let attempt = 0
  let joined = false

  function state(status, data = null) {
    onState({ status, snapshot: data })
  }

  function disconnect() {
    generation += 1
    browser.clearTimeout(reconnectTimer)
    browser.clearInterval(heartbeatTimer)
    browser.clearTimeout(pongTimer)
    browser.clearTimeout(joinTimer)
    reconnectTimer = heartbeatTimer = pongTimer = joinTimer = undefined
    controller?.abort()
    controller = undefined
    if (socket) {
      socket.onopen = socket.onmessage = socket.onclose = socket.onerror = null
      socket.close(1000)
      socket = undefined
    }
    joined = false
  }

  function available() {
    return active && !suspended && browser.navigator.onLine !== false
  }

  function retry(minimum = 0) {
    disconnect()
    state('interrupted')
    if (!available()) {
      return
    }
    const delays = [1000, 2000, 5000, 10000, 30000]
    const delay = Math.max(minimum, Math.min(30000, delays[Math.min(attempt++, 4)] + Math.random() * 250))
    reconnectTimer = browser.setTimeout(connect, delay)
  }

  function protocolFailure() {
    disconnect()
    state('interrupted')
  }

  async function connect() {
    if (!available()) {
      return
    }
    disconnect()
    const current = generation
    const isCurrent = () => active && current === generation
    controller = new AbortController()
    state('connecting')
    joinTimer = browser.setTimeout(() => {
      if (isCurrent()) {
        retry()
      }
    }, 10000)
    try {
      const body = payload()
      const data = await requestTicket(body, controller.signal)
      if (!isCurrent()) {
        return
      }
      let ticket = data.ticket
      socket = new browser.WebSocket(onlineSocketUrl(`${apiRoot}/${body.siteCode}`, browser.location))
      socket.onopen = () => {
        if (isCurrent()) {
          socket.send(JSON.stringify({ type: 'join', ticket }))
          ticket = undefined
        }
      }
      socket.onmessage = (event) => {
        if (!isCurrent()) {
          return
        }
        try {
          const message = JSON.parse(event.data)
          if (typeof message.msg !== 'string' || !Array.isArray(message.errors)
            || !Object.hasOwn(message, 'data')) {
            throw new Error('在线消息格式不正确。')
          }
          if (message.type === 'error') {
            if (['UNAUTHENTICATED', 'ACCOUNT_DISABLED'].includes(message.code)) {
              onSessionInvalid()
              if (isCurrent()) {
                retry()
              }
            } else if (message.code === 'VALIDATION_ERROR') {
              protocolFailure()
            } else {
              retry(message.code === 'RATE_LIMITED' ? 60000 : 0)
            }
            return
          }
          if (message.code !== 'OK') {
            throw new Error('在线消息状态不正确。')
          }
          if (message.type === 'online.count') {
            const data = snapshot(message.data)
            state('live', data)
            attempt = 0
            if (!joined) {
              joined = true
              browser.clearTimeout(joinTimer)
              joinTimer = undefined
              heartbeatTimer = browser.setInterval(() => {
                if (!isCurrent() || pongTimer !== undefined) {
                  return
                }
                try {
                  socket.send(JSON.stringify({ type: 'ping' }))
                  pongTimer = browser.setTimeout(() => {
                    if (isCurrent()) {
                      retry()
                    }
                  }, 10000)
                } catch {
                  retry()
                }
              }, 25000)
            }
          } else if (message.type === 'pong' && joined && pongTimer !== undefined && message.data === null) {
            browser.clearTimeout(pongTimer)
            pongTimer = undefined
          } else {
            throw new Error('未知在线消息。')
          }
        } catch {
          protocolFailure()
        }
      }
      socket.onerror = () => {
        if (isCurrent()) {
          retry()
        }
      }
      socket.onclose = (event) => {
        if (!isCurrent()) {
          return
        }
        if ([1008, 1009].includes(event.code)) {
          protocolFailure()
        } else {
          retry()
        }
      }
    } catch (error) {
      if (!isCurrent()) {
        return
      }
      if (['UNAUTHENTICATED', 'ACCOUNT_DISABLED'].includes(error.code)) {
        onSessionInvalid()
        if (isCurrent()) {
          retry()
        }
      } else if (error.code === 'VALIDATION_ERROR' || [400, 403, 404, 415].includes(error.status)) {
        protocolFailure()
      } else {
        retry(error.status === 429 ? (error.retryAfter || 60) * 1000 : 0)
      }
    }
  }

  function restart() {
    attempt = 0
    disconnect()
    if (available()) {
      connect()
    }
  }
  function pause() {
    disconnect()
    state('interrupted')
  }
  function hide() {
    suspended = true
    pause()
  }
  function show() {
    suspended = false
    restart()
  }
  function start() {
    if (active) {
      return
    }
    active = true
    browser.addEventListener('offline', pause)
    browser.addEventListener('online', restart)
    browser.addEventListener('pagehide', hide)
    browser.addEventListener('pageshow', show)
    if (available()) {
      connect()
    } else {
      state('interrupted')
    }
  }
  function stop() {
    active = false
    disconnect()
    browser.removeEventListener('offline', pause)
    browser.removeEventListener('online', restart)
    browser.removeEventListener('pagehide', hide)
    browser.removeEventListener('pageshow', show)
  }
  return { start, restart, stop }
}
