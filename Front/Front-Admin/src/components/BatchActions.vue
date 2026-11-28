<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue'
import {
  ElAlert,
  ElButton,
  ElMessage,
  ElMessageBox,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { prepareBatch } from '../batch.js'
import { request } from '../api.js'

const BatchBlockDialog = defineAsyncComponent(() => import('./BatchBlockDialog.vue'))

/** 根据资源、记录和操作配置显示批量操作；传入选中项时由父页面维护选中状态。 */
const props = defineProps({
  resource: { type: String, required: true },
  idKey: { type: String, required: true },
  rows: { type: Array, required: true },
  table: { type: Object, default: null },
  actions: { type: Array, required: true },
  disabled: Boolean,
  selectedRows: Array,
  selectable: { type: Function, default: () => true },
})
/** 批量操作成功后通知父页面刷新，外部维护选中项时由父页面清除。 */
const emit = defineEmits(['completed', 'clear'])
const tableSelection = ref([])
const selected = computed(() => props.selectedRows ?? tableSelection.value)
const busy = ref(false)
const failures = ref([])
const blockVisible = ref(false)
const blockMounted = ref(false)
const blockAction = ref(null)
const blocked = computed(() => props.disabled || busy.value)
let generation = 0

/** 翻页、筛选或刷新时清空当前页的选择，避免把旧id带到新的查询结果。 */
function clear() {
  if (props.selectedRows) {
    emit('clear')
    return
  }
  tableSelection.value = []
  props.table?.clearSelection()
}
watch(() => [props.rows, props.disabled], () => {
  generation += 1
  if (!props.selectedRows) {
    clear()
  }
}, { flush: 'post' })
onBeforeUnmount(() => {
  generation += 1
})

function select(rows) {
  tableSelection.value = rows
}

function selectable(row) {
  return !blocked.value && props.selectable(row)
}

function choose(action) {
  if (action.block) {
    blockAction.value = action
    blockMounted.value = true
    blockVisible.value = true
    return
  }
  execute(action)
}

async function confirmBlock(extra) {
  await execute(blockAction.value, selected.value, extra)
}

/** 批量操作前先读取所有选中记录的版本，再确认操作对象，最后只发送一次批量请求。 */
async function execute(action, rows = selected.value, extra = {}) {
  if (blocked.value || !rows.length) {
    return
  }
  const targets = [...rows]
  if (action.accept && !targets.every(action.accept)) {
    ElMessage.warning(action.invalidMessage)
    return
  }
  const originalGeneration = generation
  busy.value = true
  try {
    // 1. 读取选中记录的版本，列表已刷新时要求重新选择。
    const payload = await prepareBatch(props.resource, props.idKey, targets, action.revision !== false, action.matchDetail)
    if (originalGeneration !== generation) {
      throw new Error('列表已刷新，请重新选择记录。')
    }

    // 2. 展示影响范围，确认后再次检查列表是否刷新。
    let warning = action.warning || ''
    if (action.block) {
      warning += extra.blockedUntil ? ` 封禁至 ${new Date(extra.blockedUntil).toLocaleString()}。` : ' 此次为永久封禁。'
    }
    const target = action.targetLabel || `选中的 ${targets.length} 条记录`
    try {
      await ElMessageBox.confirm(`确认${action.label}${target}？${warning}`, action.label, {
        confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning',
      })
    } catch {
      return
    }
    if (originalGeneration !== generation) {
      throw new Error('列表已刷新，请重新选择记录。')
    }
    // 3. 提交整批请求，逐个展示文件删除失败的结果。
    failures.value = []
    const { data } = await request(`${props.resource}/${action.path}`, {
      method: action.method || 'POST',
      body: { ...payload, ...action.body, ...extra },
    })
    if (action.fileResults) {
      failures.value = data.results.filter((item) => item.code !== 'OK')
      const succeeded = data.results.length - failures.value.length
      if (failures.value.length) {
        ElMessage.warning(`已删除 ${succeeded} 个文件，${failures.value.length} 个失败，请查看下方明细。`)
      } else {
        ElMessage.success(`已删除 ${succeeded} 个文件`)
      }
    } else if (action.targetLabel) {
      ElMessage.success(`${action.label}成功`)
    } else {
      ElMessage.success(`${action.label}成功，共 ${targets.length} 条`)
    }
    blockVisible.value = false
    clear()
    emit('completed')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

/** 提供表格选中项更新和批量执行入口，行内操作可复用相同流程。 */
defineExpose({ select, selectable, busy, execute })
</script>

<template>
  <div v-if="actions.length" class="batch-actions">
    <div v-if="!selectedRows || selected.length" class="batch-toolbar" role="group" aria-label="批量操作">
      <span class="batch-count" aria-live="polite">已选 {{ selected.length }} 条</span>
      <el-button
        v-for="action in actions"
        :key="action.path + action.label"
        :type="action.danger ? 'danger' : (action.type || 'primary')"
        plain
        :disabled="blocked || !selected.length"
        @click="choose(action)"
      >{{ action.label }}</el-button>
      <el-button v-if="selected.length" plain :disabled="blocked" @click="clear">取消选择</el-button>
    </div>
    <el-alert v-if="failures.length" type="warning" title="部分文件未删除" :closable="false" show-icon>
      <ul class="batch-failures">
        <li v-for="item in failures" :key="item.id">文件 {{ item.id }}：{{ item.msg }}（{{ item.code }}）</li>
      </ul>
    </el-alert>
    <BatchBlockDialog
      v-if="blockMounted"
      v-model="blockVisible"
      :warning="blockAction?.warning"
      :busy="busy"
      :disabled="!selected.length || disabled"
      @confirm="confirmBlock"
    />
  </div>
</template>

<style scoped>
.batch-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.batch-toolbar .el-button + .el-button { margin-left: 0; }
.batch-count { color: var(--el-text-color-regular); font-size: 13px; white-space: nowrap; }
.batch-actions > .el-alert { margin-bottom: 16px; }
.batch-failures { margin: 4px 0; padding-left: 18px; overflow-wrap: anywhere; }
</style>
