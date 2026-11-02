// 启动 pnpm dev --port 5177 后，用 playwright-cli run-code --filename tests/image-preview.cjs 执行。
async (page) => {
  page.setDefaultTimeout(10000)
  const origin = 'http://127.0.0.1:5177'
  const external = 'https://preview.example/icon-without-extension'
  const requests = []
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80"><rect width="120" height="80" fill="#4479a1"/><text x="12" y="48" fill="white" font-size="24">MySQL</text></svg>'
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5xkAAAAASUVORK5CYII=', 'base64')
  await page.route(`${origin}/__image-preview-check`, route => route.fulfill({
    contentType: 'text/html',
    body: '<html style="height:100%"><head><link rel="stylesheet" href="/node_modules/element-plus/dist/index.css"><link rel="stylesheet" href="/src/assets/styles/main.css"></head><body style="height:100%"><div id="fixture" style="height:100%"></div></body></html>',
  }))
  await page.route('https://preview.example/**', async route => {
    requests.push({ url: route.request().url(), authorization: route.request().headers().authorization })
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    requests.push({ url: request.url(), authorization: request.headers().authorization })
    if (path.endsWith('/content')) {
      if (path.includes('/files/3/')) {
        await route.fulfill({ status: 403, json: { code: 'FORBIDDEN', msg: '无权读取图片', data: null, errors: [] } })
      } else {
        await route.fulfill({ contentType: 'image/png', body: png })
      }
      return
    }
    let data = { page: 1, pageSize: 100, total: '0', totalPages: '0', records: [] }
    if (path.endsWith('/home/profile')) {
      data = null
    } else if (path.endsWith('/admin/me')) {
      data = { userId: '1', nickname: '预览测试', email: null, avatar: null, intro: null }
    } else if (path.includes('/auth/') || path.endsWith('/bindings')) {
      data = []
    }
    await route.fulfill({ headers: { 'X-Revision': '"preview-test"' }, json: { code: 'OK', msg: '', data, errors: [] } })
  })
  await page.goto(`${origin}/__image-preview-check`)
  await page.evaluate(async () => {
    const editor = await (await fetch('/src/views/ArticleEditor.vue')).text()
    const dependency = name => editor.match(new RegExp(`from "([^"]*/${name}\\.js[^"]*)"`))[1]
    window.vueDependency = dependency('vue')
    window.routerDependency = dependency('vue-router')
    window.elementDependency = dependency('element-plus')
    const { createApp, h, ref, nextTick } = await import(window.vueDependency)
    const { default: Preview } = await import('/src/components/ImagePreview.vue')
    const { setAuthorization } = await import('/src/api.js')
    setAuthorization('fixture-token')
    window.revoked = []
    const revoke = URL.revokeObjectURL.bind(URL)
    URL.revokeObjectURL = url => { window.revoked.push(url); revoke(url) }
    window.previewSource = ref('')
    window.testApp = createApp({ setup: () => () => h(Preview, { src: window.previewSource.value }) })
    window.testApp.mount('#fixture')
    window.tick = nextTick
  })
  const setSource = src => page.evaluate(async value => {
    window.previewSource.value = value
    await window.tick()
  }, src)
  const loaded = () => page.waitForFunction(() => document.querySelector('#fixture .image-preview img')?.naturalWidth > 0)
  const check = (condition, message) => { if (!condition) throw new Error(message) }

  await setSource(external)
  await loaded()
  check(!requests.at(-1).authorization, '外链不能携带 Token')
  await setSource('https://preview.example/second.svg')
  await loaded()
  check(await page.locator('.image-preview img').getAttribute('src') === 'https://preview.example/second.svg', '修改地址后应换图')
  for (const src of ['', 'document', 'javascript:alert(1)', 'data:image/svg+xml,test']) {
    await setSource(src)
    check(await page.locator('.image-preview').count() === 0, '空值、图标键及不支持的协议不应请求图片')
  }
  for (const [src, path] of [
    ['/api/v1/blog/articles/1/files/2/content', '/api/v1/admin/articles/1/files/2/content'],
    ['/api/v1/blog/music/files/2/content', '/api/v1/admin/files/2/content'],
  ]) {
    await setSource(src)
    await loaded()
    check(requests.at(-1).url.endsWith(path) && requests.at(-1).authorization === 'Bearer fixture-token', '附件应通过后台接口鉴权')
    const blob = await page.locator('.image-preview img').getAttribute('src')
    await setSource(external)
    await loaded()
    check(await page.evaluate(url => window.revoked.includes(url), blob), '换图应释放 Blob URL')
  }
  await setSource('/api/v1/admin/files/3/content')
  await page.getByRole('status').filter({ hasText: '无权读取图片' }).waitFor()
  await setSource(external)
  await loaded()
  await page.route('https://preview.example/broken', route => route.fulfill({ contentType: 'text/plain', body: 'not an image' }))
  await setSource('https://preview.example/broken')
  await page.getByRole('status').filter({ hasText: '图片加载失败' }).waitFor()
  await setSource('/api/v1/admin/files/2/content')
  await loaded()
  const finalBlob = await page.locator('.image-preview img').getAttribute('src')
  await page.evaluate(() => window.testApp.unmount())
  check(await page.evaluate(url => window.revoked.includes(url), finalBlob), '卸载应释放 Blob URL')

  // 挂载真实页面，仅在 HTTP 边界使用虚构数据，检查全部图片输入。
  for (const [name, field] of [
    ['Account', 'avatar'], ['Users', 'avatar'], ['Links', 'avatar'], ['HomeProfile', 'avatar'],
    ['Experiences', 'logoUrl'], ['Skills', 'icon'], ['Socials', 'icon'], ['Music', 'coverUrl'],
    ['ArticleEditor', 'coverUrl'],
  ]) {
    await page.evaluate(async ({ name, field }) => {
      const { createApp, nextTick } = await import(window.vueDependency)
      const { default: ElementPlus } = await import(window.elementDependency)
      const { createRouter, createMemoryHistory } = await import(window.routerDependency)
      const { session } = await import('/src/session.js')
      session.capabilities.permissionCodes = ['blog:article:create']
      const { default: View } = await import(`/src/views/${name}.vue`)
      const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: View }] })
      await router.push('/')
      await router.isReady()
      window.testApp = createApp(View).use(ElementPlus).use(router)
      window.testView = window.testApp.mount('#fixture')
      const state = window.testView.$.setupState
      if ('dialog' in state) state.dialog = true
      await nextTick()
      window.testField = field
    }, { name, field })
    await page.waitForFunction(() => !window.testView.$.setupState.loading)
    await page.evaluate(async ({ external }) => {
      window.testView.$.setupState.form[window.testField] = external
      await window.tick()
    }, { external })
    await page.waitForFunction(() => [...document.querySelectorAll('.image-preview img')].some(img => img.naturalWidth > 0))
    const input = await page.locator('label').filter({ has: page.locator('.image-preview') }).locator('input').last().elementHandle()
    await input.fill('https://preview.example/updated.svg')
    await page.waitForFunction(() => document.querySelector('.image-preview img')?.getAttribute('src') === 'https://preview.example/updated.svg')
    await input.fill('')
    check(await page.locator('.image-preview').count() === 0, `${name} 清空地址后应移除预览`)
    if (name === 'ArticleEditor') {
      await page.getByText('点击上传封面', { exact: true }).waitFor()
      await input.fill(external)
      await page.waitForFunction(() => document.querySelector('.cover-preview img')?.naturalWidth > 0)
      await page.screenshot({ path: 'output/playwright/image-preview-article.png' })
    }
    await page.evaluate(() => window.testApp.unmount())
    console.log(`${name}: 图片输入预览通过`)
  }
  return '外链、SVG、换图、清空、图标键、失败恢复、附件鉴权、Blob 清理及全部 9 个表单验证通过'
}
