// 仅替换 HTTP 边界；全部内容为虚构测试数据，不连接业务库或外部账号。
async function smoke(page, reducedMotion = 'no-preference') {
  page.setDefaultTimeout(15000)
  await page.unroute('**/api/v1/**')
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.emulateMedia({ reducedMotion, colorScheme: 'light' })
  await page.addInitScript(() => {
    window.blogEffects = []
    document.addEventListener('transitionrun', (event) => {
      window.blogEffects.push({ className: event.target.className, property: event.propertyName })
    })
    document.addEventListener('animationstart', (event) => {
      window.blogEffects.push({ animation: event.animationName })
    })
  })
  const failures = []
  const trackingWarnings = []
  const unexpected = []
  const calls = []
  page.on('pageerror', (error) => failures.push(error.message))
  page.on('console', (entry) => {
    if (entry.text().startsWith('博客浏览统计未完成')) {
      trackingWarnings.push(entry.text())
    }
  })
  const id = '9007199254740993'
  const time = '2026-10-31T02:00:00.000Z'
  const revision = '"fictional-revision"'
  const user = {
    userId: id, username: 'test_leaf', nickname: '测试读者', avatar: null, email: null,
    intro: null, hasPassword: true, status: 1, createTime: time, updateTime: time,
  }
  const category = { categoryId: '2', categoryName: '技术手记', slug: 'technology', description: '代码里的小发现', sortOrder: 0 }
  const tag = { tagId: '3', tagName: '随笔', slug: 'notes' }
  const author = { userId: id, nickname: '测试作者', avatar: null }
  const articles = [
    {
      articleId: '101', title: '在代码与生活之间，留一处安静的角落', slug: 'quiet-corner',
      summary: '写代码，也写生活。把那些一闪而过的想法收集起来，慢慢整理成自己的答案。',
      author, category, tags: [tag], coverUrl: '/api/v1/blog/articles/101/files/1/content',
      articleType: 1, originalUrl: null, isTop: 1, allowComment: 1,
      viewCount: '128', likeCount: '12', commentCount: '1', publishTime: time, createTime: time, updateTime: time,
    },
    {
      articleId: '102', title: '让每一次请求，都有一个清楚的答案', slug: 'clear-response',
      summary: '从一份接口文档开始，聊聊真实的加载、错误与空数据，也聊聊软件里那些细小却重要的体验。',
      author, category, tags: [tag], coverUrl: null,
      articleType: 1, originalUrl: null, isTop: 0, allowComment: 1,
      viewCount: '86', likeCount: '8', commentCount: '0', publishTime: time, createTime: time, updateTime: time,
    },
    {
      articleId: '103', title: '秋日漫步：把日常写成一封长信', slug: 'autumn-letter',
      summary: '风从树梢经过，叶子有了新的去处。那些普通的日子，也值得被认真记下。',
      author, category: null, tags: [tag], coverUrl: null,
      articleType: 1, originalUrl: null, isTop: 0, allowComment: 1,
      viewCount: '64', likeCount: '6', commentCount: '0', publishTime: time, createTime: time, updateTime: time,
    },
  ]
  const comment = {
    commentId: '201', articleId: '101', parentId: null, author,
    content: '谢谢分享，这是一条虚构测试评论。', status: 1, replyCount: '1', createTime: time, updateTime: time,
  }
  let liked = false
  let keywordFailed = false
  let profileConflict = false
  let subscriptionState = null
  let messageState = {
    messageId: '301', parentId: null, author, content: '你好，欢迎来这个测试小站坐坐。',
    htmlContent: '<p>你好，欢迎来这个测试小站坐坐。</p>', contentFormat: 0, isSecret: 0,
    isOwn: true, status: 1, createTime: time, updateTime: time, editedAt: null, notifyReply: 0,
  }
  const permissions = [
    'blog:comment:create', 'blog:comment:deleteOwn', 'blog:message:create',
    'blog:message:updateOwn', 'blog:message:deleteOwn', 'blog:article:like', 'blog:rss:subscribe',
  ]
  function pagination(records, search) {
    return { page: Number(search.get('page') || 1), pageSize: Number(search.get('pageSize') || 10), total: String(records.length), totalPages: records.length ? '1' : '0', records }
  }
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.slice('/api/v1'.length)
    const method = req.method()
    const body = req.postDataJSON()
    calls.push({ path, method, body, authorization: req.headers().authorization, revision: req.headers()['if-revision'] })
    if (path === '/blog/auth/captcha') {
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({
        code: 'OK', msg: '成功', errors: [], data: { captchaId: 'a'.repeat(43), imageBase64: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', expiresIn: 120 },
      }) })
    }
    if (['POST', 'PUT'].includes(method) && req.headers()['x-captcha-code'] === '000000') {
      return route.fulfill({ status: 400, contentType: 'application/json',
        body: JSON.stringify({ code: 'CAPTCHA_INVALID', msg: '测试验证码错误', data: null, errors: [] }) })
    }
    if (method === 'POST' && (['/blog/auth/login', '/blog/auth/register', '/blog/messages'].includes(path)
      || /\/comments$/.test(path) || (/\/authorize$/.test(path) && body.operation === 'LOGIN'))
      || method === 'PUT' && /^\/blog\/me\/messages\/[^/]+$/.test(path)) {
      if (req.headers()['x-captcha-id'] !== 'a'.repeat(43) || req.headers()['x-captcha-code'] !== '012345') {
        throw new Error('缺少正确的验证码请求头')
      }
    }
    let data = null
    let status = 200
    let code = 'OK'
    const headers = { 'X-Revision': revision }
    if (path.endsWith('/files/1/content')) {
      return route.fulfill({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600"><rect width="800" height="600" fill="#bbc6b9"/><path d="M0 600 350 100 800 600" fill="#4c6359"/><path d="M200 600 650 200 950 600" fill="#83978a"/></svg>' })
    } else if (path === '/blog/profile') {
      data = { nickname: '落叶随记', tagline: '一个写代码、也写生活的人', description: '收集日常的微光，记录思考的痕迹。', avatar: null, website: null, githubUrl: null, publicEmail: null, location: '地球' }
    } else if (path === '/blog/configs') {
      data = [{ configKey: 'site', isPublic: 1, configValue: { title: 'DriftingLeaves', description: '随便坐坐，看看我写的字。些许技术、心得，以及生活里的胡思乱想。', icp: '', publicSecurityRecord: '', startDate: null } }]
    } else if (path === '/blog/statistics') {
      data = { articleCount: '3', categoryCount: '1', tagCount: '1', pageViewCount: '1024', todayPageViewCount: '12', visitorCount: '256' }
    } else if (path === '/blog/article-rankings') {
      data = articles.map((article) => ({ ...article, hotValue: article.viewCount }))
    } else if (path === '/blog/categories') {
      data = pagination([category], url.searchParams)
    } else if (path === '/blog/categories/technology') {
      data = { ...category, articleCount: '2' }
    } else if (path === '/blog/tags') {
      data = pagination([tag], url.searchParams)
    } else if (path === '/blog/tags/notes') {
      data = { ...tag, articleCount: '3' }
    } else if (path === '/blog/authors') {
      data = pagination([{ ...author, intro: '测试作者介绍', articleCount: '3', lastPublishTime: time }], url.searchParams)
    } else if (path === `/blog/authors/${id}`) {
      data = { ...author, intro: '测试作者介绍', articleCount: '3', lastPublishTime: time }
    } else if (path === '/blog/archives') {
      data = pagination([{ month: '2026-10', articleCount: '3' }], url.searchParams)
    } else if (path === '/blog/links') {
      data = pagination([{ linkId: '401', linkName: '测试友人', url: 'https://example.test/', avatar: null, description: '虚构的友链，用于交互验证。' }], url.searchParams)
    } else if (path === '/blog/music') {
      data = pagination([{ musicId: '601', title: '测试曲目', artist: '测试歌手', audioUrl: 'https://example.test/test.mp3', coverUrl: null }], url.searchParams)
    } else if (path === '/blog/social-links') {
      data = []
    } else if (path === '/blog/articles') {
      const keyword = url.searchParams.get('keyword')
      if (keyword === '限流' && !keywordFailed) {
        keywordFailed = true
        status = 429
        code = 'RATE_LIMITED'
        headers['Retry-After'] = '9'
      } else {
        data = pagination(keyword === '不存在' ? [] : articles, url.searchParams)
      }
    } else if (path === '/blog/articles/quiet-corner') {
      data = { ...articles[0], htmlContent: '<h2 id="hello">一个安静的角落</h2><p>这是虚构的测试正文，用来验证真实页面渲染。</p><p><a href="#hello">回到本节</a></p><pre><code>const leaves = []</code></pre><h3 id="details">继续记录</h3><p>文字，让时间有迹可循。</p>', wordCount: '96', readingMinutes: 1, prevArticle: null, nextArticle: { articleId: '102', title: articles[1].title, slug: articles[1].slug }, relatedArticles: [{ articleId: '103', title: articles[2].title, slug: articles[2].slug }] }
    } else if (path === '/blog/articles/101/comments') {
      data = method === 'POST' ? { ...comment, status: 0 } : pagination(url.searchParams.has('parentId') ? [{ ...comment, commentId: '202', parentId: '201', replyCount: '0', content: '一条测试回复。' }] : [comment], url.searchParams)
    } else if (path === '/blog/articles/101/like') {
      if (method === 'PUT') {
        liked = true
      } else if (method === 'DELETE') {
        liked = false
      }
      data = { liked, likeCount: liked ? '13' : '12' }
    } else if (path === '/blog/auth/oauth/providers') {
      data = [{ source: 'EXAMPLE', displayName: '测试平台' }]
    } else if (path === '/blog/auth/oauth/EXAMPLE/authorize') {
      data = { authorizationUrl: 'http://127.0.0.1:5174/oauth/complete#ticket=fictional-popup-ticket', expiresIn: 300 }
    } else if (path === '/blog/auth/oauth/exchange') {
      data = { operation: 'LOGIN', session: { tokenType: 'Bearer', accessToken: 'fictional-memory-token', expiresIn: 259200, user } }
    } else if (path === '/blog/auth/login') {
      data = { tokenType: 'Bearer', accessToken: 'fictional-memory-token', expiresIn: 259200, user }
    } else if (path === '/blog/auth/register' || path === '/blog/me') {
      data = user
    } else if (path === '/blog/me/capabilities') {
      data = { permissionCodes: permissions, roleCodes: ['member'], dataScopes: {} }
    } else if (path === '/blog/me/profile') {
      if (profileConflict) {
        status = 412
        code = 'REVISION_MISMATCH'
      } else {
        Object.assign(user, body)
        data = user
      }
    } else if (path === '/blog/me/liked-articles') {
      data = pagination([{ article: articles[0], likedAt: time }], url.searchParams)
    } else if (path === '/blog/me/comments') {
      data = pagination([comment], url.searchParams)
    } else if (path === '/blog/me/messages') {
      data = pagination([messageState], url.searchParams)
    } else if (path === '/blog/me/messages/301') {
      if (method === 'PUT') {
        messageState = { ...messageState, ...body, status: 0 }
      }
      data = method === 'DELETE' ? null : messageState
    } else if (path === '/blog/messages') {
      data = method === 'POST' ? { ...messageState, ...body, status: 0 } : pagination(url.searchParams.has('parentId') ? [] : [messageState], url.searchParams)
    } else if (path === '/blog/me/rss-subscription') {
      if (method === 'PUT') {
        subscriptionState = { subscriptionId: '501', email: body.email, status: 0 }
      } else if (method === 'DELETE') {
        subscriptionState = { ...subscriptionState, status: 2 }
      }
      data = subscriptionState
    } else if (path === '/blog/me/rss-subscription/confirm') {
      data = { subscriptionId: '501', email: 'leaf@example.test', status: 1 }
    } else if (path === '/blog/rss-subscriptions/unsubscribe' || path === '/blog/auth/logout' || path === '/blog/me/comments/201') {
      data = null
    } else if (path === '/blog/analytics/page-views') {
      data = { recorded: true }
    } else if (path === '/blog/analytics/online/tickets') {
      // 本检查只验证账号流程，在线服务在独立的 online-browser-smoke 中验证。
      status = 503
      code = 'SERVICE_UNAVAILABLE'
    } else {
      unexpected.push(`${method} ${path}`)
      status = 404
      code = 'NOT_FOUND'
    }
    return route.fulfill({ status, headers, contentType: 'application/json', body: JSON.stringify({ code, msg: status === 200 ? '成功' : '测试请求失败', data, errors: [] }) })
  })


  // 1. 原站三栏首页、时钟日历、音乐和侧栏弹窗。
  await page.goto('http://127.0.0.1:5174/')
  await page.getByRole('heading', { name: articles[0].title }).waitFor()
  await page.locator('.home-time-widgets').waitFor()
  // 动效必须由真实浏览器事件确认，避免只检查样式文本。
  await page.waitForFunction(() => window.blogEffects.some((effect) => effect.className?.includes('page-fade-enter-active')))
  await page.locator('.article-card').first().hover()
  await page.waitForFunction(() => window.blogEffects.some((effect) => effect.className?.includes('article-card') && effect.property === 'transform'))
  await page.locator('.theme-switch').click()
  await page.waitForFunction(() => window.blogEffects.some((effect) => effect.className?.includes('theme-switch__circle-container') && effect.property === 'left'))
  await page.locator('.theme-switch').click()
  await page.locator('.player-title').filter({ hasText: '测试曲目' }).waitFor()
  await page.screenshot({ path: 'output/playwright/legacy-desktop.png', fullPage: true })
  await page.locator('.info-stat').nth(1).click()
  await page.getByRole('dialog', { name: '所有分类' }).waitFor()
  await page.locator('.modal-item').filter({ hasText: '技术手记' }).click()
  await page.waitForURL('**/category/technology')
  await page.getByRole('heading', { name: articles[0].title }).waitFor()
  if (!page.url().endsWith('/category/technology')) {
    throw new Error('原分类弹窗未进入 V2 分类页面')
  }
  await page.locator('.search-toggle').click()
  await page.getByLabel('搜索文章', { exact: true }).fill('限流')
  await page.getByLabel('搜索文章', { exact: true }).press('Enter')
  await page.getByRole('alert').filter({ hasText: '9 秒' }).waitFor()
  await page.getByRole('heading', { name: '404', exact: true }).waitFor()
  await page.getByRole('link', { name: '返回首页', exact: true }).click()
  await page.getByRole('heading', { name: articles[0].title }).waitFor()
  await page.locator('.search-toggle').click()
  await page.getByLabel('搜索文章', { exact: true }).fill('不存在')
  await page.getByLabel('搜索文章', { exact: true }).press('Enter')
  await page.getByText('暂无文章', { exact: true }).waitFor()

  // 2. 原分类、标签、归档、热榜与友链页。
  for (const path of ['/category/technology', '/tag/notes', '/author/' + id, '/archive/2026-10']) {
    await page.goto('http://127.0.0.1:5174' + path)
    await page.getByRole('heading', { name: articles[0].title }).waitFor()
  }
  await page.goto('http://127.0.0.1:5174/hot')
  await page.getByRole('heading', { name: articles[0].title }).waitFor()
  await page.locator('.hot-filter-switch').nth(0).click()
  await page.locator('.hot-filter-switch').nth(1).click()
  await page.getByRole('heading', { name: articles[0].title }).waitFor()
  await page.goto('http://127.0.0.1:5174/archive')
  await page.getByRole('link', { name: '10 月 3 篇文章' }).click()
  await page.getByRole('heading', { name: articles[0].title }).waitFor()
  await page.goto('http://127.0.0.1:5174/links')
  await page.locator('.link-name').filter({ hasText: '测试友人' }).waitFor()

  // 3. 原文章 UI、目录锚点、V2 回复与点赞。
  await page.goto('http://127.0.0.1:5174/articles/quiet-corner')
  await page.getByRole('heading', { name: '一个安静的角落' }).waitFor()
  await page.locator('.toc-desktop .toc-item').filter({ hasText: '继续记录' }).click()
  if (await page.locator('#hello').count() !== 1) {
    throw new Error('后端锚点未保留')
  }
  await page.getByRole('button', { name: /^查看回复\s*\(\s*1\s*\)$/ }).click()
  await page.getByText('一条测试回复。', { exact: true }).waitFor()
  await page.getByRole('button', { name: '登录后点赞', exact: true }).click()
  await page.getByLabel('用户名', { exact: true }).fill('test_leaf')
  await page.getByLabel('密码', { exact: true }).fill('Fictional-password-2026')
  await page.getByLabel('六位数字验证码', { exact: true }).fill('000000')
  await page.getByRole('button', { name: '登录 →', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '测试验证码错误' }).last().waitFor()
  if (await page.getByLabel('用户名', { exact: true }).inputValue() !== 'test_leaf'
    || await page.getByLabel('六位数字验证码', { exact: true }).inputValue() !== '') {
    throw new Error('验证码失败后没有保留用户名或清空验证码')
  }
  await page.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '登录 →', exact: true }).click()
  await page.getByRole('button', { name: '点赞', exact: true }).click()
  await page.getByRole('button', { name: '取消点赞', exact: true }).waitFor()
  await page.waitForFunction(() => window.blogEffects.some((effect) => effect.animation?.includes('keyframes-svg-filled')))
  await page.getByLabel('评论正文', { exact: true }).fill('一条待审核的测试评论。')
  await page.getByLabel('六位数字验证码', { exact: true }).fill('000000')
  await page.getByRole('button', { name: '发表评论', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '测试验证码错误' }).last().waitFor()
  if (await page.getByLabel('评论正文', { exact: true }).inputValue() !== '一条待审核的测试评论。') {
    throw new Error('验证码失败后丢失评论正文')
  }
  await page.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '发表评论', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '评论已提交，审核通过后公开显示' }).waitFor()
  await page.screenshot({ path: 'output/playwright/legacy-article.png', fullPage: true })

  // 4. 原留言表单的 EmojiPicker、Markdown、私密性与编辑版本。
  await page.locator('.nav-desktop a[href="/message"]').click()
  await page.getByLabel('留言正文', { exact: true }).fill('一条私密的测试留言。')
  await page.getByLabel('悄悄话', { exact: true }).check()
  await page.getByLabel('Markdown', { exact: true }).check()
  await page.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '留言', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '留言已提交，正在等待审核' }).last().waitFor()
  const messageCall = calls.findLast((call) => call.path === '/blog/messages' && call.method === 'POST')
  if (messageCall.body.isSecret !== 1 || messageCall.body.contentFormat !== 1 || messageCall.body.parentId !== null) {
    throw new Error('原留言表单未适配 V2 字段')
  }
  await page.locator('.msg-actions').getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByLabel('留言正文', { exact: true }).fill('编辑后的测试留言。')
  await page.getByLabel('六位数字验证码', { exact: true }).fill('000000')
  await page.getByRole('button', { name: '修改', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '测试验证码错误' }).last().waitFor()
  if (await page.getByLabel('留言正文', { exact: true }).inputValue() !== '编辑后的测试留言。'
    || await page.getByLabel('六位数字验证码', { exact: true }).inputValue() !== '') {
    throw new Error('编辑验证码失败后丢失正文或未清空验证码')
  }
  await page.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '修改', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '留言已提交，正在等待审核' }).last().waitFor()
  if (!calls.some((call) => call.path === '/blog/me/messages/301' && call.method === 'PUT' && call.revision === revision)) {
    throw new Error('原留言编辑缺少 V2 版本头')
  }

  // 5. 原 RSS 弹窗和保留的 V2 账号流程。
  await page.locator('.info-social button').click()
  await page.getByRole('dialog', { name: 'RSS 订阅', exact: true }).waitFor()
  await page.getByLabel('订阅邮箱', { exact: true }).fill('leaf@example.test')
  await page.getByRole('button', { name: '订阅', exact: true }).click()
  await page.getByText('等待邮箱验证，请查收邮件；可重新提交以重发确认邮件。', { exact: true }).waitFor()
  await page.getByRole('dialog').locator('.el-dialog__headerbtn').click()
  await page.locator('.nav-desktop a[href="/account"]').click()
  await page.getByRole('button', { name: '我的留言', exact: true }).click()
  await page.getByRole('button', { name: '编辑留言', exact: true }).click()
  await page.getByLabel('编辑留言', { exact: true }).fill('账号页编辑后的留言。')
  await page.getByLabel('六位数字验证码', { exact: true }).fill('000000')
  await page.getByRole('button', { name: '保存修改', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '测试验证码错误' }).last().waitFor()
  if (await page.getByLabel('编辑留言', { exact: true }).inputValue() !== '账号页编辑后的留言。'
    || await page.getByLabel('六位数字验证码', { exact: true }).inputValue() !== '') {
    throw new Error('账号页编辑验证码失败后丢失正文或未清空验证码')
  }
  await page.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '保存修改', exact: true }).click()
  await page.getByRole('status').filter({ hasText: '留言已更新' }).waitFor()
  await page.getByLabel('昵称', { exact: true }).fill('冲突时保留此输入')
  profileConflict = true
  await page.getByRole('button', { name: '保存资料', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '内容已更新' }).waitFor()
  if (await page.getByLabel('昵称', { exact: true }).inputValue() !== '冲突时保留此输入') {
    throw new Error('资料冲突丢失输入')
  }
  profileConflict = false
  await page.getByRole('button', { name: '保存资料', exact: true }).click()
  await page.getByText('个人资料已更新。', { exact: true }).waitFor()
  const savedSession = await page.evaluate(() => localStorage.getItem('dl-blog-session'))
  await page.reload()
  await page.getByLabel('昵称', { exact: true }).waitFor()
  if (await page.evaluate(() => localStorage.getItem('dl-blog-session')) !== savedSession) {
    throw new Error('刷新后延长了博客会话有效期')
  }
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.goto('http://127.0.0.1:5174/account')
  await page.getByRole('heading', { name: '登录后，和这里保持联系' }).waitFor()

  // 6. 邮箱票据、登录弹窗来源验证与手动退订。
  await page.goto('http://127.0.0.1:5174/subscription/confirm#confirmTicket=fictional-confirm-ticket')
  await page.getByRole('heading', { name: '确认邮箱订阅' }).waitFor()
  if (new URL(page.url()).hash) {
    throw new Error('邮箱票据未立即清除')
  }
  await page.evaluate(() => window.postMessage({ type: 'dl-blog-oauth-login', ticket: 'forged-ticket' }, location.origin))
  if (calls.some((call) => call.path === '/blog/auth/oauth/exchange' && call.body.ticket === 'forged-ticket')) {
    throw new Error('接受了伪造弹窗消息')
  }
  await page.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '测试平台 ↗' }).click()
  await page.getByRole('button', { name: '确认订阅', exact: true }).click()
  await page.getByText('邮箱验证成功，已开启邮件订阅。', { exact: true }).waitFor()
  await page.goto('http://127.0.0.1:5174/subscription/unsubscribe#unsubscribeTicket=fictional-unsubscribe-ticket')
  await page.getByRole('button', { name: '确认退订', exact: true }).waitFor()
  if (calls.some((call) => call.path === '/blog/rss-subscriptions/unsubscribe')) {
    throw new Error('自动退订')
  }
  await page.getByRole('button', { name: '确认退订', exact: true }).click()
  await page.getByText('已取消邮件订阅。', { exact: true }).waitFor()

  // 7. 原导航与原文章页面在移动端可操作且无横向溢出。
  await page.goto('http://127.0.0.1:5174/')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('heading', { name: articles[0].title }).waitFor()
  await page.getByRole('button', { name: '展开导航', exact: true }).click()
  await page.locator('.nav-mobile-link').filter({ hasText: '归档' }).waitFor({ state: 'visible' })
  await page.getByRole('button', { name: '展开导航', exact: true }).click()
  await page.screenshot({ path: 'output/playwright/legacy-mobile.png', fullPage: true })
  await page.getByRole('button', { name: '展开导航', exact: true }).click()
  await page.locator('.nav-mobile-link').filter({ hasText: '深色模式' }).click()
  await page.getByRole('button', { name: '展开导航', exact: true }).click()
  await page.screenshot({ path: 'output/playwright/legacy-mobile-dark.png', fullPage: true })
  for (const path of ['/', '/articles/quiet-corner', '/message', '/login']) {
    await page.goto('http://127.0.0.1:5174' + path)
    if (path === '/') {
      await page.getByRole('heading', { name: articles[0].title }).waitFor()
    } else if (path.startsWith('/articles/')) {
      await page.getByRole('heading', { name: '一个安静的角落' }).waitFor()
    } else if (path === '/message') {
      await page.locator('.message-page').waitFor()
    } else {
      await page.getByRole('heading', { name: '很高兴，又见到你' }).waitFor()
    }
    const width = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, viewport: innerWidth }))
    if (width.document > width.viewport) {
      throw new Error('移动端溢出：' + path + JSON.stringify(width))
    }
  }
  await page.goto('http://127.0.0.1:5174/articles/quiet-corner')
  await page.locator('.toc-mobile-trigger').click()
  await page.waitForFunction(() => window.blogEffects.some((effect) => effect.className?.includes('toc-fade-enter-active')))
  await page.locator('.toc-mobile-close').click()
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
  await page.getByRole('button', { name: '回到顶部', exact: true }).waitFor({ state: 'visible' })
  await page.waitForFunction(() => window.blogEffects.some((effect) => effect.className?.includes('backtop-fade-enter-active')))
  const transitionDuration = await page.locator('.back-to-top').evaluate((button) => getComputedStyle(button).transitionDuration)
  if (transitionDuration === '0s') {
    throw new Error('系统动画偏好禁用了站内过渡：' + reducedMotion)
  }
  const storage = await page.evaluate(() => ({ local: Object.keys(localStorage), session: Object.keys(sessionStorage) }))
  if (storage.local.some((key) => !['dl-blog-visitor', 'dl-blog-session'].includes(key)) || storage.session.length) {
    throw new Error('持久化了登录凭据和统计标识以外的信息')
  }
  if (failures.length || unexpected.length || trackingWarnings.length) {
    throw new Error(JSON.stringify({ failures, unexpected, trackingWarnings }))
  }
  return { passed: true, motionPreference: reducedMotion, originalUi: ['时钟日历', '导航音乐', '分类弹窗', '原文章卡片', '阅读目录', '评论和留言行', 'RSS 弹窗', '移动导航和主题'], runtimeErrors: failures, unexpectedRequests: unexpected, storage }
}
