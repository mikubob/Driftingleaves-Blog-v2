// 由 Playwright CLI run-code --filename 执行；后端必须是 OnlineIntegrationTest 的临时隔离实例。
async (page) => {
  const context = page.context()
  for (const other of context.pages()) {
    if (other !== page) {
      await other.close()
    }
  }
  const pages = [page, await context.newPage(), await context.newPage()]
  const sockets = []
  const sent = []
  const errors = []
  for (const current of pages) {
    current.on('pageerror', (error) => errors.push(error.message))
    current.on('websocket', (socket) => {
      if (!/\/api\/v1\/(home|blog|cv)\/ws\/online$/.test(socket.url())) {
        return
      }
      sockets.push(socket.url())
      socket.on('framesent', (event) => sent.push(JSON.parse(event.payload)))
    })
  }
  async function counts(total, users, guests) {
    await page.waitForFunction((expected) => {
      const values = [...document.querySelectorAll('.online-stats .sg-val')].map((element) => element.textContent)
      return JSON.stringify(values) === JSON.stringify(expected)
    }, [`${total} 人`, `${users} 人`, `${guests} 人`])
  }
  const status = page.locator('.stats-card [role="status"]')
  await page.goto('http://127.0.0.1:25174/')
  await counts('1', '0', '1')
  await pages[1].goto('http://127.0.0.1:25173/')
  await counts('2', '0', '2')
  await pages[2].goto('http://127.0.0.1:25175/')
  await counts('3', '0', '3')
  for (const current of pages) {
    if (await current.locator('footer').getByText(/当前在线|在线人数|三站合计/).count()) {
      throw new Error('页脚仍展示在线人数')
    }
  }
  const duplicate = await context.newPage()
  duplicate.on('websocket', (socket) => {
    if (!/\/api\/v1\/(home|blog|cv)\/ws\/online$/.test(socket.url())) {
      return
    }
    sockets.push(socket.url())
    socket.on('framesent', (event) => sent.push(JSON.parse(event.payload)))
  })
  // 只替换两张图片，答案由隔离后端窗口预置，注册和登录仍真实消费 Redis 验证码。
  const captchaIds = ['r'.repeat(43), 'l'.repeat(43)]
  await duplicate.route('**/api/v1/blog/auth/captcha', async (route) => {
    const captchaId = captchaIds.shift()
    if (!captchaId) {
      return route.continue()
    }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({
      code: 'OK', msg: '成功', errors: [], data: { captchaId, expiresIn: 120,
        imageBase64: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=' },
    }) })
  })
  await duplicate.goto('http://127.0.0.1:25174/login')
  await counts('3', '0', '3')
  await duplicate.getByRole('button', { name: '注册账号', exact: true }).click()
  await duplicate.getByLabel('用户名', { exact: true }).fill(`online_browser_${Date.now()}`)
  await duplicate.getByLabel('昵称', { exact: true }).fill('在线验证')
  await duplicate.getByLabel('密码', { exact: true }).fill('Online-example-password-2026')
  await duplicate.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await duplicate.getByRole('button', { name: '创建账号 →', exact: true }).click()
  await duplicate.getByText('注册成功，请使用刚才的账号密码登录。').waitFor()
  await duplicate.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await duplicate.getByRole('button', { name: '登录 →', exact: true }).click()
  await counts('3', '1', '2')
  await duplicate.getByRole('button', { name: '退出登录', exact: true }).click()
  await counts('3', '0', '3')
  await duplicate.close()
  await pages[2].close()
  await counts('2', '0', '2')
  // 浏览器真实计时器至少完成一轮应用心跳；不记录票据原文。
  await page.waitForTimeout(27000)
  if (!sent.some((message) => message.type === 'ping')) {
    throw new Error('未观察到真实心跳')
  }
  if (sent.some((message) => !['join', 'ping'].includes(message.type)
    || Object.keys(message).some((key) => !['type', 'ticket'].includes(key)))) {
    throw new Error('WebSocket 发送了协议外身份字段')
  }
  if (sockets.some((url) => !/\/api\/v1\/(home|blog|cv)\/ws\/online$/.test(url))) {
    throw new Error('WebSocket 地址包含额外参数')
  }
  let newTickets = 0
  let newSockets = 0
  page.on('request', (request) => {
    if (request.url().endsWith('/analytics/online/tickets')) {
      newTickets++
    }
  })
  page.on('websocket', (socket) => {
    if (/\/api\/v1\/(home|blog|cv)\/ws\/online$/.test(socket.url())) {
      newSockets++
    }
  })
  await context.setOffline(true)
  await status.filter({ hasText: '在线人数连接中断' }).waitFor()
  await context.setOffline(false)
  await counts('2', '0', '2')
  if (newTickets !== 1 || newSockets !== 1) {
    throw new Error('恢复时没有重新申请唯一票据')
  }
  await page.locator('a[href="/archive"]').first().click()
  await page.waitForURL('**/archive')
  await counts('2', '0', '2')
  if (newSockets !== 1) {
    throw new Error('切换路由重复建立在线连接')
  }
  if (errors.length) {
    throw new Error(`浏览器运行错误：${errors.join(';')}`)
  }
  await page.screenshot({ path: 'output/playwright/online-blog-statistics.png', fullPage: true, animations: 'disabled' })
  await pages[1].close()
  await counts('1', '0', '1')
  return { display: 'Blog站点统计卡片', footerDisplays: false, homeCvParticipate: true,
    sameBrowserDeduplication: true, passwordLoginLogout: true, heartbeat: true,
    offlineReconnect: true, routeReusesConnection: true, websocketCredentials: 'ticket-only', pageErrors: errors.length }
}
