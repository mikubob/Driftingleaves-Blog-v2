async (page) => {
  return await page.evaluate(async (root) => {
    const response = await fetch(root + '/analytics/online/tickets', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fingerprint: 'cross-api-browser-0001', siteCode: 'blog', pagePath: '/' }),
    })
    if (response.status !== 201) throw new Error('跨 Origin HTTP 签票失败')
    const result = await response.json()
    return await new Promise((resolve, reject) => {
      const socket = new WebSocket(root.replace('http:', 'ws:') + '/ws/online')
      const timer = setTimeout(() => { socket.close(); reject(new Error('跨来源连接超时')) }, 5000)
      socket.onopen = () => socket.send(JSON.stringify({ type: 'join', ticket: result.data.ticket }))
      socket.onmessage = (event) => {
        const message = JSON.parse(event.data)
        clearTimeout(timer)
        socket.close(1000)
        if (message.type !== 'online.count') reject(new Error('跨来源快照失败'))
        else resolve({ directApiOrigin: true, snapshot: message.data })
      }
      socket.onerror = () => { clearTimeout(timer); reject(new Error('跨来源 WebSocket 失败')) }
    })
  }, 'http://127.0.0.1:62821/api/v1')
}
