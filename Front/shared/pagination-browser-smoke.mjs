// 用 Vite 加载真实组件，仅在 HTTP 请求处提供虚构分页数据。
async function paginationSmoke(page) {
  const failures = []
  page.on('pageerror', (error) => failures.push(error.message))
  page.setDefaultTimeout(10000)
  await page.setViewportSize({ width: 1280, height: 800 })
  let total = '120'
  const calls = []
  await page.route('**/api/v1/pagination-test**', async (route) => {
    const query = new URL(route.request().url()).searchParams
    const current = Number(query.get('page'))
    const pageSize = Number(query.get('pageSize'))
    calls.push({ page: current, pageSize })
    await route.fulfill({ json: { code: 'OK', errors: [], data: {
      page: current, pageSize, total,
      totalPages: String((BigInt(total) + BigInt(pageSize) - 1n) / BigInt(pageSize)),
      records: [],
    } } })
  })
  await page.route('**/pagination-fixture', (route) => route.fulfill({
    contentType: 'text/html',
    body: '<html lang="zh-CN"><meta charset="utf-8"><body><div id="app"></div></body></html>',
  }))
  await page.goto('http://127.0.0.1:5176/pagination-fixture')
  await page.evaluate(async () => {
    const { createApp, h } = await import('/node_modules/.vite/deps/vue.js')
    const { default: ElementPlus } = await import('/node_modules/.vite/deps/element-plus.js')
    const { default: zhCn } = await import('/node_modules/element-plus/es/locale/lang/zh-cn.mjs')
    const { default: ListPager } = await import('/src/components/ListPager.vue')
    const { useList } = await import('/src/composables/useList.js')
    await import('/node_modules/element-plus/dist/index.css')
    await import('/src/style.css')
    createApp({
      setup() {
        const list = useList('/pagination-test')
        window.paginationList = list
        list.load()
        return () => h(ListPager, {
          page: list.page.value, pageSize: list.pageSize.value, total: list.total.value,
          totalPages: list.totalPages.value, loading: list.loading.value,
          onChange: list.changePage, onSizeChange: list.changePageSize,
        })
      },
    }).use(ElementPlus, { locale: zhCn }).mount('#app')
  })
  const active = page.locator('.el-pager .is-active')
  await active.filter({ hasText: /^\s*1\s*$/ }).waitFor()
  await page.locator('.el-pager li.number').filter({ hasText: /^\s*3\s*$/ }).click()
  await page.waitForFunction(() => !window.paginationList.loading.value)
  if (calls.at(-1).page !== 3) throw new Error('后台数字翻页没有请求第三页')
  const beforeSizeChange = calls.length
  await page.locator('.el-pagination__sizes .el-select').click()
  await page.getByRole('option', { name: '20条/页', exact: true }).click()
  await active.filter({ hasText: /^\s*1\s*$/ }).waitFor()
  await page.waitForFunction(() => !window.paginationList.loading.value)
  if (calls.length !== beforeSizeChange + 1 || calls.at(-1).page !== 1 || calls.at(-1).pageSize !== 20) {
    throw new Error('更改条数必须只请求一次第一页')
  }
  const jumper = page.locator('.el-pagination__jump input')
  await jumper.fill('4')
  await jumper.press('Enter')
  await active.filter({ hasText: /^\s*4\s*$/ }).waitFor()
  await page.waitForFunction(() => !window.paginationList.loading.value)
  if (calls.at(-1).page !== 4 || calls.at(-1).pageSize !== 20) throw new Error('跳页丢失了条数')

  // 空结果仍可调整条数；超大总数保留全部数字，页码遵守接口上限。
  total = '0'
  await page.evaluate(() => window.paginationList.load(true))
  if (!await page.locator('.btn-next').isDisabled()) throw new Error('空列表允许翻页')
  total = '9223372036854775807'
  await page.evaluate(() => window.paginationList.load())
  if (!(await page.locator('.list-pager').innerText()).includes('9,223,372,036,854,775,807')) {
    throw new Error('大整数总数展示丢失精度')
  }
  await jumper.fill('100001')
  await jumper.press('Enter')
  await active.filter({ hasText: /^\s*100000\s*$/ }).waitFor()
  if (!await page.locator('.btn-next').isDisabled()) throw new Error('超过接口页码上限仍允许翻页')
  await page.setViewportSize({ width: 375, height: 812 })
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) {
    throw new Error('后台分页在窄屏溢出')
  }
  await page.screenshot({ path: 'output/playwright/pagination-admin-mobile.png' })

  await page.goto('http://127.0.0.1:5174/pagination-fixture')
  await page.evaluate(async () => {
    const { createApp, h, reactive } = await import('/node_modules/.vite/deps/vue.js')
    const { default: ListPager } = await import('/src/components/ListPager.vue')
    await import('/node_modules/element-plus/dist/index.css')
    await import('/src/assets/styles/main.scss')
    await import('/src/assets/styles/account.scss')
    const data = reactive({ page: 1, pageSize: 6, total: '120', totalPages: '20' })
    window.paginationData = data
    window.paginationEvents = []
    createApp({ render: () => h(ListPager, { data, onChange(value) {
      window.paginationEvents.push(value)
      data.page = value
    } }) }).mount('#app')
  })
  await page.locator('.el-pager li.number').filter({ hasText: /^\s*3\s*$/ }).click()
  await active.filter({ hasText: /^\s*3\s*$/ }).waitFor()
  await page.locator('.btn-next').click()
  await active.filter({ hasText: /^\s*4\s*$/ }).waitFor()
  const events = await page.evaluate(() => window.paginationEvents)
  if (JSON.stringify(events) !== '[3,4]') throw new Error('前台翻页事件不正确')
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) {
    throw new Error('前台分页在窄屏溢出')
  }
  await page.screenshot({ path: 'output/playwright/pagination-blog-mobile.png' })
  await page.evaluate(() => { window.paginationData.totalPages = '1' })
  await page.locator('.v2-pager').waitFor({ state: 'hidden' })
  await page.evaluate(() => {
    window.paginationData.page = 100000
    window.paginationData.totalPages = '9223372036854775807'
  })
  await active.filter({ hasText: /^\s*100000\s*$/ }).waitFor()
  if (!await page.locator('.btn-next').isDisabled()) throw new Error('前台页码超过接口上限')
  const bounds = await page.locator('.el-pagination').boundingBox()
  if (bounds.x < 0 || bounds.x + bounds.width > 375) throw new Error('前台大页码在窄屏被裁切')
  if (failures.length) throw new Error(failures.join('\n'))
  return { admin: '条数、数字翻页、跳页、空页、BIGINT、上限、窄屏通过', blog: '数字翻页、事件、单页隐藏、上限、窄屏通过' }
}
