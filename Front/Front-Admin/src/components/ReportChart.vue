<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

/** 复用旧管理端的图表引擎，随容器尺寸变化重绘，卸载时释放实例和监听。 */
const props = defineProps({ option: Object, label: String })
const element = ref(null)
let chart
let observer

onMounted(() => {
  chart = echarts.init(element.value)
  chart.setOption(props.option)
  observer = new ResizeObserver(() => {
    // 首次观察也会回调，尺寸未变时重绘会中断 ECharts 的入场动画。
    if (
      chart.getWidth() !== element.value.clientWidth ||
      chart.getHeight() !== element.value.clientHeight
    ) {
      chart.resize()
    }
  })
  observer.observe(element.value)
})

watch(
  () => props.option,
  (option) => {
    chart?.setOption(option)
  },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="element" class="report-chart" role="img" :aria-label="label" />
</template>

<style scoped>
.report-chart {
  width: 100%;
  height: 260px;
  min-width: 0;
}
</style>
