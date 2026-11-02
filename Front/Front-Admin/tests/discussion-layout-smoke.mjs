// 仅在 HTTP 边界提供虚构数据，验证评论/留言的真实页面、正文渲染和响应式详情。
async function discussionLayoutSmoke(page) {
  page.setDefaultTimeout(15000)
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  const id = '9007199254740993'
  const parentId = '9007199254740995'
  const time = '2026-11-02T00:30:00.000Z'
  const definitions = [
    ['评论', 'comments', 'content/comment/index', 'blog:comment'],
    ['留言', 'messages', 'content/message/index', 'blog:message'],
  ]
  const permissions = ['admin:access', ...definitions.flatMap((entry) =>
    ['query', 'review', 'moderate'].map((action) => `${entry[3]}:${action}`))]
  let markdown = true
  const comment = {
    commentId: id, articleId: id, userId: id, parentId, status: 1,
    author: { userId: id, nickname: '秋日来信', avatar: null },
    articleTitle: '把日常写成一封长信：代码与生活的记录',
    content: '<script>这段内容应作为普通文字显示</script>\n第二行：保留原始换行。\n' + '一段比较长的评论正文。'.repeat(30),
    location: '江苏省 南京市', userAgentOs: 'Windows', userAgentBrowser: 'Edge',
    createTime: time, updateTime: time,
  }
  function message() {
    return {
      messageId: id, parentId, author: comment.author, status: 1, isSecret: 1, isOwn: false,
      content: markdown ? '**留言的重点**\n\n欢迎交流，保留 Markdown 的段落和列表。' : '<b>纯文本留言</b>\n下一行仍然保留。',
      htmlContent: '<p><strong>留言的重点</strong></p><p>欢迎交流，保留 Markdown 的段落和列表。</p><ul><li>记录日常</li><li>分享技术</li></ul><pre><code>' + 'long_code_'.repeat(40) + '</code></pre>',
      contentFormat: markdown ? 1 : 0, notifyReply: 1, editedAt: time,
      location: null, userAgentOs: null, userAgentBrowser: null, createTime: time, updateTime: time,
    }
  }
  await page.addInitScript(() => localStorage.setItem('dl-admin-session', JSON.stringify({
    accessToken: 'discussion-layout-fixture', expiresAt: Date.now() + 3600000,
  })))
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    const respond = (data) => route.fulfill({ contentType: 'application/json',
      headers: { 'X-Revision': '"fixture"' }, body: JSON.stringify({ code: 'OK', msg: '成功', data, errors: [] }) })
    if (path === '/admin/me') {
      return respond({ userId: id, username: 'layout_test', nickname: '布局测试' })
    }
    if (path === '/admin/me/capabilities') {
      return respond({ roleCodes: [], permissionCodes: permissions,
        dataScopes: Object.fromEntries(permissions.map((permission) => [permission, 'ALL'])) })
    }
    if (path === '/admin/me/menus') {
      return respond(definitions.map(([title, resource, component, permission], index) => ({
        menuId: String(index + 1), parentId: null, menuName: `${title}管理`, path: `/content/${resource}`,
        component, permissionCode: `${permission}:query`, menuType: 'M', status: 1, isVisible: 1, children: [],
      })))
    }
    for (const [, resource] of definitions) {
      const row = resource === 'comments' ? comment : message()
      if (path === `/admin/${resource}`) {
        return respond({ page: 1, pageSize: 20, total: '1', totalPages: '1', records: [row] })
      }
      if (path === `/admin/${resource}/${id}`) {
        return respond(row)
      }
    }
    throw new Error(`未预期请求：${route.request().method()} ${path}`)
  })

  let checks = 0
  for (const width of [1440, 1280, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    for (const [title, resource] of definitions) {
      await page.goto(`http://127.0.0.1:5176/content/${resource}`)
      await page.getByRole('heading', { name: `${title}管理`, exact: true }).waitFor()
      await page.locator('.el-table__body-wrapper tbody tr').first().waitFor()
      const headers = (await page.locator('.el-table__header-wrapper th .cell').allTextContents()).filter(Boolean)
      const expected = [`${title}内容`, '昵称', '回复目标 ID', resource === 'comments' ? '所属文章' : '可见性', '状态', '发表时间', '操作']
      if (JSON.stringify(headers) !== JSON.stringify(expected)) {
        throw new Error(`列表列顺序错误：${resource} ${headers}`)
      }
      if (width === 1440) {
        await page.screenshot({ path: `output/playwright/${resource}-list.png`, fullPage: true })
      }
      await page.getByRole('button', { name: '详情', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: `${title}详情`, exact: true })
      const content = dialog.getByLabel(`${title}内容`, { exact: true })
      await content.waitFor()
      const target = dialog.getByRole('cell', { name: '回复目标 ID', exact: true }).locator('xpath=following-sibling::td[1]')
      if (await target.innerText() !== parentId) {
        throw new Error('回复目标 ID 丢失整数精度')
      }
      if (resource === 'comments') {
        if (await content.innerText() !== comment.content || await content.locator('script').count()) {
          throw new Error('评论正文未按纯文本保留')
        }
      } else {
        await content.getByText('留言的重点', { exact: true }).waitFor()
        if (await content.locator('strong').count() !== 1 || await content.locator('li').count() !== 2) {
          throw new Error('留言 Markdown 未保留富文本结构')
        }
        for (const label of ['IP 属地', '操作系统', '浏览器']) {
          const value = dialog.getByRole('cell', { name: label, exact: true }).locator('xpath=following-sibling::td[1]')
          if (await value.innerText() !== '未知') {
            throw new Error(`历史${label}未显示未知`)
          }
        }
      }
      const bounds = await content.evaluate((element) => ({
        border: getComputedStyle(element).borderTopWidth, height: element.getBoundingClientRect().height,
        document: document.documentElement.scrollWidth, viewport: innerWidth,
      }))
      if (bounds.border === '0px' || bounds.height < 96 || bounds.document > bounds.viewport) {
        throw new Error(`正文没有完整边框或页面溢出：${JSON.stringify(bounds)}`)
      }
      const cells = await dialog.locator('.el-descriptions__table tr').first().locator('td').count()
      if (cells !== (width <= 640 ? 2 : 4)) {
        throw new Error('详情未根据窗口切换列数')
      }
      await dialog.screenshot({ path: `output/playwright/${resource}-detail-${width}.png` })
      await dialog.getByRole('button', { name: '关闭', exact: true }).click()
      await dialog.waitFor({ state: 'hidden' })
      checks++
    }
  }

  // 详情打开期间改变窗口，检查媒体监听仍更新列数；纯文本留言不能按 HTML 渲染。
  markdown = false
  await page.setViewportSize({ width: 1280, height: 1000 })
  await page.reload()
  await page.getByRole('button', { name: '详情', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '留言详情', exact: true })
  const plain = dialog.getByLabel('留言内容', { exact: true })
  if (await plain.innerText() !== message().content || await plain.locator('b').count()) {
    throw new Error('纯文本留言被当作 HTML')
  }
  await page.setViewportSize({ width: 390, height: 1000 })
  await page.waitForFunction(() => document.querySelector('.discussion-detail tr')?.children.length === 2)
  if (errors.length) {
    throw new Error(errors.join('\n'))
  }
  return { passed: true, layouts: checks, plainText: true, markdown: true, responsiveDialog: true, runtimeErrors: errors }
}
