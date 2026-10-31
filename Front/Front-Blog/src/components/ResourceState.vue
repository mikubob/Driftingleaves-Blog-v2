<script setup>
defineProps({ resource: { type: Object, required: true } })
</script>

<template>
  <div v-if="resource.loading" class="loading-state" role="status">
    <span class="spinner"></span>
    正在载入…
  </div>
  <div v-else-if="resource.error" class="error-state" role="alert">
    <p>{{ resource.error }}</p>
    <button class="text-button" @click="resource.reload">重新加载 ↻</button>
  </div>
</template>
