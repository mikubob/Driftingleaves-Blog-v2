// 挂载真实统计页面，只在 HTTP 边界提供虚构数据，不连接业务服务。
async function chartsSmoke(page) {
  const failures = []
  page.on('pageerror', (error) => failures.push(error.message))
  page.setDefaultTimeout(10000)
  await page.setViewportSize({ width: 1280, height: 900 })
  let empty = false
  let zero = false
  const title = '<img src=x onerror="alert(1)">需要完整展示的文章标题与浏览量'
  await page.route('**/api/v1/**', async (route) => {
    const kind = new URL(route.request().url()).pathname.split('/').at(-1)
    const responses = {
      overview: { pageViewCount: '1248', visitorCount: '256', newVisitorCount: '48' },
      articles: [{ articleId: '1', title, pageViewCount: '128' }, { articleId: '2', title: '另一篇文章', pageViewCount: '64' }],
      provinces: [{ province: '浙江', visitorCount: '128' }, { province: '上海', visitorCount: '64' }],
      'page-views': [32, 16, 48, 24, 0, 16, 40].map((value, index) => ({
        date: new Date(Date.UTC(2026, 9, 26 + index)).toISOString().slice(0, 10),
        pageViewCount: zero ? '0' : String(value),
      })),
      visitors: [4, 1, 10, 0, 0, 3, 9].map((value, index) => ({
        date: new Date(Date.UTC(2026, 9, 26 + index)).toISOString().slice(0, 10),
        newVisitorCount: zero ? '0' : String(value),
      })),
    }
    if (!(kind in responses)) {
      throw new Error(`图表测试出现意外请求：${kind}`)
    }
    await route.fulfill({ json: {
      code: 'OK', msg: '成功', errors: [],
      data: empty && kind !== 'overview' ? [] : responses[kind],
    } })
  })
  await page.route('**/charts-fixture', (route) => route.fulfill({
    contentType: 'text/html',
    body: '<html lang="zh-CN"><meta charset="utf-8"><body style="padding:24px"><div id="app"></div></body></html>',
  }))
  await page.goto('http://127.0.0.1:5176/charts-fixture')
  await page.evaluate(async () => {
    const { createApp } = await import('/node_modules/.vite/deps/vue.js')
    const { default: ElementPlus } = await import('/node_modules/.vite/deps/element-plus.js')
    const { default: Dashboard } = await import('/src/views/Dashboard.vue')
    window.chartEngine = await import('/node_modules/.vite/deps/echarts_core.js')
    await import('/node_modules/element-plus/dist/index.css')
    await import('/src/style.css')
    window.chartApp = createApp(Dashboard).use(ElementPlus)
    window.chartApp.mount('#app')
  })
  await page.waitForFunction(() => document.querySelectorAll('.report-chart canvas').length === 4)
  const animated = await page.evaluate(() => {
    window.chartInstances = [...document.querySelectorAll('.report-chart')].map((element) => window.chartEngine.getInstanceByDom(element))
    return window.chartInstances.every((chart) => !chart.getZr().animation.isFinished())
  })
  if (!animated) {
    throw new Error('图表未启用旧项目的入场动画')
  }
  await page.waitForFunction(() => window.chartInstances.every((chart) => chart.getZr().animation.isFinished()))

  // 使用真实鼠标移动触发提示，检查折线按日期联动、柱体阴影和扇区高亮。
  for (const [index, expected] of [[0, '浏览量：32'], [1, '新访客数：4'], [2, title], [3, '(66.67%)']]) {
    const element = page.locator('.report-chart').nth(index)
    await element.scrollIntoViewIfNeeded()
    const point = await element.evaluate((element, index) => {
      const chart = window.chartInstances[index]
      let point
      if (index === 3) {
        const layout = chart.getModel().getSeriesByIndex(0).getData().getItemLayout(0)
        const angle = (layout.startAngle + layout.endAngle) / 2
        const radius = (layout.r0 + layout.r) / 2
        point = [layout.cx + Math.cos(angle) * radius, layout.cy + Math.sin(angle) * radius]
      } else {
        let values = [0, '4']
        if (index === 0) {
          values = [0, '32']
        }
        if (index === 2) {
          values = ['128', 0]
        }
        point = chart.convertToPixel({ seriesIndex: 0 }, values)
        if (index === 2) {
          point[0] -= 10
        }
      }
      const rect = element.getBoundingClientRect()
      return { x: rect.x + point[0], y: rect.y + point[1] }
    }, index)
    await page.mouse.move(point.x, point.y)
    await element.getByText(expected, { exact: false }).waitFor({ state: 'visible' })
    if (await element.locator('img').count()) {
      throw new Error('外部标题被当作 HTML 执行')
    }
  }
  await page.waitForFunction(() => window.chartInstances[3].getModel().getSeriesByIndex(0).getData().getItemGraphicEl(0).currentStates.includes('emphasis'))
  await page.screenshot({ path: 'output/playwright/charts-desktop.png', fullPage: true })

  const legendPoint = await page.locator('.report-chart').nth(3).evaluate((element) => {
    const chart = window.chartInstances[3]
    const text = chart.getZr().storage.getDisplayList().find((item) => item.style?.text === '浙江')
    const rect = text.getBoundingRect()
    const point = text.transformCoordToGlobal(rect.x + rect.width / 2, rect.y + rect.height / 2)
    const box = element.getBoundingClientRect()
    return { x: box.x + point[0], y: box.y + point[1] }
  })
  await page.mouse.click(legendPoint.x, legendPoint.y)
  await page.waitForFunction(() => window.chartInstances[3].getOption().legend[0].selected['浙江'] === false)
  await page.getByText('查看文章访问明细', { exact: true }).focus()
  await page.keyboard.press('Enter')
  await page.getByRole('cell', { name: title, exact: true }).waitFor({ state: 'visible' })

  await page.setViewportSize({ width: 375, height: 812 })
  await page.waitForFunction(() => window.chartInstances.every((chart) => Math.abs(chart.getWidth() - chart.getDom().clientWidth) < 1))
  await page.waitForFunction(() => window.chartInstances.every((chart) => chart.getZr().animation.isFinished()))
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) {
    throw new Error('图表在手机宽度出现横向溢出')
  }
  await page.screenshot({ path: 'output/playwright/charts-mobile.png', fullPage: true })
  zero = true
  await page.getByRole('button', { name: '更新报表' }).click()
  await page.waitForFunction(() => window.chartInstances[0].getOption().series[0].data.every((item) => item.value === '0'))
  empty = true
  await page.getByRole('button', { name: '更新报表' }).click()
  await page.waitForFunction(() => !document.querySelector('.report-chart'))
  if (!await page.evaluate(() => window.chartInstances.every((chart) => chart.isDisposed()))) {
    throw new Error('空结果未释放旧图表')
  }
  await page.getByText('暂无地域数据', { exact: true }).waitFor()
  empty = false
  await page.getByRole('button', { name: '更新报表' }).click()
  await page.waitForFunction(() => document.querySelectorAll('.report-chart canvas').length === 4)
  await page.evaluate(() => {
    window.chartInstances = [...document.querySelectorAll('.report-chart')].map((element) => window.chartEngine.getInstanceByDom(element))
    window.chartApp.unmount()
  })
  if (!await page.evaluate(() => window.chartInstances.every((chart) => chart.isDisposed()))) {
    throw new Error('页面卸载未释放图表')
  }
  if (failures.length) {
    throw new Error(JSON.stringify(failures))
  }
  return { charts: 4, animated, mouseTooltips: true, legendToggle: true, mobileWidth: 375, zeroAndEmptyRefresh: true, disposedOnUnmount: true, runtimeErrors: failures }
}
