<script setup>
import { ElButton } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import { computed } from 'vue'

/** 接收当前行和可用状态操作，通过同一个批量组件提交单条记录。 */
const props = defineProps({
  row: { type: Object, required: true },
  actions: { type: Array, required: true },
  batch: { type: Object, default: null },
  disabled: Boolean,
})
// 行内按钮与多选按钮共用确认、详情版本读取和批量请求。
const actions = computed(() => props.actions.filter(
  (action) => action.path === 'batch-status' && action.body.status !== props.row.status,
))
</script>

<template>
  <el-button
    v-for="action in actions"
    :key="action.body.status"
    :type="action.type || 'primary'"
    :disabled="disabled || !batch || batch.busy"
    link
    @click="batch.execute(action, [row])"
  >{{ action.label.replace('批量', '') }}</el-button>
</template>
