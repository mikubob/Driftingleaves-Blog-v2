<script setup>
import { ElEmpty } from 'element-plus'
import 'element-plus/es/components/empty/style/css'
import { computed } from 'vue'
import { formatCount } from '../format.js'
import { makeTrendOption } from '../chartOptions.js'
import ReportChart from './ReportChart.vue'
/** 按指定的计数字段和图例名称展示趋势数据，支持在图表与每日明细表之间原地切换。 */
const props = defineProps({
  rows: { type: Array, default: () => [] },
  valueKey: String,
  label: String,
  color: String,
  showTable: { type: Boolean, default: false },
})
const option = computed(() => makeTrendOption(props.rows, props.valueKey, props.label, props.color))
</script>

<template>
  <div v-if="rows.length" class="trend-chart">
    <Transition name="chart-swap" mode="out-in">
      <ReportChart
        v-if="!showTable"
        :option="option"
        :label="`${label}，UTC 每日数据`"
      />
      <div v-else class="chart-table-pane">
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
      </div>
    </Transition>
  </div>
  <el-empty v-else description="暂无趋势数据" :image-size="70" />
</template>
