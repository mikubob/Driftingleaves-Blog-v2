// 在 HTTP 边界使用虚构文章，验证真实 Vue 编辑器和路由，不连接业务服务。
async function articleEditorSmoke(page) {
  page.on('dialog', (dialog) => dialog.accept())
  await page.unroute('**/api/v1/**')
  const failures = []
  page.on('pageerror', (error) => failures.push(error.message))
  const articles = new Map()
  const writes = []
  const revisions = new Map()
  let nextId = 9007199254741000n
  let failPublish = false
  let failDetail = false
  let holdSave = null
  const permissions = ['admin:access', 'blog:article:query', 'blog:article:create',
    'blog:article:update', 'blog:article:publish', 'blog:category:query', 'blog:tag:query']
  function check(condition, message) {
    if (!condition) {
      throw new Error(message)
    }
  }
  await page.addInitScript(() => {
    localStorage.setItem('dl-admin-session', JSON.stringify({
      accessToken: 'article-editor-fixture', expiresAt: Date.now() + 3600000,
    }))
  })
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request()
    const path = new URL(req.url()).pathname.replace('/api/v1', '')
    function respond(data, status = 200, revision = '"options"') {
      return route.fulfill({ status, contentType: 'application/json',
        headers: { 'X-Revision': revision },
        body: JSON.stringify({ code: status === 200 ? 'OK' : 'VALIDATION_ERROR',
          msg: status === 200 ? '成功' : '测试发布失败', data, errors: [] }),
      })
    }
    if (path === '/admin/me') {
      return respond({ userId: '1', username: 'editor_test', nickname: '编辑测试' })
    }
    if (path === '/admin/me/capabilities') {
      return respond({ roleCodes: [], permissionCodes: permissions,
        dataScopes: Object.fromEntries(permissions.map((permission) => [permission, 'ALL'])) })
    }
    if (path === '/admin/me/menus') {
      return respond([{ menuId: '10', parentId: null, menuName: '文章管理', menuType: 'M',
        path: '/content/articles', component: 'content/article/index', status: 1,
        isVisible: 1, keepAlive: 1, permissionCode: 'blog:article:query', children: [] }])
    }
    if (path === '/admin/categories' || path === '/admin/tags') {
      return respond({ page: 1, pageSize: 100, total: '0', totalPages: '0', records: [] })
    }
    if (path === '/admin/articles' && req.method() === 'GET') {
      return respond({ page: 1, pageSize: 10, total: String(articles.size),
        totalPages: articles.size ? '1' : '0', records: [...articles.values()] })
    }
    if (path === '/admin/articles/batch-status') {
      const body = req.postDataJSON()
      const item = body.items[0]
      writes.push({ path, method: req.method(), body, revision: item.revision })
      check(!req.headers()['if-revision'], '统一发布不能使用单一版本头')
      check(item.revision === revisions.get(item.id), '发布使用了错误的逐条版本')
      if (failPublish) {
        failPublish = false
        return respond(null, 400)
      }
      articles.get(item.id).status = body.status
      const revision = `"revision-${writes.length}"`
      revisions.set(item.id, revision)
      return respond({ items: [{ id: item.id, revision }] }, 200)
    }
    if (path.startsWith('/admin/articles')) {
      const id = path.split('/')[3]
      if (req.method() === 'GET') {
        return respond(failDetail ? null : articles.get(id), failDetail ? 404 : 200, revisions.get(id))
      }
      const body = req.postDataJSON()
      writes.push({ path, method: req.method(), body, revision: req.headers()['if-revision'] })
      if (req.method() !== 'POST') {
        check(req.headers()['if-revision'] === revisions.get(id), '保存或发布使用了错误的版本')
      }
      if (holdSave) {
        await holdSave
        holdSave = null
      }
      const articleId = id || String(nextId++)
      const article = { articleId, title: '', slug: '', summary: '', categoryId: null,
        coverUrl: null, articleType: 1, originalUrl: null, status: 0, visibility: 1,
        isTop: 0, allowComment: 1, markdownContent: '', htmlContent: '<p>服务端预览</p>',
        tagIds: [], viewCount: '0', likeCount: '0', commentCount: '0', updateTime: null,
        ...articles.get(articleId), ...body }
      articles.set(articleId, article)
      const revision = `"revision-${writes.length}"`
      revisions.set(articleId, revision)
      return respond(article, 200, revision)
    }
    throw new Error(`未预期的请求：${req.method()} ${path}`)
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('http://127.0.0.1:5176/content/articles')
  async function newArticle(title, slug) {
    await page.getByRole('button', { name: '写文章', exact: true }).click()
    const input = page.getByRole('textbox', { name: '文章标题', exact: true })
    check(await input.inputValue() === '', '新建文章残留上次标题')
    check((await page.locator('.cm-content').innerText()).trim() === '', '新建文章残留上次正文')
    await input.fill(title)
    await input.press('Tab')
    await page.getByRole('textbox', { name: 'URL 标识', exact: true }).fill(slug)
    await page.locator('.cm-content').fill(`# ${title}\n\n独立正文`)
  }
  async function publish() {
    await page.locator('.edit-topbar').getByRole('button', { name: '发布', exact: true }).click()
    await page.waitForURL('**/content/articles')
  }

  // 连续新建必须生成独立 ID，返回缓存列表时也能看到新文章。
  await newArticle('第一篇独立文章', 'first-independent')
  await page.locator('.md-editor-preview h1').filter({ hasText: '第一篇独立文章' }).waitFor()
  await page.screenshot({ path: 'output/playwright/article-editor.png', fullPage: true })
  await publish()
  await page.locator('.el-table').getByText('第一篇独立文章', { exact: true }).waitFor()
  await newArticle('第二篇独立文章', 'second-independent')
  await publish()
  await page.locator('.el-table').getByText('第二篇独立文章', { exact: true }).waitFor()
  check(articles.size === 2, '连续发布覆盖了已有文章')
  check([...articles.values()].every((article) => article.status === 1), '文章没有全部发布')

  // 草稿续存使用 PUT，发布失败保留当前内容和 ID，重试不再创建。
  await newArticle('草稿文章', 'draft-article')
  await page.getByRole('button', { name: '保存文章', exact: true }).click()
  await page.waitForURL(/\/edit\/\d+$/)
  await page.getByRole('textbox', { name: '文章标题', exact: true }).fill('草稿修改')
  const createdId = [...articles.keys()].at(-1)
  failPublish = true
  await page.locator('.edit-topbar').getByRole('button', { name: '发布', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '测试发布失败' }).waitFor()
  check(await page.getByRole('textbox', { name: '文章标题', exact: true }).inputValue() === '草稿修改', '发布失败丢失输入')
  check(articles.size === 3 && articles.get(createdId).status === 0, '发布失败未保留独立草稿')
  await publish()
  check(articles.size === 3 && articles.get(createdId).status === 1, '发布重试重复创建文章')

  // 提交中的新输入保持未保存，不能被响应覆盖或被自动跳转丢弃。
  await newArticle('提交快照', 'snapshot-article')
  let releaseSave
  holdSave = new Promise((resolve) => { releaseSave = resolve })
  const pending = page.waitForRequest((request) => request.method() === 'POST' && request.url().endsWith('/admin/articles'))
  await page.locator('.edit-topbar').getByRole('button', { name: '发布', exact: true }).click()
  await pending
  await page.keyboard.press('Control+s')
  await page.getByRole('textbox', { name: '文章标题', exact: true }).fill('提交期间的新输入')
  releaseSave()
  await page.getByText('文章已保存并发布', { exact: true }).last().waitFor()
  check(new URL(page.url()).pathname.endsWith('/edit'), '有未保存输入时错误跳转')
  check(await page.getByRole('textbox', { name: '文章标题', exact: true }).inputValue() === '提交期间的新输入', '保存响应覆盖了新输入')
  await page.getByText('有未保存的修改', { exact: true }).waitFor()
  check([...articles.values()].at(-1).title === '提交快照', '未按提交快照保存')
  await page.keyboard.press('Control+s')
  await page.waitForURL(/\/edit\/\d+$/)
  check(articles.size === 4 && [...articles.values()].at(-1).title === '提交期间的新输入', '快捷键续存重复创建或丢失输入')

  // 非法预览 HTML 不执行；移动端编辑区与发布设置均可访问。
  await page.locator('.cm-content').fill('<img src=x onerror="window.editorXss = 1"><script>window.editorXss = 1</script>')
  await page.waitForFunction(() => !document.querySelector('.md-editor-preview [onerror]'))
  check(await page.evaluate(() => window.editorXss) === undefined, '实时预览执行了文章脚本')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForFunction(() => document.querySelector('.sidebar').getBoundingClientRect().right <= 0)
  await page.locator('.el-message').last().waitFor({ state: 'hidden' })
  await page.screenshot({ path: 'output/playwright/article-editor-mobile.png', fullPage: true })
  check(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), '移动端出现横向溢出')
  await page.getByRole('button', { name: '返回列表', exact: true }).click()
  await page.getByRole('button', { name: '不保存', exact: true }).click()
  await page.waitForURL('**/content/articles')

  // 详情读取失败不能保存空白表单覆盖原文章。
  failDetail = true
  const writeCount = writes.length
  await page.getByRole('button', { name: '编辑', exact: true }).first().click()
  await page.locator('.edit-topbar').getByRole('button', { name: '保存文章', exact: true }).waitFor()
  check(await page.getByRole('button', { name: '保存文章', exact: true }).isDisabled(), '详情失败仍允许保存')
  await page.keyboard.press('Control+s')
  check(writes.length === writeCount, '详情失败时发送了覆盖请求')
  check(!failures.length, `浏览器运行错误：${failures.join('; ')}`)
  return { independentArticles: articles.size, createRequests: writes.filter((write) => write.method === 'POST').length,
    publishRedirect: true, draftRetry: true, pendingInputPreserved: true, cachedListRefreshed: true,
    revisionChecked: true, previewSafe: true, mobileFits: true, loadFailureBlocked: true }
}
