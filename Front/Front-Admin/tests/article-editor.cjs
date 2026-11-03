// 启动 pnpm dev --host 127.0.0.1 --port 5177 后，用 playwright-cli run-code --filename tests/article-editor.cjs 执行。
async (page) => {
  page.setDefaultTimeout(10000)
  const origin = 'http://127.0.0.1:5177'
  const writes = []
  const errors = []
  const conflict = 'URL 标识已存在，不允许重复，请修改后重试。'
  page.on('pageerror', error => errors.push(error.message))
  await page.route(`${origin}/__article-editor-check`, route => route.fulfill({
    contentType: 'text/html',
    body: '<html style="height:100%"><head><link rel="stylesheet" href="/node_modules/element-plus/dist/index.css"><link rel="stylesheet" href="/src/style.css"></head><body style="height:100%;margin:0"><div id="fixture" style="height:100%"></div></body></html>',
  }))
  await page.route('**/api/v1/admin/articles**', async route => {
    const request = route.request()
    const body = request.postDataJSON()
    writes.push({ method: request.method(), body, revision: request.headers()['if-revision'] })
    if (body.slug === 'validation') {
      await route.fulfill({ status: 400, json: {
        code: 'VALIDATION_ERROR', msg: 'URL 标识：只能使用字母、数字及单个连字符。', data: null,
        errors: [{ field: 'slug', message: 'URL 标识：只能使用字母、数字及单个连字符。' }],
      } })
      return
    }
    if (body.slug === 'test') {
      await route.fulfill({ status: 409, json: {
        code: 'UNIQUE_CONFLICT', msg: conflict, data: null, errors: [{ field: 'slug', message: conflict }],
      } })
      return
    }
    const data = body.items ? { items: [{ id: '1', revision: '"published"' }] }
      : { articleId: '1', status: 0, markdownContent: body.markdownContent }
    await route.fulfill({ headers: { 'X-Revision': '"saved"' }, json: { code: 'OK', msg: '成功', data, errors: [] } })
  })
  await page.goto(`${origin}/__article-editor-check`)
  await page.evaluate(async () => {
    const source = await (await fetch('/src/views/ArticleEditor.vue')).text()
    const dependency = name => source.match(new RegExp(`from "([^" ]*/${name}\\.js[^" ]*)"`))[1]
    const { createApp, h } = await import(dependency('vue'))
    const { createRouter, createMemoryHistory, RouterView } = await import(dependency('vue-router'))
    const { default: ElementPlus } = await import(dependency('element-plus'))
    const { default: Editor } = await import('/src/views/ArticleEditor.vue')
    const { session } = await import('/src/session.js')
    const { setAuthorization } = await import('/src/api.js')
    session.capabilities.permissionCodes = ['blog:article:create', 'blog:article:update', 'blog:article:publish']
    setAuthorization('fixture-token')
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/articles/:id?', name: 'article-editor', component: Editor, meta: { articleListRoute: 'articles' } },
      { path: '/list', name: 'articles', component: { render: () => h('p', '文章列表测试入口') } },
    ] })
    await router.push('/articles')
    window.testApp = createApp({ render: () => h(RouterView) }).use(router).use(ElementPlus)
    window.testApp.mount('#fixture')
  })
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  const save = page.getByRole('button', { name: '保存文章', exact: true })
  const title = page.getByRole('textbox', { name: '文章标题', exact: true })
  const slug = page.getByRole('textbox', { name: 'URL 标识', exact: true })
  await save.click()
  await page.locator('.el-message--warning').getByText('请输入文章标题。', { exact: true }).waitFor()
  check(writes.length === 0, '标题为空不能发送写请求')
  await title.fill('test')
  await slug.fill('bad slug')
  await save.click()
  await page.locator('.el-message--warning').getByText('请输入有效的 URL 标识（字母、数字及单个连字符）。', { exact: true }).waitFor()
  check(writes.length === 0, 'URL格式错误不能发送写请求')
  await slug.fill('test')
  await save.click()
  await page.locator('.el-message--error').getByText(conflict, { exact: true }).waitFor()
  check(await title.inputValue() === 'test' && await slug.inputValue() === 'test', '失败应保留全部输入')
  check(await page.locator('.editor-error').count() === 0, '错误使用顶部tip，不占用编辑区')
  await page.waitForFunction(() => {
    const tip = document.querySelector('.el-message--error')
    return tip && getComputedStyle(tip).opacity === '1'
  })
  await page.screenshot({ path: 'output/playwright/article-editor-conflict.png' })
  await slug.fill('validation')
  await save.click()
  await page.locator('.el-message--error').getByText('URL 标识：只能使用字母、数字及单个连字符。', { exact: true }).waitFor()
  check(await slug.inputValue() === 'validation', '后端字段校验失败仍保留输入，提示不重复追加英文字段名')
  await slug.fill('test-2')
  await save.click()
  await page.locator('.el-message--success').getByText('文章已保存', { exact: true }).waitFor()
  check(writes.at(-1).body.title === 'test' && writes.at(-1).body.slug === 'test-2', '修改URL后仍提交原文章标题')
  await slug.fill('test')
  await save.click()
  await page.waitForFunction(() => !document.querySelector('.edit-actions .is-loading'))
  check(writes.at(-1).method === 'PUT' && writes.at(-1).revision === '"saved"', '更新仍携带原版本')
  check(writes.at(-1).body.slug === 'test', '修改文章也能处理重复URL')
  await slug.fill('test-3')
  await page.locator('.cm-content[contenteditable="true"]').fill('文章正文')
  await page.getByRole('button', { name: '发布', exact: true }).click()
  await page.getByText('文章列表测试入口', { exact: true }).waitFor()
  check(writes.at(-1).body.items[0].revision === '"saved"', '发布继续使用保存后的版本')
  check(errors.length === 0, `页面运行异常：${errors.join(', ')}`)
  return '文章标题保持不变、URL重复tip、表单校验tip、失败保留输入、保存及发布通过；HTTP边界使用虚构数据'
}
