<script setup>
import { computed } from 'vue'
import { formatCount } from '../format.js'
import { makeTrendOption } from '../chartOptions.js'
import ReportChart from './ReportChart.vue'
const props = defineProps({
  rows: { type: Array, default: () => [] },
  valueKey: String,
  label: String,
  color: String,
})
const option = computed(() => makeTrendOption(props.rows, props.valueKey, props.label, props.color))
</script>

<template>
  <div v-if="rows.length" class="trend-chart">
    <ReportChart :option="option" :label="`${label}，UTC 每日数据`" />
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
