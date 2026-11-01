<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { prepareBatch } from '../batch.js'
import { request } from '../api.js'

const props = defineProps({
  resource: { type: String, required: true },
  idKey: { type: String, required: true },
  rows: { type: Array, required: true },
  table: { type: Object, default: null },
  actions: { type: Array, required: true },
  disabled: Boolean,
  selectable: { type: Function, default: () => true },
})
const emit = defineEmits(['completed'])
const selected = ref([])
const busy = ref(false)
const failures = ref([])
const blockVisible = ref(false)
const blockAction = ref(null)
const blockedUntil = ref(null)
const permanent = ref(false)
const blockError = ref('')
const blocked = computed(() => props.disabled || busy.value)
let generation = 0

/** 翻页、筛选、刷新时只清当前页选择，不将旧ID带到新的查询结果。 */
function clear() {
  selected.value = []
  props.table?.clearSelection()
}
watch(() => [props.rows, props.disabled], () => {
  generation += 1
  clear()
}, { flush: 'post' })
onBeforeUnmount(() => { generation += 1 })

function select(rows) {
  selected.value = rows
}

function selectable(row) {
  return !blocked.value && props.selectable(row)
}

function choose(action) {
  if (action.block) {
    blockAction.value = action
    blockedUntil.value = null
    permanent.value = false
    blockError.value = ''
    blockVisible.value = true
    return
  }
  execute(action)
}

async function block() {
  if (!permanent.value && (!(blockedUntil.value instanceof Date) || blockedUntil.value.getTime() <= Date.now())) {
    blockError.value = '请选择晚于当前时间的结束时间，或明确勾选永久限制。'
    return
  }
  await execute(blockAction.value, {
    blockedUntil: permanent.value ? null : blockedUntil.value.toISOString(),
  })
}

/** 读取全部版本后确认具体目标，只提交一次批量写请求。 */
async function execute(action, extra = {}) {
  if (blocked.value || !selected.value.length) {
    return
  }
  const targets = [...selected.value]
  if (action.accept && !targets.every(action.accept)) {
    ElMessage.warning(action.invalidMessage)
    return
  }
  const originalGeneration = generation
  busy.value = true
  try {
    const payload = await prepareBatch(props.resource, props.idKey, targets, action.revision !== false)
    if (originalGeneration !== generation) {
      throw new Error('列表已刷新，请重新选择记录。')
    }
    let warning = action.warning || ''
    if (action.block) {
      warning += extra.blockedUntil ? ` 限制至 ${new Date(extra.blockedUntil).toLocaleString()}。` : ' 将永久限制这些统计标识。'
    }
    try {
      await ElMessageBox.confirm(`确认${action.label}选中的 ${targets.length} 条记录？${warning}`, action.label, {
        confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning',
      })
    } catch {
      return
    }
    if (originalGeneration !== generation) {
      throw new Error('列表已刷新，请重新选择记录。')
    }
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

defineExpose({ select, selectable, busy })
</script>

<template>
  <div v-if="actions.length" class="batch-actions">
    <div class="batch-toolbar" role="group" aria-label="批量操作">
      <span class="batch-count" aria-live="polite">已选 {{ selected.length }} 条</span>
      <el-button
        v-for="action in actions"
        :key="action.path + action.label"
        :type="action.danger ? 'danger' : 'default'"
        plain
        :disabled="blocked || !selected.length"
        @click="choose(action)"
      >{{ action.label }}</el-button>
      <el-button v-if="selected.length" text :disabled="blocked" @click="clear">取消选择</el-button>
      <span class="batch-hint">表头全选当前页，最多 100 条</span>
    </div>
    <el-alert v-if="failures.length" type="warning" title="部分文件未删除" :closable="false" show-icon>
      <ul class="batch-failures">
        <li v-for="item in failures" :key="item.id">文件 {{ item.id }}：{{ item.msg }}（{{ item.code }}）</li>
      </ul>
    </el-alert>
    <el-dialog v-model="blockVisible" title="批量限制访客采集" width="460px" :close-on-click-modal="!busy">
      <p>仅限制所选统计标识的访问采集，不禁用账号，也不阻止公开阅读。</p>
      <el-checkbox v-model="permanent" :disabled="busy">永久限制</el-checkbox>
      <el-date-picker
        v-model="blockedUntil" type="datetime" placeholder="选择限制结束时间"
        aria-label="批量限制结束时间" :disabled="permanent || busy"
      />
      <p v-if="blockError" role="alert" class="danger-text">{{ blockError }}</p>
      <template #footer>
        <el-button :disabled="busy" @click="blockVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selected.length || disabled" :loading="busy" @click="block">继续</el-button>
      </template>
    </el-dialog>
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
.batch-hint { color: var(--el-text-color-secondary); font-size: 12px; }
.batch-actions > .el-alert { margin-bottom: 16px; }
.batch-failures { margin: 4px 0; padding-left: 18px; overflow-wrap: anywhere; }
.el-date-editor { max-width: 100%; margin: 12px 0; }
</style>
