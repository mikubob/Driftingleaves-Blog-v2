<script setup>
defineProps({ message: String })
defineEmits(['retry'])
</script>

<template>
  <el-alert v-if="message" class="request-error" type="error" :closable="false" show-icon>
    <template #title>{{ message }}</template>
    <el-button size="small" @click="$emit('retry')">重新加载</el-button>
  </el-alert>
</template>
