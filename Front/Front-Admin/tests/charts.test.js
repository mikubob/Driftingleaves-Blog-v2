import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as echarts from 'echarts/core.js'
import { BarChart, LineChart, PieChart } from 'echarts/charts.js'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components.js'
import { SVGRenderer } from 'echarts/renderers.js'
import { makeArticleOption, makeProvinceOption, makeTrendOption } from '../src/chartOptions.js'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, SVGRenderer])

test('真实图表渲染保留 BIGINT 原值，悬停提示显示完整标题并转义外部文本', () => {
  const count = '9223372036854775807'
  const title = '<img src=x onerror="alert(1)">一篇需要完整显示标题的文章'
  const options = [
    makeTrendOption([{ date: '2026-11-01', pageViewCount: count }], 'pageViewCount', '浏览量'),
    makeArticleOption([{ title, pageViewCount: count }]),
    makeProvinceOption([{ province: '浙江', visitorCount: count }]),
  ]
  const chart = echarts.init(null, null, { renderer: 'svg', ssr: true, width: 600, height: 260 })
  try {
    for (const option of options) {
      chart.setOption(option, { notMerge: true })
      assert.ok(chart.renderToSVGString().includes('<path'))
      const series = chart.getModel().getSeriesByIndex(0)
      const params = series.getDataParams(0)
      assert.equal(params.data.value, count)
      const tooltip = option.tooltip.formatter(option.tooltip.trigger === 'axis' ? [params] : params)
      assert.ok(tooltip.includes('9,223,372,036,854,775,807'))
      if (params.seriesType === 'bar') {
        assert.ok(tooltip.includes('&lt;img'))
        assert.ok(tooltip.includes('一篇需要完整显示标题的文章'))
        assert.ok(!tooltip.includes('<img'))
      }
      if (params.seriesType === 'pie') {
        assert.ok(tooltip.includes('(100%)'))
      }
    }
  } finally {
    chart.dispose()
  }
})

test('更新为空数据和零值时清除旧系列，未知省份仍可显示', () => {
  const chart = echarts.init(null, null, { renderer: 'svg', ssr: true, width: 600, height: 260 })
  try {
    const option = makeProvinceOption([{ province: null, visitorCount: '0' }])
    chart.setOption(option)
    const params = chart.getModel().getSeriesByIndex(0).getDataParams(0)
    assert.match(option.tooltip.formatter(params), /未知<br\/>访客数：0 \(0%\)/)
    for (const emptyOption of [
      makeTrendOption([], 'newVisitorCount', '新访客数'),
      makeArticleOption([]),
      makeProvinceOption([]),
    ]) {
      chart.setOption(emptyOption, { notMerge: true })
      assert.equal(chart.getModel().getSeriesByIndex(0).getData().count(), 0)
      assert.ok(!chart.renderToSVGString().includes('NaN'))
    }
  } finally {
    chart.dispose()
  }
})
