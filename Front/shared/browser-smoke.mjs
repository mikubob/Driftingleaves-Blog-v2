// 通过 Playwright CLI 的 run-code --filename 执行；所有接口均使用虚构响应。
async (page) => {
  page.setDefaultTimeout(15000)
  await page.unroute('**/api/v1/**')
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  const results = []
  let failure = null
  let notFoundReports = 0
  await page.route('**/api/v1/**', async (route) => {
    if (route.request().url().endsWith('/security/not-found')) {
      notFoundReports += 1
    }
    if (failure === 'network') {
      return route.abort('failed')
    }
    if (failure) {
      return route.fulfill({
        status: failure,
        headers: { 'Retry-After': '17' },
        contentType: failure === 403 ? 'application/json' : 'text/html',
        body: failure === 403
          ? JSON.stringify({ code: 'ACCESS_BLOCKED', msg: '访问暂时受限', data: null, errors: [] })
          : '<html>模拟网关错误</html>',
      })
    }
    const url = new URL(route.request().url())
    const path = url.pathname.slice('/api/v1'.length)
    const pagination = {
      page: Number(url.searchParams.get('page') || '1'),
      pageSize: Number(url.searchParams.get('pageSize') || '6'),
      total: '0', totalPages: '0', records: [],
    }
    let data = []
    if (path === '/admin/me') {
      data = { userId: '1', username: 'test_admin', nickname: '测试管理员', status: 1 }
    } else if (path === '/admin/me/capabilities') {
      data = { roleCodes: ['admin'], permissionCodes: ['admin:access'], dataScopes: { 'admin:access': 'ALL' } }
    }
    if (path.endsWith('/auth/captcha')) {
      data = { captchaId: 'a'.repeat(43), imageBase64: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', expiresIn: 120 }
    }
    if (/^\/(home|blog|cv)\/profile$/.test(path)) {
      data = null
    } else if (/^\/(home|blog|cv)\/analytics\/page-views$/.test(path)) {
      data = { recorded: true }
    } else if (path === '/blog/statistics') {
      data = {
        articleCount: '0', categoryCount: '0', tagCount: '0',
        pageViewCount: '0', visitorCount: '0', todayPageViewCount: '0',
      }
    } else if (['/blog/articles', '/blog/music', '/blog/categories', '/blog/tags', '/cv/skills', '/cv/experiences'].includes(path)) {
      data = pagination
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'OK', msg: '', data, errors: [] }),
    })
  })

  for (const [name, port, success] of [
    ['Home', 5173, '#home'],
    ['Blog', 5174, '.home-page'],
    ['Cv', 5175, '#resume'],
    ['Admin', 5176, '.login-page'],
  ]) {
    await page.setViewportSize({ width: 390, height: 844 })
    for (const status of [500, 502, 503, 429, 403, 'network']) {
      failure = status
      await page.goto(`http://127.0.0.1:${port}/`)
      await page.getByRole('heading', { name: '404', exact: true }).waitFor()
      await page.waitForFunction(() => document.title === '404 - 页面不存在')
      const message = await page.locator('.error-desc').innerText()
      if (message !== '你访问的页面已经走丢了') {
        throw new Error(`${name} 错误页泄露接口错误：${message}`)
      }
      const dimensions = await page.locator('.error-page').evaluate((element) => ({
        height: element.getBoundingClientRect().height,
        viewport: innerHeight,
        overflow: document.documentElement.scrollWidth > innerWidth,
      }))
      if (dimensions.height < dimensions.viewport || dimensions.overflow) {
        throw new Error(`${name} 异常页布局不正确：${JSON.stringify(dimensions)}`)
      }
      failure = null
      await page.getByRole('link', { name: '返回首页', exact: true }).click()
      await page.locator(success).waitFor()
      if (await page.getByRole('heading', { name: '404', exact: true }).count()) {
        throw new Error(`${name} 返回首页后仍停留在异常页`)
      }
      results.push({ name, status, recovered: true })
    }
  }
  if (notFoundReports !== 0) {
    throw new Error('接口故障错误页重复上报了前端404')
  }
  for (const path of ['/unknown-one', '/unknown-two']) {
    await page.goto(`http://127.0.0.1:5174${path}`)
    await page.getByRole('heading', { name: '404', exact: true }).waitFor()
    await page.waitForLoadState('networkidle')
  }
  await page.goto('http://127.0.0.1:5176/unknown-anonymous')
  await page.locator('.login-page').waitFor()
  if (notFoundReports !== 2) {
    throw new Error('管理端未登录的导航应保留登录跳转，不当作404上报')
  }
  // 恢复虚构会话，身份和权限仍通过上面的 HTTP 夹具读取。
  await page.evaluate(() => localStorage.setItem('dl-admin-session', JSON.stringify({
    accessToken: 'test-not-found-browser-token', expiresAt: Date.now() + 60000,
  })))
  for (const path of ['/unknown-one', '/unknown-two']) {
    await page.goto(`http://127.0.0.1:5176${path}`)
    await page.getByRole('heading', { name: '404', exact: true }).waitFor()
    await page.waitForLoadState('networkidle')
  }
  if (notFoundReports !== 4) {
    throw new Error(`两个路由端的未知地址应各上报一次，实际${notFoundReports}次`)
  }
  await page.screenshot({ path: 'output/playwright/not-found-fixed.png', fullPage: true })
  await page.evaluate(() => localStorage.removeItem('dl-admin-session'))
  if (errors.length) {
    throw new Error(JSON.stringify(errors))
  }
  return { passed: true, results, notFoundReports, runtimeErrors: errors }
}
