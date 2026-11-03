// 启动博客 5180、管理端 5178 后，用 playwright-cli run-code --filename Front/Front-Blog/tests/navigation.cjs 执行。
async (page) => {
  page.setDefaultTimeout(10000)
  await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'no-preference' })
  const blog = 'http://127.0.0.1:5180'
  const admin = 'http://127.0.0.1:5178'
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  const failures = []
  page.on('pageerror', error => failures.push(error.message))
  let captchaFails = false
  let providersFail = false
  let providers = []
  let refreshes = 0
  const captchaImage = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 200
    canvas.height = 80
    const context = canvas.getContext('2d')
    context.fillStyle = '#f2f4f7'
    context.fillRect(0, 0, 200, 80)
    context.font = 'bold 30px monospace'
    context.fillStyle = '#434b58'
    context.fillText('123456', 42, 51)
    return canvas.toDataURL('image/png')
  })
  // 所有外部服务均在网络边界隔离，音乐只验证操作和状态，不验证解码或真实播放。
  await page.route('**/*', route => {
    const url = new URL(route.request().url())
    return [blog, admin].includes(url.origin) ? route.continue() : route.abort()
  })
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname
    const ok = data => route.fulfill({ json: { code: 'OK', msg: '', data, errors: [] } })
    const fail = message => route.fulfill({ status: 400, json: { code: 'INVALID_ARGUMENT', msg: message, data: null, errors: [] } })
    if (path.endsWith('/captcha')) {
      refreshes += 1
      return captchaFails ? fail('验证码暂时不可用') : ok({ captchaId: 'a'.repeat(43), expiresIn: 120, imageBase64: captchaImage })
    }
    if (path.endsWith('/oauth/providers')) return providersFail ? fail('登录平台暂时不可用') : ok(providers)
    if (path.endsWith('/online/tickets')) return fail('隔离验收不建立在线连接')
    if (path.endsWith('/profile')) return ok(null)
    if (path.endsWith('/statistics')) return ok({ articleCount: '0', categoryCount: '0', tagCount: '0', pageViewCount: '0', visitorCount: '0', todayPageViewCount: '0' })
    if (path.endsWith('/configs') || path.endsWith('/social-links')) return ok([])
    const records = path.endsWith('/music') ? [
      { musicId: '1', title: '测试曲目一', artist: '虚构歌手', audioUrl: 'https://fixture.invalid/one.mp3', coverUrl: null, lyricUrl: null, lyricType: null },
      { musicId: '2', title: '测试曲目二', artist: null, audioUrl: 'https://fixture.invalid/two.mp3', coverUrl: null, lyricUrl: null, lyricType: null },
    ] : []
    return ok({ page: 1, pageSize: 100, total: String(records.length), totalPages: records.length ? '1' : '0', records })
  })
  const settled = () => page.waitForFunction(() => document.getAnimations().filter(animation => animation.playState === 'running' && animation.effect.getComputedTiming().iterations !== Infinity).length === 0)
  const noOverflow = () => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)

  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.goto(`${blog}/login`)
  await page.getByRole('heading', { name: '账号登录' }).waitFor()
  await page.getByRole('textbox', { name: '六位数字验证码', exact: true }).waitFor({ state: 'visible' })
  await settled()
  check(await page.locator('.auth-divider').count() === 0, '无平台时不应保留第三方登录区块')
  const theme = page.getByRole('checkbox')
  await theme.focus()
  check(await theme.evaluate(element => element === document.activeElement), '主题开关应可聚焦')
  const checked = await theme.isChecked()
  await theme.press('Space')
  check(await theme.isChecked() !== checked, '空格应切换主题')
  await theme.press('Space')
  check(await theme.evaluate(element => getComputedStyle(element.nextElementSibling).outlineStyle !== 'none'), '主题焦点应在可见开关上显示')
  await page.screenshot({ path: 'output/playwright/frontend-polish/blog-desktop.png', fullPage: true })

  const playlist = page.getByRole('button', { name: '播放列表', exact: true })
  await playlist.focus()
  await playlist.press('Enter')
  check(await playlist.getAttribute('aria-expanded') === 'true', '回车应展开播放列表')
  await page.evaluate(() => {
    const audio = document.querySelector('audio')
    audio.load = () => {}
    audio.play = () => { audio.dispatchEvent(new Event('play')); return Promise.resolve() }
  })
  await page.getByRole('button', { name: '播放 测试曲目二', exact: true }).press('Enter')
  await page.waitForFunction(() => document.querySelector('.player-title')?.textContent === '测试曲目二')
  check(await page.getByRole('button', { name: '播放 测试曲目二', exact: true }).getAttribute('aria-pressed') === 'true', '键盘选曲应更新播放状态')
  await playlist.press('Space')
  check(await playlist.getAttribute('aria-expanded') === 'false', '空格应收起播放列表')

  const answer = page.getByRole('textbox', { name: '六位数字验证码', exact: true })
  await answer.fill('123456')
  check(await page.getByRole('button', { name: '登录 →', exact: true }).isEnabled(), '有效验证码应允许登录')
  captchaFails = true
  await page.getByRole('button', { name: '刷新验证码', exact: true }).click()
  await page.getByRole('alert').filter({ hasText: '验证码暂时不可用' }).waitFor()
  check(await answer.inputValue() === '' && await answer.isDisabled(), '刷新失败应丢弃旧答案并禁用输入')
  await page.screenshot({ path: 'output/playwright/frontend-polish/blog-captcha-error.png', fullPage: true })
  captchaFails = false
  await page.getByRole('button', { name: '刷新验证码', exact: true }).click()
  await page.waitForFunction(() => !document.querySelector('.captcha-controls input').disabled)
  check(refreshes >= 3, '失败后应可重新获取验证码')

  await page.setViewportSize({ width: 390, height: 844 })
  const menu = page.getByRole('button', { name: '展开导航' })
  await menu.focus()
  await menu.press('Enter')
  await page.locator('.nav-mobile').waitFor({ state: 'visible' })
  const mobileTheme = page.locator('.nav-mobile').getByRole('button', { name: '深色模式' })
  await mobileTheme.press('Space')
  await page.locator('.nav-mobile').getByRole('button', { name: '浅色模式' }).waitFor()
  await page.screenshot({ path: 'output/playwright/frontend-polish/blog-mobile-menu.png', fullPage: true })
  await page.locator('.nav-mobile').getByRole('button', { name: '搜索', exact: true }).press('Enter')
  const search = page.getByRole('textbox', { name: '搜索文章', exact: true })
  check(await search.evaluate(element => element === document.activeElement), '移动端搜索应打开并聚焦输入框')
  check(await noOverflow(), '窄屏搜索不应产生横向溢出')
  await search.fill('测试')
  await search.press('Enter')
  await page.waitForURL(`${blog}/?search=*`)
  check(new URL(page.url()).searchParams.get('search') === '测试', '键盘搜索应传递关键词')

  await page.setViewportSize({ width: 1600, height: 1000 })
  providersFail = true
  await page.goto(`${blog}/login`)
  await page.getByRole('alert').filter({ hasText: '登录平台暂时不可用' }).waitFor()
  providersFail = false
  providers = [{ source: 'fixture', displayName: '测试平台' }]
  await page.getByRole('button', { name: '重新加载 ↻', exact: true }).click()
  await page.getByRole('button', { name: '测试平台' }).waitFor()
  await settled()
  const transition = await page.locator('.site-header').evaluate(element => getComputedStyle(element).transitionDuration)
  check(transition.split(',').some(value => parseFloat(value) > 0.1), '普通模式应保留页头动效')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  check(await page.locator('.site-header').evaluate(element => getComputedStyle(element).transitionDuration) === transition, '系统关闭动画时仍应保留原有页头动效')
  await page.setViewportSize({ width: 1600, height: 600 })
  await page.evaluate(() => window.scrollTo({ top: 600, behavior: 'instant' }))
  await page.waitForFunction(() => window.scrollY > 400)
  await page.evaluate(() => { window.observedScroll = []; window.scrollTo = options => window.observedScroll.push(options) })
  await page.getByRole('button', { name: '回到顶部' }).click()
  check(await page.evaluate(() => window.observedScroll.at(-1)?.behavior === 'smooth'), '系统关闭动画时返回顶部仍应平滑滚动')

  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`${admin}/login`)
  await page.getByRole('heading', { name: '管理控制台' }).waitFor()
  await page.waitForFunction(() => !document.querySelector('.captcha-controls input').disabled)
  await page.screenshot({ path: 'output/playwright/frontend-polish/admin-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByText('邮箱验证码', { exact: true }).click()
  check(await page.getByRole('radio', { name: '邮箱验证码' }).isChecked(), '点击邮箱登录标签应切换方式')
  await page.getByRole('textbox', { name: '绑定邮箱' }).waitFor()
  check(await noOverflow(), '窄屏邮箱登录不应溢出')
  await settled()
  await page.screenshot({ path: 'output/playwright/frontend-polish/admin-mobile-email.png', fullPage: true })
  check(failures.length === 0, `页面异常：${failures.join('；')}`)
  return '桌面/窄屏导航、键盘主题与选曲、搜索、验证码失败恢复、登录平台失败重试、动效偏好及两端登录截图通过；音频与 API 为替身。'
}
