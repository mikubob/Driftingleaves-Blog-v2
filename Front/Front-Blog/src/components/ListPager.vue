<script setup>
defineProps({ data: { type: Object, required: true } })
defineEmits(['change'])
</script>

<template>
  <nav v-if="data.totalPages !== '0'" class="v2-pager" aria-label="分页">
    <button :disabled="data.page <= 1" @click="$emit('change', data.page - 1)">← 上一页</button>
    <span>第 {{ data.page }} / {{ data.totalPages }} 页 · 共 {{ data.total }} 条</span>
    <button
      :disabled="BigInt(data.page) >= BigInt(data.totalPages) || data.page >= 100000"
      @click="$emit('change', data.page + 1)"
    >
      下一页 →
    </button>
  </nav>
</template>
