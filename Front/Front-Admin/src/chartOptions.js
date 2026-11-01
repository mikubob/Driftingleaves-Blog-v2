import { formatCount } from './format.js'
import { format } from 'echarts/core.js'

// 外部标题先转义；提示与明细读取原始字符串，保留 BIGINT 精度。
function countTooltip(params) {
  const item = Array.isArray(params) ? params[0] : params
  let text = `${format.encodeHTML(item.name)}<br/>${format.encodeHTML(item.seriesName)}：${formatCount(item.data.value)}`
  if (item.seriesType === 'pie') {
    text += ` (${item.percent}%)`
  }
  return text
}

const tooltip = {
  renderMode: 'html',
  confine: true,
  extraCssText: 'max-width: min(360px, 60vw); white-space: normal; overflow-wrap: anywhere;',
  formatter: countTooltip,
}

const valueAxis = {
  type: 'value',
  splitLine: { lineStyle: { color: '#f0f0f0' } },
  axisLabel: { color: '#909399', fontSize: 11 },
}

/** 沿用旧后台的平滑折线、渐变面积和按日期联动提示。 */
export function makeTrendOption(rows, valueKey, label, color = '#2563eb') {
  return {
    tooltip: { ...tooltip, trigger: 'axis' },
    grid: { left: 12, right: 20, top: 30, bottom: 12, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.date),
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#909399', fontSize: 12, hideOverlap: true },
    },
    yAxis: valueAxis,
    series: [
      {
        name: label,
        type: 'line',
        data: rows.map((row) => ({ value: row[valueKey] })),
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color, width: 2 },
        itemStyle: { color },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: `${color}33` },
              { offset: 1, color: `${color}00` },
            ],
          },
        },
      },
    ],
  }
}

/** 标题仅在坐标轴截断，悬停时保留完整标题和精确浏览量。 */
export function makeArticleOption(rows) {
  return {
    tooltip: {
      ...tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    grid: { left: 12, right: 20, top: 16, bottom: 12, containLabel: true },
    xAxis: valueAxis,
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((row) => row.title),
      axisLabel: { color: '#606266', fontSize: 12, width: 110, overflow: 'truncate' },
      axisLine: { lineStyle: { color: '#e4e7ed' } },
    },
    series: [
      {
        name: '浏览量',
        type: 'bar',
        data: rows.map((row) => ({ value: row.pageViewCount })),
        barMaxWidth: 20,
        itemStyle: { color: '#7c3aed', borderRadius: [0, 4, 4, 0] },
      },
    ],
  }
}

/** 省份使用不同颜色区分，保留扇区高亮和图例筛选。 */
export function makeProvinceOption(rows) {
  return {
    tooltip: { ...tooltip, trigger: 'item' },
    color: [
      '#2563eb', '#258552', '#7c3aed', '#d97706', '#0891b2',
      '#db2777', '#0f766e', '#dc2626', '#4f46e5', '#64748b',
    ],
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 0,
      top: 'center',
      textStyle: { color: '#606266', fontSize: 12, width: 95, overflow: 'truncate' },
      icon: 'circle',
    },
    series: [
      {
        name: '访客数',
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        labelLine: { show: false },
        data: rows.map((row) => ({ name: row.province || '未知', value: row.visitorCount })),
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
      },
    ],
  }
}
