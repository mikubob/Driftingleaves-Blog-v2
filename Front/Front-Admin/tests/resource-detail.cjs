// 启动 pnpm dev --host 127.0.0.1 --port 5177 后，用 playwright-cli run-code --filename tests/resource-detail.cjs 执行。
async (page) => {
  page.setDefaultTimeout(10000)
  const origin = 'http://127.0.0.1:5177'
  const id = '9007199254740993'
  const requests = []
  const errors = []
  const description = '<b>描述按文本展示</b>\n第二行'
  page.on('pageerror', error => errors.push(error.message))
  let current
  let mode = 'success'
  let release
  const fixtures = [
    { view: 'Categories', resource: 'categories', kind: '分类', permission: 'blog:category', nameKey: 'categoryName',
      data: { categoryId: id, categoryName: '最新分类', slug: 'category-fixture', description, sortOrder: 0, status: 0 } },
    { view: 'Tags', resource: 'tags', kind: '标签', permission: 'blog:tag', nameKey: 'tagName',
      data: { tagId: id, tagName: '最新标签', slug: 'tag-fixture' } },
    { view: 'Links', resource: 'links', kind: '友链', permission: 'blog:link', nameKey: 'linkName',
      data: { linkId: id, linkName: '最新友链', url: `https://example.test/${'long-path-'.repeat(15)}`, avatar: null, description, sortOrder: 0, status: 0 } },
  ]
  await page.route(`${origin}/__resource-detail-check`, route => route.fulfill({
    contentType: 'text/html',
    body: '<html><head><link rel="stylesheet" href="/node_modules/element-plus/dist/index.css"><link rel="stylesheet" href="/src/assets/styles/main.css"><link rel="stylesheet" href="/src/style.css"></head><body><div id="fixture"></div></body></html>',
  }))
  await page.route('**/api/v1/admin/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    requests.push({ path, method: request.method(), authorization: request.headers().authorization })
    const data = { ...current.data, createTime: '2026-11-01T00:00:00.000Z', updateTime: '2026-11-02T00:00:00.000Z' }
    if (path.endsWith(`/${current.resource}/${id}`)) {
      if (mode === 'slow') await new Promise(resolve => { release = resolve })
      if (mode === 'error') {
        await route.fulfill({ status: 404, json: { code: 'NOT_FOUND', msg: `${current.kind}不存在`, data: null, errors: [] } })
        return
      }
      if (mode === 'empty') {
        data.description = null
        if (current.resource === 'links') data.status = 2
      }
      await route.fulfill({ headers: { 'X-Revision': '"fixture-version"' }, json: { code: 'OK', msg: '成功', data, errors: [] } })
      return
    }
    if (!path.endsWith(`/${current.resource}`)) throw new Error(`不应请求额外的接口：${path}`)
    data[current.nameKey] = '列表中的旧名称'
    await route.fulfill({ json: { code: 'OK', msg: '成功', data: {
      page: 1, pageSize: 10, total: '1', totalPages: '1', records: [data],
    }, errors: [] } })
  })
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  for (const fixture of fixtures) {
    current = fixture
    mode = 'success'
    await page.setViewportSize({ width: 1280, height: 1000 })
    await page.goto(`${origin}/__resource-detail-check`)
    await page.evaluate(async ({ view, permission }) => {
      const source = await (await fetch(`/src/views/${view}.vue`)).text()
      const dependency = name => source.match(new RegExp(`from "([^" ]*/${name}\\.js[^" ]*)"`))[1]
      const { createApp } = await import(dependency('vue'))
      const { default: ElementPlus } = await import(dependency('element-plus'))
      const { default: View } = await import(`/src/views/${view}.vue`)
      const { session } = await import('/src/session.js')
      const { setAuthorization } = await import('/src/api.js')
      session.capabilities.permissionCodes = [`${permission}:query`]
      setAuthorization('fixture-token')
      window.testApp = createApp(View).use(ElementPlus)
      window.testApp.mount('#fixture')
    }, fixture)
    const dialog = page.getByRole('dialog', { name: `${fixture.kind}详情`, exact: true })
    const open = () => page.getByRole('button', { name: '详情', exact: true }).click()
    const close = async () => {
      await dialog.getByRole('button', { name: '关闭', exact: true }).click()
      await dialog.waitFor({ state: 'hidden' })
    }
    await open()
    await dialog.getByText(fixture.data[fixture.nameKey], { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('.dialog-fade-enter-active'))
    check((await dialog.innerText()).includes(id), '大ID应完整展示')
    check(await page.getByRole('button', { name: '编辑', exact: true }).count() === 0, '只需查询权限即可看详情')
    check(await dialog.locator('input, textarea').count() === 0, '详情应为只读')
    if (fixture.resource !== 'tags') {
      check(await dialog.locator('.detail-text').textContent() === description && await dialog.locator('.detail-text b').count() === 0, '描述必须转义并保留换行')
      check((await dialog.innerText()).includes(fixture.resource === 'categories' ? '已隐藏' : '待审核'), '状态应按资源展示')
    }
    await page.screenshot({ path: `output/playwright/${fixture.resource}-detail-desktop.png`, animations: 'disabled' })
    await page.setViewportSize({ width: 390, height: 844 })
    check(await dialog.evaluate(element => element.getBoundingClientRect().width <= window.innerWidth), '弹窗不能超出手机屏幕')
    check(await dialog.locator('.el-descriptions__table').evaluate(element => element.scrollWidth <= element.clientWidth + 1), '详情长文本不能撑破表格')
    await page.screenshot({ path: `output/playwright/${fixture.resource}-detail-mobile.png`, animations: 'disabled' })
    await close()
    mode = 'error'
    await open()
    await dialog.getByText(`${fixture.kind}不存在`, { exact: true }).waitFor()
    check(await dialog.locator('.el-descriptions').count() === 0, '失败不能展示旧详情')
    mode = 'empty'
    await dialog.getByRole('button', { name: '重新加载', exact: true }).click()
    await dialog.getByText(fixture.data[fixture.nameKey], { exact: true }).waitFor()
    if (fixture.resource !== 'tags') check((await dialog.innerText()).includes('未填写'), '可空字段应有占位')
    if (fixture.resource === 'links') check((await dialog.innerText()).includes('已下架'), '下架友链也能查看')
    await close()
    mode = 'slow'
    await open()
    await dialog.getByText('正在加载详情…').waitFor()
    await close()
    mode = 'success'
    release()
    await open()
    await dialog.getByText(fixture.data[fixture.nameKey], { exact: true }).waitFor()
    await close()

    // 原有编辑入口仍独立读取详情及版本，不与只读弹窗共享表单状态。
    await page.evaluate(async permission => {
      const { session } = await import('/src/session.js')
      session.capabilities.permissionCodes.push(`${permission}:update`)
    }, fixture.permission)
    await page.getByRole('button', { name: '编辑', exact: true }).click()
    const editor = page.getByRole('dialog', { name: `编辑${fixture.kind}`, exact: true })
    await editor.waitFor()
    check(await editor.locator('input').first().inputValue() === fixture.data[fixture.nameKey], '编辑仍使用最新详情')
    await editor.getByRole('button', { name: '取消', exact: true }).click()
    await editor.waitFor({ state: 'hidden' })
    await page.evaluate(() => window.testApp.unmount())
    console.log(`${fixture.kind}：详情、查询权限、状态、重试、关闭取消、手机布局和编辑回归通过`)
  }
  check(requests.every(item => item.method === 'GET' && item.authorization === 'Bearer fixture-token'), '全部交互只发送携带会话的查询')
  check(errors.length === 0, `页面运行异常：${errors.join(', ')}`)
  return '分类、标签、友链详情与原有编辑入口验证通过；仅在HTTP边界使用虚构数据'
}
