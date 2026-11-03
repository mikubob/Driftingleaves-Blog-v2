// 启动 pnpm dev --host 127.0.0.1 --port 5178 后，用 playwright-cli run-code --filename tests/discussion-tree.cjs 执行。
async (page) => {
  page.setDefaultTimeout(10000)
  const origin = 'http://127.0.0.1:5178'
  const rootId = '9007199254740993'
  const childId = '9007199254740994'
  const grandchildId = '9007199254741099'
  const revision = `"${'a'.repeat(64)}"`
  const errors = []
  const calls = []
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  let current
  let failReplies = false
  let releaseReply
  let delayReplies = false
  page.on('pageerror', error => errors.push(error.message))
  await page.route(`${origin}/__discussion-tree-check`, route => route.fulfill({
    contentType: 'text/html',
    body: '<html><head><link rel="stylesheet" href="/node_modules/element-plus/dist/index.css"><link rel="stylesheet" href="/src/assets/styles/main.css"><link rel="stylesheet" href="/src/style.css"></head><body><div id="fixture"></div></body></html>',
  }))
  const record = (id, parentId, content, hasChildren = false, status = 0) => ({
    [current.idKey]: id, parentId, content, hasChildren, status,
    articleId: '101', articleTitle: '隔离测试文章', userId: '12',
    author: { userId: '12', nickname: '隔离作者', avatar: null },
    isSecret: 1, contentFormat: 0, notifyReply: 0, htmlContent: `<p>${content}</p>`,
    createTime: '2026-11-01T00:00:00.000Z', updateTime: '2026-11-01T00:00:00.000Z',
  })
  await page.route('**/api/v1/admin/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const params = url.searchParams
    const path = url.pathname
    const method = request.method()
    calls.push({ path, method, params: Object.fromEntries(params), body: request.postDataJSON(), authorization: request.headers().authorization })
    if (path.endsWith('/batch-review') || path.endsWith('/batch-delete')) {
      await route.fulfill({ json: { code: 'OK', msg: '成功', data: null, errors: [] } })
      return
    }
    if (method === 'POST' && path.endsWith('/replies')) {
      await route.fulfill({ status: 201, json: { code: 'OK', msg: '成功', data: record(grandchildId, childId, '后台回复'), errors: [] } })
      return
    }
    if (!path.endsWith(`/${current.resource}`)) {
      await route.fulfill({ headers: { 'X-Revision': revision }, json: {
        code: 'OK', msg: '成功', data: record(path.split('/').at(-1), rootId, '详情原文'), errors: [],
      } })
      return
    }
    let records
    let total = '1'
    let totalPages = '1'
    if (params.get('parentId') === rootId) {
      if (delayReplies) await new Promise(resolve => { releaseReply = resolve })
      if (failReplies) {
        failReplies = false
        await route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE', msg: '回复加载失败', data: null, errors: [] } })
        return
      }
      const start = params.get('page') === '2' ? 20 : 0
      const count = start === 20 ? 1 : 20
      records = Array.from({ length: count }, (_, index) => record(
        String(BigInt(childId) + BigInt(start + index)), rootId, `直接回复${start + index + 1}`, start + index === 0,
      ))
      total = '21'
      totalPages = '2'
    } else if (params.get('parentId') === childId) {
      records = [record(grandchildId, childId, '第三层回复')]
    } else if (!params.has('parentId')) {
      records = [record(grandchildId, childId, '筛选命中的待审核回复')]
    } else {
      records = [record(rootId, null, '已通过的根记录', true, 1)]
    }
    await route.fulfill({ json: { code: 'OK', msg: '成功', data: {
      page: Number(params.get('page')), pageSize: Number(params.get('pageSize')), total, totalPages, records,
    }, errors: [] } })
  })

  for (const fixture of [
    { view: 'Comments', resource: 'comments', idKey: 'commentId', permission: 'blog:comment', cascade: true },
    { view: 'Messages', resource: 'messages', idKey: 'messageId', permission: 'blog:message' },
  ]) {
    current = fixture
    calls.length = 0
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto(`${origin}/__discussion-tree-check`)
    await page.evaluate(async ({ view, permission }) => {
      const source = await (await fetch(`/src/views/${view}.vue`)).text()
      const dependency = name => source.match(new RegExp(`from "([^" ]*/${name}\\.js[^" ]*)"`))[1]
      const { createApp } = await import(dependency('vue'))
      const { default: ElementPlus } = await import(dependency('element-plus'))
      const { default: View } = await import(`/src/views/${view}.vue`)
      const { session } = await import('/src/session.js')
      const { setAuthorization } = await import('/src/api.js')
      const permissionCodes = ['query', 'review', 'moderate'].map(action => `${permission}:${action}`)
      session.capabilities = { roleCodes: [], permissionCodes, dataScopes: Object.fromEntries(permissionCodes.map(code => [code, 'ALL'])) }
      setAuthorization('fixture-token')
      window.testApp = createApp(View).use(ElementPlus)
      window.testApp.mount('#fixture')
    }, fixture)
    const row = text => page.locator('.el-table__body tr').filter({ has: page.getByRole('button', { name: text, exact: true }) })
    const expand = text => row(text).locator('.el-table__expand-icon').click()
    await row('已通过的根记录').waitFor()
    check(calls.length === 1 && calls[0].params.parentId === '0', '初始只请求根节点页，不能预取所有回复')
    await row('已通过的根记录').locator('.el-checkbox').click()
    failReplies = true
    await expand('已通过的根记录')
    await page.getByRole('button', { name: '重试加载回复', exact: true }).click()
    await row('直接回复20').waitFor()
    check(await row('已通过的根记录').getByRole('checkbox').isChecked(), '加载或重试子回复不能丢失已有选择')
    if (fixture.cascade) {
      check(await row('直接回复1').getByRole('checkbox').isChecked(), '已选父评论新加载的回复应继承选择')
      check(await row('直接回复20').getByRole('checkbox').isChecked(), '首批回复都应继承父评论选择')
    }
    await page.getByRole('button', { name: '取消选择', exact: true }).click()
    check(await row('直接回复21').count() === 0, '首次展开最多取当前一页')
    await expand('直接回复1')
    await row('第三层回复').waitFor()
    const positions = await Promise.all(['已通过的根记录', '直接回复1', '第三层回复'].map(text => row(text).locator('.discussion-preview').boundingBox()))
    check(positions[0].x < positions[1].x && positions[1].x < positions[2].x, '正文必须按回复层级缩进')
    if (fixture.cascade) {
      await row('已通过的根记录').locator('.el-checkbox').click()
      check(await row('第三层回复').getByRole('checkbox').isChecked(), '勾选根评论应联动全部已加载层级')
    }
    failReplies = true
    await page.getByRole('button', { name: '加载更多回复', exact: true }).click()
    await page.getByRole('button', { name: '重试加载回复', exact: true }).click()
    await row('直接回复21').waitFor()
    check(await row('直接回复1').count() === 1 && await row('第三层回复').count() === 1, '追加后不能重复已有回复或丢失嵌套分支')
    if (fixture.cascade) {
      check(await row('直接回复21').getByRole('checkbox').isChecked(), '加载更多回复应继承父评论选择')
      await row('已通过的根记录').locator('.el-checkbox').click()
      check(await row('第三层回复').getByRole('checkbox').isChecked() === false, '取消根评论应取消所有已加载子回复')
      check((await page.locator('.batch-count').innerText()).includes('已选 0 条'), '取消父评论后批量选择应清空')
    }
    check(await page.getByRole('button', { name: '加载更多回复', exact: true }).count() === 0, '末页不能继续加载')
    await row('已通过的根记录').scrollIntoViewIfNeeded()
    await page.screenshot({ path: `output/playwright/${fixture.resource}-tree.png`, animations: 'disabled' })
    await row('第三层回复').locator('.el-checkbox').click()
    check(await row('直接回复1').getByRole('checkbox').isChecked() === !!fixture.cascade, '父节点选择应符合页面的联动设置')
    check(await row('已通过的根记录').getByRole('checkbox').isChecked() === false, '部分子回复选中时根评论不能全选')
    await page.getByRole('button', { name: '批量通过', exact: true }).click()
    await page.getByRole('dialog', { name: '批量通过', exact: true }).getByRole('button', { name: '确认', exact: true }).click()
    await row('第三层回复').waitFor({ state: 'hidden' })
    const review = calls.find(call => call.path.endsWith('/batch-review'))
    const reviewIds = fixture.cascade ? [childId, grandchildId] : [grandchildId]
    check(review.body.status === 1 && review.body.items.every(item => item.revision === revision), '批量审核保留审核状态和原样版本')
    check(JSON.stringify(review.body.items.map(item => item.id).sort()) === JSON.stringify(reviewIds.sort()), '批量审核只提交真实选中回复')
    check((await page.locator('.batch-count').innerText()).includes('0'), '写入刷新后清空选择和展开缓存')

    await page.locator('.el-select').filter({ has: page.getByRole('combobox', { name: '审核状态' }) }).click()
    await page.getByRole('option', { name: '待审核', exact: true }).click()
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await row('筛选命中的待审核回复').waitFor()
    const filtered = calls.filter(call => call.path.endsWith(`/${fixture.resource}`)).at(-1)
    check(filtered.params.status === '0' && !Object.hasOwn(filtered.params, 'parentId'), '筛选必须包含父节点未命中的回复')
    check(await page.locator('.el-table__expand-icon').count() === 0, '平铺筛选结果不能重复展开同一记录')
    await page.getByRole('button', { name: '重置', exact: true }).click()
    await row('已通过的根记录').waitFor()
    await expand('已通过的根记录')
    await row('直接回复20').waitFor()
    await page.getByRole('checkbox', { name: 'Select all rows', exact: true }).locator('..').click()
    const selectedCount = fixture.cascade ? 21 : 1
    await page.locator('.batch-count').filter({ hasText: `已选 ${selectedCount} 条` }).waitFor()
    check(await row('直接回复1').getByRole('checkbox').isChecked() === !!fixture.cascade, '表头全选应遵守父子联动设置')
    await page.getByRole('button', { name: '取消选择', exact: true }).click()
    await row('直接回复1').locator('.el-checkbox').click()
    const pager = page.locator('.el-table__body tr').filter({ has: page.getByRole('button', { name: '加载更多回复', exact: true }) })
    check(await pager.getByRole('checkbox').isDisabled(), '加载按钮不能进入批量选择')
    await page.getByRole('button', { name: '批量删除', exact: true }).click()
    await page.getByRole('dialog', { name: '批量删除', exact: true }).getByRole('button', { name: '确认', exact: true }).click()
    await row('直接回复1').waitFor({ state: 'hidden' })
    check(calls.find(call => call.path.endsWith('/batch-delete')).body.ids.join() === childId, '批量删除保持字符串ID且不包含加载按钮')

    if (fixture.resource === 'messages') {
      await row('已通过的根记录').getByRole('button', { name: '回复', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '回复留言', exact: true })
      await dialog.getByRole('textbox').fill('后台继续回复')
      await dialog.getByRole('button', { name: '发送回复', exact: true }).click()
      await dialog.waitFor({ state: 'hidden' })
      check(calls.some(call => call.method === 'POST' && call.path.endsWith(`/${rootId}/replies`)), '原留言回复接口继续可用')
    }

    delayReplies = true
    const pendingReply = page.waitForRequest(request => new URL(request.url()).searchParams.get('parentId') === rootId)
    await expand('已通过的根记录')
    await pendingReply
    await page.getByRole('button', { name: '重置', exact: true }).click()
    delayReplies = false
    releaseReply()
    await row('已通过的根记录').waitFor()
    check(await row('直接回复1').count() === 0, '刷新后不能追加旧的异步回复结果')
    check(calls.every(call => call.authorization === 'Bearer fixture-token'), '列表及子回复请求必须携带后台身份')
  }
  check(errors.length === 0, `浏览器运行错误：${errors.join('; ')}`)
  return 'discussion-tree passed: roots, multi-level replies, pagination/retry, filters, comment cascade, batch revisions, delete, reply and stale requests'
}
