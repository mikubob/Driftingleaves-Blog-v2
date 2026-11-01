// 仅在 HTTP 边界使用虚构数据，不连接真实账号、数据库或外部服务。
async function smoke(page) {
  await page.unroute('**/api/v1/**')
  await page.setViewportSize({ width: 1280, height: 800 })
  const failures = []
  const discussionFocusChecks = []

  async function checkDiscussionInputs(scope, label) {
    for (const field of await scope.locator('textarea, input:not([type="checkbox"]):not([type="radio"])').all()) {
      if (!await field.isVisible() || await field.isDisabled()) {
        continue
      }
      await field.focus()
      if (!await field.evaluate((element) => {
        const style = getComputedStyle(element)
        return style.outlineStyle === 'none' || style.outlineWidth === '0px'
      })) {
        throw new Error(`${label}出现重复焦点边框`)
      }
      discussionFocusChecks.push(label)
    }
  }
  page.on('pageerror', (error) => failures.push(error.message))
  const revision = '"' + 'a'.repeat(64) + '"'
  const time = '2026-10-31T02:00:00.000Z'
  const id = '9007199254740993'
  const user = {
    userId: id,
    username: 'test_admin',
    nickname: '测试管理员',
    email: null,
    avatar: null,
    intro: null,
    status: 1,
    hasPassword: true,
    lastLoginTime: time,
    createTime: time,
    updateTime: time,
    roleIds: ['1'],
  }
  const pageDefinitions = [
    ['文章管理', '/content/articles', 'content/article/index', 'blog:article', 'document'],
    ['分类管理', '/content/categories', 'content/category/index', 'blog:category', 'folder'],
    ['标签管理', '/content/tags', 'content/tag/index', 'blog:tag', 'tag'],
    ['评论管理', '/content/comments', 'content/comment/index', 'blog:comment', 'chat'],
    ['留言管理', '/content/messages', 'content/message/index', 'blog:message', 'chat'],
    ['友链管理', '/content/links', 'content/link/index', 'blog:link', 'link'],
    ['文件管理', '/content/files', 'content/file/index', 'blog:file', 'picture'],
    ['音乐管理', '/content/music', 'content/music/index', 'blog:music', 'music'],
    ['邮件订阅', '/content/subscriptions', 'content/rss/index', 'blog:rss', 'mail'],
    ['用户管理', '/system/users', 'system/user/index', 'sys:user', 'user'],
    ['角色管理', '/system/roles', 'system/role/index', 'sys:role', 'lock'],
    ['权限管理', '/system/permissions', 'system/permission/index', 'sys:permission', 'key'],
    ['菜单管理', '/system/menus', 'system/menu/index', 'sys:menu', 'menu'],
    ['站点配置', '/system/config', 'system/config/index', 'blog:config', 'setting'],
    ['操作日志', '/system/logs', 'system/log/index', 'sys:log', 'document'],
    ['主页资料', '/home/profile', 'home/profile/index', 'home:profile', 'user'],
    ['社交链接', '/home/social-links', 'home/social/index', 'home:social', 'link'],
    ['简历经历', '/cv/experiences', 'cv/experience/index', 'cv:experience', 'document'],
    ['简历技能', '/cv/skills', 'cv/skill/index', 'cv:skill', 'tag'],
    ['统计报表', '/statistics/overview', 'statistics/overview/index', 'blog:statistics', 'chart'],
    ['访客管理', '/statistics/visitors', 'statistics/visitor/index', 'blog:visitor', 'user'],
    ['浏览记录', '/statistics/views', 'statistics/view/index', 'blog:view', 'document'],
  ]
  const permissions = ['admin:access']
  for (const definition of pageDefinitions) {
    for (const operation of [
      'query',
      'create',
      'update',
      'delete',
      'publish',
      'restore',
      'purge',
      'review',
      'moderate',
      'upload',
      'assignRole',
      'grant',
      'block',
      'unblock',
    ]) {
      permissions.push(`${definition[3]}:${operation}`)
    }
  }
  const nodes = pageDefinitions.map(([menuName, path, component, permission, icon], index) => ({
    menuId: String(index + 10),
    parentId: '1',
    menuCode: `test-${index}`,
    menuName,
    menuType: 'M',
    routeName: `Page${index}`,
    path,
    component,
    icon,
    permissionId: String(index + 10),
    permissionCode: `${permission}:query`,
    sortOrder: index,
    isVisible: 1,
    status: 1,
    keepAlive: component === 'content/category/index' ? 1 : 0,
    children: [],
  }))
  const menus = [
    {
      menuId: '1',
      parentId: null,
      menuName: '测试管理模块',
      menuType: 'D',
      component: 'Layout',
      status: 1,
      isVisible: 1,
      children: nodes,
    },
  ]
  const article = {
    articleId: id,
    authorId: id,
    author: { userId: id, nickname: '测试管理员' },
    category: { categoryId: '1', categoryName: '技术笔记' },
    title: '从一个想法开始：测试文章',
    slug: 'test-article',
    summary: '这是浏览器验证使用的虚构内容。',
    coverUrl: null,
    articleType: 1,
    originalUrl: null,
    status: 0,
    visibility: 1,
    isTop: 1,
    allowComment: 1,
    viewCount: '128',
    likeCount: '12',
    commentCount: '3',
    publishTime: null,
    deletedAt: null,
    createTime: time,
    updateTime: time,
    tags: [],
    categoryId: '1',
    tagIds: [],
    markdownContent: '# 测试正文',
    htmlContent: '<h1>测试正文</h1>',
  }
  const records = {
    '/admin/articles': [article],
    '/admin/categories': [
      {
        categoryId: '1',
        categoryName: '技术笔记',
        slug: 'technology',
        description: '开发与思考',
        sortOrder: 0,
        status: 1,
        createTime: time,
        updateTime: time,
      },
    ],
    '/admin/tags': [
      { tagId: '1', tagName: 'Vue', slug: 'vue', createTime: time, updateTime: time },
    ],
    '/admin/comments': [
      {
        commentId: '1',
        articleId: id,
        userId: id,
        parentId: null,
        content: '一条用于验证审核的测试评论',
        status: 0,
        author: { nickname: '测试读者' },
        articleTitle: article.title,
        createTime: time,
        updateTime: time,
      },
    ],
    '/admin/messages': [
      {
        messageId: '1',
        parentId: null,
        author: { userId: id, nickname: '测试读者' },
        content: '你好，这是虚构的测试留言。',
        htmlContent: '<p>测试留言</p>',
        contentFormat: 0,
        isSecret: 1,
        status: 0,
        editedAt: null,
        isOwn: false,
        notifyReply: 0,
        createTime: time,
        updateTime: time,
      },
    ],
    '/admin/links': [
      {
        linkId: '1',
        linkName: '测试友链',
        url: 'https://example.com',
        avatar: null,
        description: null,
        sortOrder: 0,
        status: 1,
        updateTime: time,
      },
    ],
    '/admin/files': [
      {
        fileId: '1',
        uploaderId: id,
        originalName: 'test-image.png',
        mimeType: 'image/png',
        sizeBytes: '128',
        storageType: 'oss',
        previewUrl: '/api/v1/admin/files/1/content',
        createTime: time,
      },
    ],
    '/admin/music': [
      {
        musicId: '1',
        title: '测试音乐',
        artist: '测试歌手',
        durationSeconds: null,
        coverUrl: null,
        audioUrl: 'https://example.com/audio.mp3',
        lyricUrl: null,
        lyricType: null,
        sortOrder: 0,
        status: 1,
        updateTime: time,
      },
    ],
    '/admin/rss-subscriptions': [
      {
        subscriptionId: '1',
        userId: id,
        email: 'test@example.com',
        status: 1,
        verifiedAt: time,
        subscribeTime: time,
        unsubscribeTime: null,
        updateTime: time,
      },
    ],
    '/admin/users': [{ ...user, lastLoginIp: '192.0.2.42', lastLoginLocation: '江苏省 南京市' }],
    '/admin/roles': [
      {
        roleId: '1',
        roleCode: 'test_admin',
        roleName: '测试角色',
        description: null,
        dataScope: 'ALL',
        status: 1,
        permissionIds: ['1'],
        menuIds: ['1'],
        updateTime: time,
      },
    ],
    '/admin/permissions': [
      {
        permissionId: '1',
        permissionCode: 'admin:access',
        permissionName: '测试管理入口',
        module: 'admin',
        description: null,
        status: 1,
        updateTime: time,
      },
    ],
    '/admin/operation-logs': [
      {
        logId: '1',
        userId: id,
        operationType: 'update',
        operationTarget: 'blog:article',
        targetId: id,
        permissionCode: 'blog:article:update',
        result: 1,
        errorMessage: null,
        ipAddress: '192.0.2.0/24',
        operationTime: time,
        operationData: { title: '测试文章' },
      },
    ],
    '/admin/home/social-links': [
      {
        socialId: '1',
        name: '测试链接',
        icon: null,
        url: 'https://example.com',
        sortOrder: 0,
        status: 1,
        updateTime: time,
      },
    ],
    '/admin/cv/experiences': [
      {
        experienceId: '1',
        experienceType: 0,
        title: '测试大学',
        subtitle: '测试专业',
        logoUrl: null,
        content: '测试经历',
        startDate: '2020-09-01',
        endDate: null,
        sortOrder: 0,
        status: 1,
        updateTime: time,
      },
    ],
    '/admin/cv/skills': [
      {
        skillId: '1',
        name: 'Vue',
        description: '测试技能',
        icon: null,
        sortOrder: 0,
        status: 1,
        updateTime: time,
      },
    ],
    '/admin/visitors': [
      {
        visitorId: '1',
        fingerprint: 'test-fingerprint',
        ipAddress: '192.0.2.42',
        userAgent: 'Test Browser',
        country: '中国',
        province: '浙江',
        city: '杭州',
        isBlocked: 0,
        blockedUntil: null,
        firstVisitTime: time,
        lastVisitTime: time,
        updateTime: time,
      },
    ],
    '/admin/page-views': [
      {
        viewId: '1',
        visitorId: '1',
        userId: null,
        articleId: id,
        siteCode: 'blog',
        pagePath: '/articles/test-article',
        pageTitle: '测试文章',
        referer: null,
        ipAddress: '192.0.2.42',
        userAgent: 'Test Browser',
        viewTime: time,
      },
    ],
  }
  const writes = []
  const unexpected = []
  let conflict = false
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace('/api/v1', '')
    const respond = async (data, status = 200, code = 'OK') =>
      route.fulfill({
        status,
        contentType: 'application/json',
        headers: { 'X-Revision': revision },
        body: JSON.stringify({ code, msg: status < 400 ? '成功' : '测试错误', data, errors: [] }),
      })
    if (path === '/admin/auth/captcha') {
      return respond({ captchaId: 'a'.repeat(43), imageBase64: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', expiresIn: 120 })
    }
    if (req.method() !== 'GET') {
      const body = req.postDataJSON()
      writes.push({ path, method: req.method(), body, headers: req.headers() })
      if (['/admin/auth/login', '/admin/auth/email-code'].includes(path) || /\/replies$/.test(path)) {
        if (req.headers()['x-captcha-id'] !== 'a'.repeat(43) || req.headers()['x-captcha-code'] !== '012345') {
          throw new Error('缺少正确的验证码请求头')
        }
      }
      if (path === '/admin/auth/email-code') {
        return respond(null)
      }
      if (path === '/admin/auth/login') {
        if (body.email !== 'admin@example.test' || body.emailCode !== '012345') {
          throw new Error('后台登录没有同时提交绑定邮箱和邮件验证码')
        }
        return respond({
          tokenType: 'Bearer',
          accessToken: 'browser-test-token',
          expiresIn: 604800,
          user,
        })
      }
      if (path === '/admin/auth/oauth/TEST/authorize') {
        return respond({
          authorizationUrl: 'http://127.0.0.1:5176/oauth/complete#ticket=browser-bind-ticket',
          expiresIn: 300,
        })
      }
      if (path === '/admin/auth/oauth/exchange') {
        return respond({ operation: 'BIND', binding: { source: 'TEST', createTime: time } })
      }
      if (path === '/admin/categories/1' && conflict) {
        return respond(null, 412, 'REVISION_CONFLICT')
      }
      if (path.startsWith('/admin/articles')) {
        return respond({ ...article, ...body, articleId: id })
      }
      return respond(records[path]?.[0] || null)
    }
    if (path === '/admin/auth/oauth/providers') {
      return respond([{ source: 'TEST', displayName: '测试平台' }])
    }
    if (path === '/admin/me/oauth-bindings') {
      return respond([])
    }
    if (path === '/admin/me') {
      return respond(user)
    }
    if (path === '/admin/me/capabilities') {
      return respond({
        roleCodes: ['test_admin'],
        permissionCodes: permissions,
        dataScopes: Object.fromEntries(permissions.map((code) => [code, 'ALL'])),
      })
    }
    if (path === '/admin/me/menus' || path === '/admin/menus') {
      return respond(menus)
    }
    if (path === '/admin/home/profile') {
      return respond({
        profileId: '1',
        userId: id,
        nickname: '测试站点主人',
        tagline: '测试标语',
        description: null,
        avatar: null,
        website: null,
        publicEmail: null,
        githubUrl: null,
        location: null,
        updateTime: time,
      })
    }
    if (path.startsWith('/admin/configs/')) {
      const configKey = path.split('/').at(-1)
      return respond({
        configKey,
        configValue:
          configKey === 'site'
            ? {
                title: '测试站点',
                description: '',
                icp: '',
                publicSecurityRecord: '',
                startDate: null,
              }
            : { enabled: true, reviewRequired: true },
        description: null,
        isPublic: configKey === 'site' ? 1 : 0,
        updateTime: time,
      })
    }
    if (path.startsWith('/admin/statistics/')) {
      const key = path.split('/').at(-1)
      if (key === 'overview') {
        return respond({
          pageViewCount: '1248',
          visitorCount: '256',
          newVisitorCount: '48',
          articleCount: '24',
          publishedArticleCount: '18',
          commentCount: '36',
          messageCount: '12',
          pendingCommentCount: '3',
          pendingMessageCount: '2',
        })
      }
      if (key === 'provinces') {
        return respond([
          { province: '浙江', visitorCount: '128' },
          { province: '上海', visitorCount: '84' },
        ])
      }
      if (key === 'articles') {
        return respond([
          { articleId: id, title: article.title, slug: article.slug, pageViewCount: '128' },
        ])
      }
      return respond(
        [1, 2, 3, 4, 5, 6, 7].map((day) => ({
          date: `2026-10-${24 + day}`,
          pageViewCount: String(day * 32),
          visitorCount: String(day * 8),
          newVisitorCount: String(day * 4),
          cumulativeNewVisitorCount: String(day * 5),
        })),
      )
    }
    if (records[path]) {
      return respond({
        page: Number(url.searchParams.get('page') || 1),
        pageSize: Number(url.searchParams.get('pageSize') || 10),
        total: String(records[path].length),
        totalPages: '1',
        records: records[path],
      })
    }
    const parent = path.replace(/\/[^/]+$/, '')
    if (records[parent]) {
      return respond(records[parent][0])
    }
    unexpected.push(`${req.method()} ${path}`)
    return respond(null, 404, 'NOT_FOUND')
  })

  await page.goto('http://127.0.0.1:5176/login')
  await page.getByRole('heading', { name: '管理控制台', exact: true }).waitFor()
  if (!(await page.locator('.login-decoration').isVisible()) || await page.locator('.floating-leaves').count()) {
    throw new Error('登录页未保留当前项目设计')
  }
  await page.screenshot({ path: 'output/playwright/login.png', fullPage: true })
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill('test_admin')
  await page.getByRole('textbox', { name: '登录密码', exact: true }).fill('  fake-pass  ')
  await page.getByLabel('绑定邮箱', { exact: true }).fill('admin@example.test')
  await page.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '发送邮箱验证码', exact: true }).click()
  await page.getByRole('status').filter({ hasText: '验证码已发送' }).waitFor()
  if (await page.getByLabel('六位数字验证码', { exact: true }).inputValue()) {
    throw new Error('发送邮件后未刷新图形验证码')
  }
  await page.getByLabel('邮箱验证码', { exact: true }).fill('012345')
  await page.getByLabel('六位数字验证码', { exact: true }).fill('012345')
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL('**/statistics/overview')
  await page.getByText('1,248', { exact: true }).waitFor()
  await page.evaluate(() => document.fonts.ready)
  const font = await page.locator('nav .iconfont').first().evaluate((icon) => getComputedStyle(icon).fontFamily)
  if (!font.includes('iconfont')) {
    throw new Error('导航未使用老项目图标字体')
  }
  const menuIcons = await page.locator('.sidebar a').evaluateAll((links) => links.map((link) => {
    const icon = link.querySelector('.iconfont, .el-icon')
    return icon?.querySelector('svg')?.innerHTML || getComputedStyle(icon, '::before').content
  }))
  if (new Set(menuIcons).size !== 23) {
    throw new Error('侧边栏入口图标重复或缺失')
  }
  await page.getByRole('button', { name: '收起侧栏' }).click()
  await page.waitForFunction(() => document.querySelector('.sidebar').getBoundingClientRect().width === 60)
  if (await page.locator('nav .nav-label').first().isVisible()) {
    throw new Error('折叠后导航文字未隐藏')
  }
  await page.getByRole('button', { name: '展开侧栏' }).click()
  await page.waitForFunction(() => document.querySelector('.sidebar').getBoundingClientRect().width === 220)
  await page.screenshot({ path: 'output/playwright/dashboard.png', fullPage: true })
  if (writes[0].body.password !== '  fake-pass  ') {
    throw new Error('登录密码被修改')
  }
  for (const [title, path] of pageDefinitions) {
    await page.locator(`nav a[href="${path}"]`).click()
    await page.waitForURL(`**${path}`)
    await page.locator('main h1').first().waitFor()
    await page.locator('.el-loading-mask').waitFor({ state: 'hidden' })
    if (await page.locator('.request-error').count()) {
      throw new Error(`${title}加载失败：${await page.locator('.request-error').innerText()}`)
    }
    if (path === '/content/comments' || path === '/content/messages') {
      await checkDiscussionInputs(page.locator('.filter-form'), `${title}筛选框`)
      await page.keyboard.press('Escape')
    }
    if (path === '/content/messages') {
      await page.getByRole('button', { name: '回复', exact: true }).click()
      const replyDialog = page.getByRole('dialog', { name: '回复留言', exact: true })
      await replyDialog.waitFor()
      await page.waitForFunction(() => !document.querySelector('.el-dialog .captcha-controls input').disabled)
      await checkDiscussionInputs(replyDialog, '后台留言回复框')
      await replyDialog.getByRole('button', { name: '取消', exact: true }).click()
    }
  }
  await page.getByRole('button', { name: '详情', exact: true }).click()
  const viewDialog = page.getByRole('dialog', { name: '浏览记录详情', exact: true })
  await viewDialog.waitFor()
  const viewTime = await page.evaluate((value) => new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value)), time)
  const expectedViewDetails = {
    '记录 ID': '1', '访客 ID': '1', '用户 ID': '—', '文章 ID': id,
    '页面标题': '测试文章', '站点': 'blog', '页面路径': '/articles/test-article',
    '来源': '—', 'IP 地址': '192.0.2.42', 'User-Agent': 'Test Browser', '访问时间': viewTime,
  }
  for (const [label, expected] of Object.entries(expectedViewDetails)) {
    const item = viewDialog.locator('tr').filter({ has: page.getByText(label, { exact: true }) })
    const actual = await item.locator('.el-descriptions__content').innerText()
    if (actual !== expected) {
      throw new Error(`浏览记录详情字段 ${label} 显示错误：${actual}`)
    }
  }
  await page.keyboard.press('Escape')
  await viewDialog.waitFor({ state: 'hidden' })
  await page.locator('nav a[href="/system/users"]').click()
  await page.getByRole('cell', { name: '192.0.2.42', exact: true }).waitFor()
  await page.getByRole('cell', { name: '江苏省 南京市', exact: true }).waitFor()
  await page.locator('nav a[href="/statistics/visitors"]').click()
  await page.getByRole('button', { name: '详情', exact: true }).click()
  const visitorDialog = page.getByRole('dialog', { name: '访客详情', exact: true })
  await visitorDialog.getByText('192.0.2.42', { exact: true }).waitFor()
  await visitorDialog.getByText('中国 / 浙江 / 杭州', { exact: true }).waitFor()
  await page.keyboard.press('Escape')
  await visitorDialog.waitFor({ state: 'hidden' })
  const editPages = [
    ['/content/categories', '保存分类'],
    ['/content/tags', '保存标签'],
    ['/content/links', '保存友链'],
    ['/content/music', '保存音乐'],
    ['/system/users', '保存用户'],
    ['/system/roles', '保存角色'],
    ['/system/permissions', '保存权限'],
    ['/home/social-links', '保存社交链接'],
    ['/cv/experiences', '保存简历经历'],
    ['/cv/skills', '保存简历技能'],
  ]
  await page.locator('nav a[href="/content/categories"]').click()
  await page.getByRole('textbox', { name: '分类关键词' }).fill('缓存检查')
  await page.locator('nav a[href="/content/tags"]').click()
  await page.getByRole('heading', { name: '标签管理', exact: true }).waitFor()
  await page.locator('nav a[href="/content/categories"]').click()
  if (await page.getByRole('textbox', { name: '分类关键词' }).inputValue() !== '缓存检查') {
    throw new Error('页面切换动画破坏了 keepAlive 状态')
  }
  await page.getByRole('textbox', { name: '分类关键词' }).fill('')
  for (const [path, button] of editPages) {
    await page.locator(`nav a[href="${path}"]`).click()
    await page.waitForFunction(() => !document.querySelector('.page-fade-enter-active, .page-fade-leave-active'))
    await page.getByRole('button', { name: '编辑', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.waitFor()
    await dialog.getByRole('button', { name: button, exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
  }
  await page.locator('a[href="/account"]').first().click()
  await page.getByRole('heading', { name: '个人资料与安全' }).waitFor()
  const popupPromise = page.waitForEvent('popup')
  await page.getByRole('button', { name: '绑定账号', exact: true }).click()
  const bindingPopup = await popupPromise
  await page.getByText('第三方账号已绑定', { exact: true }).waitFor()
  const bindingWrite = writes.find((write) => write.path === '/admin/auth/oauth/exchange')
  if (
    !bindingWrite ||
    bindingWrite.headers.authorization !== 'Bearer browser-test-token' ||
    bindingWrite.body.ticket !== 'browser-bind-ticket'
  ) {
    throw new Error('绑定兑换未保留原会话或票据不匹配')
  }
  if (!bindingPopup.isClosed()) {
    await bindingPopup.close()
  }
  await page.locator('nav a[href="/content/articles"]').click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByRole('heading', { name: '编辑文章' }).waitFor()
  await page.getByRole('textbox', { name: '文章标题', exact: true }).fill('浏览器测试保存的文章')
  await page.getByRole('button', { name: '保存文章', exact: true }).click()
  await page.getByText('文章已保存', { exact: true }).waitFor()
  const articleWrite = writes.find(
    (write) => write.path === `/admin/articles/${id}` && write.method === 'PUT',
  )
  if (
    !articleWrite ||
    articleWrite.headers['if-revision'] !== revision ||
    articleWrite.body.categoryId !== '1' ||
    Object.hasOwn(articleWrite.body, 'authorId') ||
    Object.hasOwn(articleWrite.body, 'htmlContent')
  ) {
    throw new Error('文章保存请求不符合契约')
  }
  await page.locator('nav a[href="/content/categories"]').click()
  await page.waitForFunction(() => !document.querySelector('.page-fade-enter-active, .page-fade-leave-active'))
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.getByRole('textbox', { name: '分类名称', exact: true }).fill('保留冲突后的修改')
  conflict = true
  await page.getByRole('button', { name: '保存分类', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '内容已被其他人修改' }).waitFor()
  if (
    (await page.getByRole('textbox', { name: '分类名称', exact: true }).inputValue()) !==
    '保留冲突后的修改'
  ) {
    throw new Error('版本冲突丢失了输入')
  }
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.locator('nav a[href="/content/articles"]').click()
  await page.screenshot({ path: 'output/playwright/articles.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '打开导航' }).click()
  await page.locator('nav a[href="/statistics/overview"]').click()
  await page.waitForURL('**/statistics/overview')
  await page.locator('.sidebar-scrim').waitFor({ state: 'hidden' })
  await page.getByText('1,248', { exact: true }).waitFor()
  await page.waitForFunction(() => !document.querySelector('.page-fade-enter-active, .page-fade-leave-active'))
  await page.screenshot({ path: 'output/playwright/mobile.png', fullPage: true })
  const storage = await page.evaluate(() => ({
    local: { ...localStorage },
    session: { ...sessionStorage },
    width: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
  }))
  const savedSession = JSON.parse(storage.local['dl-admin-session'] || 'null')
  if (savedSession?.accessToken !== 'browser-test-token' || storage.width > storage.viewport) {
    throw new Error('登录凭据未持久化或移动端页面溢出')
  }
  await page.evaluate(async () => {
    const router = document.querySelector('#app').__vue_app__.config.globalProperties.$router
    await router.push('/missing-admin-page')
  })
  await page.getByRole('heading', { name: '404', exact: true }).waitFor()
  if (await page.locator('.sidebar').count()) {
    throw new Error('未知路由未显示全屏 404')
  }
  await page.reload()
  await page.getByRole('heading', { name: '404', exact: true }).waitFor()
  await page.goto('http://127.0.0.1:5176/statistics/overview')
  await page.getByText('1,248', { exact: true }).waitFor()
  if (await page.evaluate(() => localStorage.getItem('dl-admin-session')) !== storage.local['dl-admin-session']) {
    throw new Error('刷新后延长了管理端会话有效期')
  }
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '退出', exact: true }).click()
  await page.waitForURL('**/login')
  await page.getByRole('button', { name: '发送邮箱验证码', exact: true }).waitFor()
  if (await page.locator('.oauth-buttons').count()) {
    throw new Error('后台仍提供第三方直接登录入口')
  }
  for (const failure of [429, 503, 'network']) {
    let requests = 0
    await page.route('**/api/v1/admin/auth/captcha', async (route) => {
      requests += 1
      if (failure === 'network') {
        return route.abort('failed')
      }
      return route.fulfill({
        status: failure,
        contentType: 'text/plain',
        headers: { 'Retry-After': '17' },
        body: '测试服务异常',
      })
    })
    await page.reload()
    await page.getByRole('heading', { name: '404', exact: true }).waitFor()
    const message = await page.locator('.error-desc').innerText()
    if (requests !== 1 || message !== '你访问的页面已经走丢了') {
      throw new Error(`异常请求被重试或错误页泄露接口错误：${JSON.stringify({ failure, requests, message })}`)
    }
    if (await page.title() !== '404 - 页面不存在') {
      throw new Error('异常页标题不正确')
    }
    await page.screenshot({ path: `output/playwright/error-${failure}.png`, fullPage: true })
    await page.unroute('**/api/v1/admin/auth/captcha')
  }
  await page.getByRole('link', { name: '返回首页' }).click()
  await page.waitForURL('**/login')
  await page.getByRole('textbox', { name: '用户名', exact: true }).waitFor()
  if (unexpected.length || failures.length) {
    throw new Error(JSON.stringify({ unexpected, failures }))
  }
  return {
    pages: pageDefinitions.length,
    discussionFocusChecks: discussionFocusChecks.length,
    articleSaved: true,
    conflictPreserved: true,
    mobileWidth: storage.width,
    refreshRestoresLogin: true,
    unexpectedRequests: unexpected,
    runtimeErrors: failures,
    editFormsSaved: editPages.length,
    bindingExchangedWithOriginalSession: true,
    loginDesignPreserved: true,
    iconFontLoaded: true,
    distinctSidebarIcons: menuIcons.length,
    collapseAndKeepAlivePreserved: true,
    fullScreen404: ['unknown-route', 429, 503, 'network'],
  }
}
