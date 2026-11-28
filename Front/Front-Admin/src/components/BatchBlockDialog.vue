<script setup>
import { ref, watch } from 'vue'
import { ElButton, ElCheckbox, ElDatePicker, ElDialog, ElMessage } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/checkbox/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/message/style/css'

/** 批量封禁 IP 的到期时间弹窗，仅在访客封禁操作时按需加载。 */
const props = defineProps({
  modelValue: Boolean,
  warning: { type: String, default: '' },
  busy: Boolean,
  disabled: Boolean,
})
const emit = defineEmits(['update:modelValue', 'confirm'])
const blockedUntil = ref(null)
const permanent = ref(false)

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      blockedUntil.value = null
      permanent.value = false
    }
  },
  { immediate: true },
)

function close() {
  if (props.busy) {
    return
  }
  emit('update:modelValue', false)
}

function submit() {
  if (props.busy || props.disabled) {
    return
  }
  if (!permanent.value && (!(blockedUntil.value instanceof Date) || blockedUntil.value.getTime() <= Date.now())) {
    ElMessage.warning('请选择晚于当前时间的结束时间，或明确勾选永久限制。')
    return
  }
  emit('confirm', {
    blockedUntil: permanent.value ? null : blockedUntil.value.toISOString(),
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="批量封禁 IP"
    width="460px"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p>{{ warning }}</p>
    <el-checkbox v-model="permanent" :disabled="busy">永久封禁</el-checkbox>
    <el-date-picker
      v-model="blockedUntil"
      type="datetime"
      placeholder="选择限制结束时间"
      aria-label="批量限制结束时间"
      :disabled="permanent || busy"
    />
    <template #footer>
      <el-button :disabled="busy" @click="close">取消</el-button>
      <el-button type="primary" :disabled="disabled" :loading="busy" @click="submit">继续</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.el-date-editor {
  max-width: 100%;
  margin: 12px 0;
}
</style>
