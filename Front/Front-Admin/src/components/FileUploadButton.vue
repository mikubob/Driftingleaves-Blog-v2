<script setup>
import { ref } from 'vue'
import { ElButton } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import { fileAccept } from '../fileUpload.js'

/** 选择单个指定类型的文件，同一个文件可以重新选择。 */
defineProps({ kind: { type: String, required: true }, disabled: Boolean, loading: Boolean })
const emit = defineEmits(['select'])
const input = ref(null)

function select(event) {
  const file = event.target.files[0]
  event.target.value = ''
  if (file) {
    emit('select', file)
  }
}
</script>

<template>
  <input ref="input" type="file" :accept="fileAccept[kind]" :disabled="disabled || loading" hidden @change="select" />
  <el-button :disabled="disabled" :loading="loading" @click="input.click()">
    <slot />
  </el-button>
</template>
