// 启动博客 pnpm dev --port 5180 后，从仓库根目录运行 playwright-cli run-code --filename Front/Front-Blog/tests/ui-actions.cjs。
async (page) => {
  page.setDefaultTimeout(10000)
  const origin = 'http://127.0.0.1:5180'
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  const settled = () => page.waitForFunction(() => document.getAnimations().filter(animation => animation.playState === 'running' && animation.effect.getComputedTiming().iterations !== Infinity).length === 0)
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'no-preference' })
  const time = '2026-11-01T00:00:00.000Z'
  const user = { userId: '1', username: 'fixture', nickname: '测试读者', email: 'demo@example.test', avatar: null, intro: null, hasPassword: true }
  const article = {
    articleId: '101', title: '交互检查文章', slug: 'ui-actions', summary: '',
    author: { userId: '2', nickname: '演示作者', avatar: null }, category: null, tags: [], coverUrl: null,
    articleType: 1, isTop: 0, allowComment: 1, viewCount: '0', likeCount: '0', commentCount: '1',
    publishTime: time, updateTime: time, markdownContent: '# 正文\n\n这是一篇虚构文章。', wordCount: '20', readingMinutes: 1,
    prevArticle: null, nextArticle: null, relatedArticles: [],
  }
  const comment = {
    commentId: '10', articleId: '101', content: '虚构评论', createTime: time,
    location: null, userAgentOs: null, userAgentBrowser: null, status: 1, replyCount: '0',
    author: { userId: '2', nickname: 'A'.repeat(64), avatar: null },
  }
  await page.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort())
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname
    let data = []
    if (path.endsWith('/online/tickets')) return route.fulfill({ status: 400, json: { code: 'INVALID_ARGUMENT', msg: '隔离验收', data: null, errors: [] } })
    if (path.endsWith('/profile')) data = null
    else if (path.endsWith('/statistics')) data = { articleCount: '1', categoryCount: '0', tagCount: '0', pageViewCount: '0', visitorCount: '0', todayPageViewCount: '0' }
    else if (path.endsWith('/auth/math-captcha')) data = { captchaId: 'a'.repeat(43), expiresIn: 120, question: '1 + 1 = ?' }
    else if (path.endsWith('/me')) data = user
    else if (path.endsWith('/rss-subscription')) data = { subscriptionId: '20', email: user.email || 'demo@example.test', status: 0 }
    else if (path.endsWith('/like')) data = { liked: false, likeCount: '0' }
    else if (path.endsWith('/articles/ui-actions')) data = article
    else if (/\/(categories|tags|music|articles|comments|liked-articles)$/.test(path)) {
      let records = []
      if (path.endsWith('/101/comments')) records = [comment]
      if (path.endsWith('/liked-articles')) records = [{ article, likedAt: time }]
      data = { page: 1, pageSize: 10, total: String(records.length), totalPages: records.length ? '1' : '0', records }
    }
    return route.fulfill({ headers: { 'X-Revision': '"fixture"' }, json: { code: 'OK', msg: '', data, errors: [] } })
  })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${origin}/articles/ui-actions`)
  await page.getByText('虚构评论', { exact: true }).waitFor()
  await page.evaluate(async user => {
    const { session } = await import('/src/session.js')
    session.user = user
    session.permissions = ['blog:comment:create', 'blog:article:like', 'blog:rss:subscribe']
  }, user)
  await page.getByRole('button', { name: '回复', exact: true }).press('Enter')
  const cancel = page.getByRole('button', { name: '取消回复', exact: true })
  await cancel.waitFor()
  const bounds = await cancel.boundingBox()
  check(bounds.x >= 0 && bounds.x + bounds.width <= 390, '64字符昵称下取消回复应保持在窄屏内')
  await page.getByRole('textbox', { name: '评论正文' }).fill('保留未提交的评论')
  await settled()
  await page.locator('.comment-card').screenshot({ path: 'output/playwright/ui-copy-cleanup/reply-mobile.png' })
  await cancel.press('Enter')
  check(await page.locator('.reply-hint').count() === 0, '回车应取消回复对象')
  check(await page.getByRole('textbox', { name: '评论正文' }).inputValue() === '保留未提交的评论', '取消回复不应删除已写正文')

  await page.evaluate(async () => { const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router; await router.push({ path: '/', query: { search: 'A'.repeat(100) } }) })
  const clear = page.getByRole('button', { name: '清除搜索', exact: true })
  await clear.waitFor()
  await settled()
  const clearBounds = await clear.boundingBox()
  check(clearBounds.x >= 0 && clearBounds.x + clearBounds.width <= 390, '长搜索词不应把清除按钮挤出窄屏')
  check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '长搜索词页面不应横向溢出')
  await page.locator('.search-result-tip').screenshot({ path: 'output/playwright/ui-copy-cleanup/search-mobile.png' })
  await clear.press('Enter')
  await page.waitForURL(`${origin}/`)

  await page.setViewportSize({ width: 1280, height: 1000 })
  await page.evaluate(async () => { const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router; await router.push('/account') })
  await page.getByRole('heading', { name: '邮件订阅' }).waitFor()
  const email = page.getByRole('textbox', { name: '账号邮箱', exact: true })
  await email.waitFor()
  check(await email.inputValue() === user.email && await email.getAttribute('readonly') !== null, '订阅应保持账号邮箱只读')
  check(await page.locator('#subscription-email-hint').count() === 0, '有邮箱时不应重复解释只读规则')
  await page.getByRole('button', { name: '重发确认邮件', exact: true }).waitFor()
  const unlike = page.getByRole('button', { name: '取消喜欢', exact: true })
  await unlike.waitFor()
  check(await unlike.evaluate(element => parseFloat(getComputedStyle(element).borderTopWidth) > 0), '取消喜欢应显示浅边框')
  await unlike.press('Enter')
  await page.getByText('确认取消喜欢？', { exact: true }).waitFor()
  await page.getByRole('button', { name: '取消', exact: true }).press('Enter')
  await page.getByText('确认取消喜欢？', { exact: true }).waitFor({ state: 'hidden' })
  await settled()
  await page.getByRole('heading', { name: '邮件订阅' }).locator('..').screenshot({ path: 'output/playwright/ui-copy-cleanup/account-desktop.png' })
  await page.evaluate(async () => { const { session } = await import('/src/session.js'); session.user.email = null })
  await page.locator('#subscription-email-hint').waitFor()
  check(await email.getAttribute('aria-describedby') === 'subscription-email-hint', '无邮箱提示应关联到输入框')
  check(await page.getByRole('button', { name: '重发确认邮件', exact: true }).isDisabled(), '无账号邮箱时不能提交订阅')
  await page.evaluate(async () => { const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router; await router.push('/') })
  await page.getByTitle('RSS 订阅', { exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'RSS 订阅', exact: true })
  await dialog.waitFor()
  const sidebarEmail = dialog.getByRole('textbox', { name: '账号邮箱', exact: true })
  await page.locator('#rss-email-hint').waitFor()
  check(await sidebarEmail.getAttribute('aria-describedby') === 'rss-email-hint', '弹窗缺失邮箱提示应关联输入框')
  await page.evaluate(async () => { const { session } = await import('/src/session.js'); session.user.email = 'demo@example.test' })
  await page.locator('#rss-email-hint').waitFor({ state: 'hidden' })
  check(await sidebarEmail.inputValue() === user.email && await sidebarEmail.getAttribute('readonly') !== null, '弹窗仍应只读展示账号邮箱')
  check(errors.length === 0, `浏览器运行异常：${errors.join('；')}`)
  return '窄屏长昵称取消回复、保留正文、长词清除搜索、取消喜欢、邮箱只读及缺失邮箱提示通过；仅使用虚构HTTP数据。'
}
