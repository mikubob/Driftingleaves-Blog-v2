async (page) => {
  const status = () => page.locator('footer [role="status"]')
  let tickets = 0
  let sockets = 0
  page.on('request', (request) => {
    if (request.url().endsWith('/analytics/online/tickets')) tickets++
  })
  page.on('websocket', (socket) => {
    if (socket.url().includes('/api/v1/ws/online')) sockets++
  })
  await page.context().setOffline(true)
  await status().filter({ hasText: '在线人数连接中断' }).waitFor()
  await page.context().setOffline(false)
  await status().filter({ hasText: '当前在线 1 人' }).waitFor()
  if (tickets !== 1 || sockets !== 1) throw new Error('恢复连接没有重新签发唯一票据')
  await page.locator('a[href="/archive"]').first().click()
  await page.waitForURL('**/archive')
  await status().filter({ hasText: '当前在线 1 人' }).waitFor()
  if (sockets !== 1) throw new Error('路由切换重复创建连接')
  const cv = await page.context().newPage()
  await cv.setViewportSize({ width: 390, height: 844 })
  await cv.goto('http://127.0.0.1:25175/')
  await cv.locator('footer [role="status"]').filter({ hasText: '当前在线 2 人' }).waitFor()
  await cv.screenshot({ path: 'output/playwright/online-cv-mobile.png', fullPage: true })
  const overflow = await cv.evaluate(() => document.documentElement.scrollWidth > innerWidth)
  if (overflow) throw new Error('简历页脚在手机宽度横向溢出')
  await cv.close()
  await page.screenshot({ path: 'output/playwright/online-blog-archive.png', fullPage: true })
  return { offlineInterrupted: true, newTicketOnReconnect: true, routeReusesConnection: true, cvMobileOverflow: false }
}
