// 只在 HTTP 边界提供虚构验证码和平台列表，挂载两端真实登录组件。
async function authFocusSmoke(page) {
  const failures = []
  const checked = []
  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    let data
    if (pathname.endsWith('/auth/captcha')) {
      data = {
        captchaId: 'a'.repeat(43), expiresIn: 120,
        imageBase64: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',
      }
    } else if (pathname.endsWith('/auth/oauth/providers')) {
      data = []
    } else {
      throw new Error(`焦点检查出现意外请求：${pathname}`)
    }
    await route.fulfill({ json: { code: 'OK', msg: '成功', errors: [], data } })
  })
  await page.route('**/auth-focus-fixture', (route) => route.fulfill({
    contentType: 'text/html',
    body: '<html lang="zh-CN"><meta charset="utf-8"><body><div id="app"></div></body></html>',
  }))

  async function checkInputs(label) {
    await page.getByLabel('六位数字验证码', { exact: true }).waitFor({ state: 'visible' })
    await page.waitForFunction(() => !document.querySelector('.captcha-controls input').disabled)
    for (const input of await page.locator('#app input:not([type="checkbox"])').all()) {
      await input.focus()
      const style = await input.evaluate((element) => {
        const style = getComputedStyle(element)
        const wrapper = element.closest('.el-input')?.querySelector('.el-input__wrapper')
        return {
          outline: style.outlineStyle, width: style.outlineWidth,
          name: element.id || element.placeholder,
          focusVisible: wrapper
            ? wrapper.classList.contains('is-focus') && getComputedStyle(wrapper).boxShadow !== 'none'
            : style.borderTopStyle === 'solid' && style.borderTopColor === style.color,
        }
      })
      if (style.outline !== 'none' && style.width !== '0px') {
        failures.push({ label, ...style })
      }
      if (!style.focusVisible) {
        throw new Error(`${label}/${style.name}未保留可见焦点边框`)
      }
      checked.push(`${label}/${style.name}`)
    }
  }

  for (const site of ['admin', 'blog']) {
    const port = site === 'admin' ? 5176 : 5174
    await page.goto(`http://127.0.0.1:${port}/auth-focus-fixture`)
    await page.evaluate(async (site) => {
      const { createApp, h } = await import('/node_modules/.vite/deps/vue.js')
      const { createRouter, createMemoryHistory } = await import('/node_modules/.vite/deps/vue-router.js')
      await import('/node_modules/element-plus/dist/index.css')
      const { default: Login } = await import('/src/views/Login.vue')
      const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Login }] })
      const app = createApp({ render: () => h('div', { class: site === 'blog' ? 'v2-page' : '' }, [h(Login)]) })
      if (site === 'admin') {
        const { default: ElementPlus } = await import('/node_modules/.vite/deps/element-plus.js')
        await import('/src/assets/styles/main.css')
        await import('/src/style.css')
        app.use(ElementPlus)
      } else {
        await import('/src/assets/styles/main.scss')
        await import('/src/assets/styles/account.scss')
      }
      app.use(router).mount('#app')
    }, site)
    await checkInputs(`${site}/账号密码登录`)
    if (site === 'blog') {
      await page.getByRole('button', { name: '邮箱验证码', exact: true }).click()
      await checkInputs('blog/邮箱登录')
      await page.getByRole('button', { name: '注册账号', exact: true }).click()
      for (const dark of [false, true]) {
        await page.evaluate((dark) => document.documentElement.classList.toggle('dark', dark), dark)
        await checkInputs(`blog/注册/${dark ? '深色' : '浅色'}`)
      }
    }
    await page.screenshot({ path: `output/playwright/${site}-auth-focus.png`, fullPage: true })
  }
  if (failures.length) {
    throw new Error(`登录输入框出现重复焦点边框：${JSON.stringify(failures)}`)
  }
  return { checkedInputs: checked.length, loginModes: ['后台登录', '博客账密登录', '博客邮箱登录', '博客注册'], blogThemes: ['浅色', '深色'] }
}
