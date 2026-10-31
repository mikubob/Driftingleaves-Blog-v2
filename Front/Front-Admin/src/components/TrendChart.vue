<script setup>
import { computed } from 'vue'
import { formatCount, ratio } from '../format.js'
const props = defineProps({
  rows: { type: Array, default: () => [] },
  valueKey: String,
  label: String,
})
const maximum = computed(() =>
  props.rows.reduce(
    (max, row) => (BigInt(row[props.valueKey]) > BigInt(max) ? row[props.valueKey] : max),
    '0',
  ),
)
const points = computed(() =>
  props.rows.map((row, index) => ({
    x: 30 + (index * 640) / Math.max(props.rows.length - 1, 1),
    y: 180 - ratio(row[props.valueKey], maximum.value) * 1.5,
    row,
  })),
)
const line = computed(() => points.value.map((point) => `${point.x},${point.y}`).join(' '))
</script>

<template>
  <div v-if="rows.length" class="trend-chart">
    <svg viewBox="0 0 700 220" role="img" :aria-label="`${label}，UTC 每日数据`">
      <line
        v-for="y in [30, 80, 130, 180]"
        :key="y"
        x1="30"
        :y1="y"
        x2="670"
        :y2="y"
        stroke="#e8ebef"
        stroke-dasharray="4 4"
      />
      <polygon :points="`30,180 ${line} ${points.at(-1).x},180`" fill="#f1f3f5" />
      <polyline
        :points="line"
        fill="none"
        stroke="#303133"
        stroke-width="2.5"
        stroke-linejoin="round"
      />
      <circle
        v-for="point in points"
        :key="point.row.date"
        :cx="point.x"
        :cy="point.y"
        r="3"
        fill="#303133"
      >
        <title>{{ point.row.date }} · {{ label }} {{ formatCount(point.row[valueKey]) }}</title>
      </circle>
      <text x="30" y="210" fill="#909399" font-size="12">{{ rows[0].date }}</text>
      <text x="670" y="210" text-anchor="end" fill="#909399" font-size="12">
        {{ rows.at(-1).date }}
      </text>
      <text x="30" y="20" fill="#909399" font-size="11">最高 {{ formatCount(maximum) }}</text>
    </svg>
    <details class="chart-details">
      <summary>查看每日数据</summary>
      <table class="data-table">
        <thead>
          <tr>
            <th>日期（UTC）</th>
            <th>{{ label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.date">
            <td>{{ row.date }}</td>
            <td>{{ formatCount(row[valueKey]) }}</td>
          </tr>
        </tbody>
      </table>
    </details>
  </div>
  <el-empty v-else description="暂无趋势数据" :image-size="70" />
</template>
