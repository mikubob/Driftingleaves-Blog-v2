// 在 HTTP 边界使用虚构数据，验证表单布局和日志详情，不连接业务服务。
async function settingsLogsSmoke(page) {
  const failures = []
  page.on('pageerror', (error) => failures.push(error.message))
  const id = '9007199254740993'
  const time = '2026-11-01T01:00:00.000Z'
  const definitions = [
    ['主页资料', '/home/profile', 'home/profile/index', 'home:profile:query'],
    ['站点配置', '/system/config', 'system/config/index', 'blog:config:query'],
    ['操作日志', '/system/logs', 'system/log/index', 'sys:log:query'],
  ]
  const permissions = ['admin:access', ...definitions.map((item) => item[3])]
  let detail = {
    logId: id,
    userId: id,
    operationType: 'update',
    operationTarget: 'blog:article',
    targetId: id,
    permissionCode: 'blog:article:update',
    result: 1,
    errorMessage: null,
    ipAddress: '192.0.2.0/24',
    operationTime: time,
    operationData: { title: '<img src=x onerror="alert(1)">', status: 1 },
  }
  let detailDenied = false
  await page.addInitScript(() => {
    localStorage.setItem('dl-admin-session', JSON.stringify({
      accessToken: 'settings-logs-fixture', expiresAt: Date.now() + 3600000,
    }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    function respond(data, status = 200) {
      return route.fulfill({
        status,
        contentType: 'application/json',
        headers: { 'X-Revision': '"fixture"' },
        body: JSON.stringify({ code: status === 200 ? 'OK' : 'FORBIDDEN',
          msg: status === 200 ? '成功' : '测试详情无权访问', data, errors: [] }),
      })
    }
    if (path === '/admin/me') {
      return respond({ userId: id, username: 'layout_test', nickname: '布局测试' })
    }
    if (path === '/admin/me/capabilities') {
      return respond({ roleCodes: [], permissionCodes: permissions,
        dataScopes: Object.fromEntries(permissions.map((permission) => [permission, 'ALL'])) })
    }
    if (path === '/admin/me/menus') {
      return respond(definitions.map(([menuName, path, component, permissionCode], index) => ({
        menuId: String(index + 1), parentId: null, menuName, path, component,
        permissionCode, menuType: 'M', status: 1, isVisible: 1, children: [],
      })))
    }
    if (path === '/admin/home/profile') {
      return respond({ userId: id, nickname: '测试主人', tagline: '测试标语' })
    }
    if (path.startsWith('/admin/configs/')) {
      return respond({ configKey: path.split('/').at(-1), isPublic: 1, description: null,
        configValue: { title: '测试站点', enabled: true, reviewRequired: true } })
    }
    if (path === '/admin/operation-logs') {
      const { operationData, ...row } = detail
      return respond({ page: 1, pageSize: 10, total: '1', totalPages: '1', records: [row] })
    }
    if (path === `/admin/operation-logs/${id}`) {
      return respond(detailDenied ? null : detail, detailDenied ? 403 : 200)
    }
    throw new Error(`未预期的请求：${route.request().method()} ${path}`)
  })

  for (const width of [2514, 1280, 390]) {
    await page.setViewportSize({ width, height: 900 })
    for (const [title, path] of definitions.slice(0, 2)) {
      await page.goto(`http://127.0.0.1:5176${path}`)
      await page.getByRole('heading', { name: title, exact: true }).waitFor()
      await page.locator('.edit-form').waitFor()
      await page.locator('.el-loading-mask').waitFor({ state: 'hidden' })
      const bounds = await page.locator('.edit-form').evaluate((form) => ({
        card: form.closest('.el-card').getBoundingClientRect().width,
        section: form.closest('section').getBoundingClientRect().width,
        document: document.documentElement.scrollWidth,
        viewport: window.innerWidth,
      }))
      if (Math.abs(bounds.card - bounds.section) > 1 || bounds.document > bounds.viewport) {
        throw new Error(`${title}在 ${width}px 下未铺满内容区或页面溢出：${JSON.stringify(bounds)}`)
      }
      if (width === 2514) {
        await page.screenshot({ path: `output/playwright/settings-logs-${path.split('/').at(-1)}.png`, fullPage: true })
      }
      if (path === '/system/config') {
        for (const tab of ['评论设置', '留言设置', '站点信息']) {
          await page.getByRole('button', { name: tab, exact: true }).click()
          await page.locator('.el-loading-mask').waitFor({ state: 'hidden' })
          await page.locator('.page-tabs button.active').filter({ hasText: tab }).waitFor()
        }
      }
    }
  }

  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('http://127.0.0.1:5176/system/logs')
  await page.getByRole('button', { name: '详情', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '操作日志详情' })
  await dialog.waitFor()
  await dialog.getByRole('cell', { name: '操作数据', exact: true }).waitFor()
  for (const value of [id, 'update', 'blog:article', 'blog:article:update', '成功', '192.0.2.0/24']) {
    await dialog.locator('.el-descriptions__content').getByText(value, { exact: true }).first().waitFor()
  }
  if (await dialog.locator('.json-content').innerText() !== JSON.stringify(detail.operationData, null, 2)) {
    throw new Error('日志操作数据未完整展示')
  }
  if (await dialog.locator('img').count() || await dialog.locator('.el-descriptions__label').count() !== 11) {
    throw new Error('日志字段缺失或操作数据未按文本展示')
  }
  await page.evaluate(() => Promise.all(document.getAnimations()
    .filter((animation) => Number.isFinite(animation.effect.getComputedTiming().iterations))
    .map((animation) => animation.finished)))
  await page.screenshot({ path: 'output/playwright/settings-logs-detail.png', fullPage: true })
  await dialog.getByRole('button', { name: '关闭此对话框' }).click()
  await dialog.waitFor({ state: 'hidden' })

  detail = { ...detail, userId: null, targetId: null, permissionCode: null,
    result: 0, errorMessage: '测试操作失败', ipAddress: null, operationData: null }
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '详情', exact: true }).click()
  await dialog.getByText('失败', { exact: true }).waitFor()
  await dialog.getByText('测试操作失败', { exact: true }).waitFor()
  if (await dialog.getByText('—', { exact: true }).count() !== 5) {
    throw new Error('日志可空字段未显示占位符')
  }
  await page.evaluate(() => Promise.all(document.getAnimations()
    .filter((animation) => Number.isFinite(animation.effect.getComputedTiming().iterations))
    .map((animation) => animation.finished)))
  const box = await dialog.boundingBox()
  if (box.x < 0 || box.x + box.width > 390 || await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) {
    throw new Error('移动端日志详情溢出')
  }
  await page.screenshot({ path: 'output/playwright/settings-logs-mobile.png', fullPage: true })
  await dialog.getByRole('button', { name: '关闭此对话框' }).click()
  await dialog.waitFor({ state: 'hidden' })

  detailDenied = true
  await page.getByRole('button', { name: '详情', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '测试详情无权访问' }).waitFor()
  if (await dialog.isVisible() || failures.length) {
    throw new Error(`详情失败仍弹窗或出现运行错误：${JSON.stringify(failures)}`)
  }
  return { widths: [2514, 1280, 390], configTabs: 3, detailFields: 11,
    nullableFields: true, failedOperation: true, escapedData: true,
    requestFailure: true, mobileDialog: true, runtimeErrors: failures }
}
