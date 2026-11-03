// 启动 pnpm dev --host 127.0.0.1 --port 5177 后，用 playwright-cli run-code --filename tests/user-detail.cjs 执行。
async (page) => {
  page.setDefaultTimeout(10000)
  const origin = 'http://127.0.0.1:5177'
  const userId = '9007199254740993'
  const requests = []
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  let mode = 'success'
  let release
  const user = {
    userId, username: 'detail_fixture', nickname: '详情演示', email: null, avatar: null,
    intro: '<b>简介按文本展示</b>\n第二行', status: 0, hasPassword: true,
    lastLoginTime: '2026-11-01T14:03:00.000Z', lastLoginIp: '198.51.100.8',
    lastLoginLocation: '江苏省 南京市', createTime: '2026-10-01T00:00:00.000Z',
    updateTime: '2026-11-01T14:03:00.000Z', roleIds: ['2', '3'],
    roles: [
      { roleId: '2', roleCode: 'member', roleName: '普通用户', status: 1 },
      { roleId: '3', roleCode: 'author', roleName: '作者', status: 0 },
    ],
  }
  await page.route(`${origin}/__user-detail-check`, route => route.fulfill({
    contentType: 'text/html',
    body: '<html><head><link rel="stylesheet" href="/node_modules/element-plus/dist/index.css"><link rel="stylesheet" href="/src/assets/styles/main.css"><link rel="stylesheet" href="/src/style.css"></head><body><div id="fixture"></div></body></html>',
  }))
  await page.route('**/api/v1/admin/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    requests.push({ path, method: request.method(), authorization: request.headers().authorization })
    if (path.endsWith(`/users/${userId}`)) {
      if (mode === 'slow') await new Promise(resolve => { release = resolve })
      if (mode === 'error') {
        await route.fulfill({ status: 404, json: { code: 'NOT_FOUND', msg: '用户不存在', data: null, errors: [] } })
        return
      }
      const data = mode === 'empty' ? { ...user, roleIds: [], roles: [], hasPassword: false } : user
      await route.fulfill({ headers: { 'X-Revision': '"fixture-version"' }, json: { code: 'OK', msg: '成功', data, errors: [] } })
      return
    }
    if (!path.endsWith('/users')) throw new Error(`不应请求额外的接口：${path}`)
    await route.fulfill({ json: { code: 'OK', msg: '成功', data: {
      page: 1, pageSize: 10, total: '1', totalPages: '1', records: [user],
    }, errors: [] } })
  })
  await page.goto(`${origin}/__user-detail-check`)
  await page.evaluate(async () => {
    const source = await (await fetch('/src/views/Users.vue')).text()
    const dependency = name => source.match(new RegExp(`from "([^" ]*/${name}\\.js[^" ]*)"`))[1]
    const { createApp } = await import(dependency('vue'))
    const { default: ElementPlus } = await import(dependency('element-plus'))
    const { default: Users } = await import('/src/views/Users.vue')
    const { session } = await import('/src/session.js')
    const { setAuthorization } = await import('/src/api.js')
    session.capabilities.permissionCodes = ['sys:user:query']
    setAuthorization('fixture-token')
    window.testApp = createApp(Users).use(ElementPlus)
    window.testApp.mount('#fixture')
  })
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  const dialog = page.getByRole('dialog', { name: '用户详情' })
  const open = () => page.getByRole('button', { name: '详情', exact: true }).click()
  const close = async () => {
    await dialog.getByRole('button', { name: '关闭', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
  }
  await page.setViewportSize({ width: 1280, height: 1100 })
  await open()
  await dialog.getByText('本站密码', { exact: true }).waitFor()
  await page.waitForFunction(() => !document.querySelector('.dialog-fade-enter-active'))
  const text = await dialog.innerText()
  check(text.includes(userId) && text.includes('198.51.100.8') && text.includes('作者'), '详情应展示最新账号资料、登录信息和角色')
  check(text.includes('已禁用') && text.includes('未设置') && text.includes('已设置'), '状态与空值应正确展示')
  check(await dialog.locator('.user-intro').textContent() === user.intro && await dialog.locator('.user-intro b').count() === 0, '简介必须按文本展示')
  check(requests.every(item => item.method === 'GET' && item.authorization === 'Bearer fixture-token'), '只发送携带会话的查询')
  await page.screenshot({ path: 'output/playwright/user-detail-desktop.png', animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  check(await dialog.evaluate(element => element.getBoundingClientRect().width <= window.innerWidth), '详情弹窗不能超出手机屏幕')
  await page.screenshot({ path: 'output/playwright/user-detail-mobile.png', animations: 'disabled' })
  await close()
  mode = 'error'
  await open()
  await dialog.getByText('用户不存在', { exact: true }).waitFor()
  check(await dialog.locator('.el-descriptions').count() === 0, '失败不能展示旧详情')
  mode = 'empty'
  await dialog.getByRole('button', { name: '重新加载', exact: true }).click()
  await dialog.getByText('未分配角色', { exact: true }).waitFor()
  await close()
  mode = 'slow'
  await open()
  await dialog.getByText('正在加载用户详情…').waitFor()
  await page.waitForFunction(() => document.querySelector('[aria-busy="true"]'))
  await close()
  mode = 'success'
  release()
  await open()
  await dialog.getByText('本站密码', { exact: true }).waitFor()
  await close()
  check(errors.length === 0, `页面运行异常：${errors.join(', ')}`)
  await page.evaluate(() => window.testApp.unmount())
  return '用户详情通过：仅查询权限、资料和角色、文本转义、空值、错误重试、加载关闭及重新打开、桌面和手机布局'
}
