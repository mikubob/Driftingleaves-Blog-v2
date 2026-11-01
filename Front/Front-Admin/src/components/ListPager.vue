<script setup>
import { computed } from 'vue'
import { formatCount } from '../format.js'

/** 页数来自接口；切换每页条数由列表重置到第一页并重新查询。 */
const props = defineProps({
  page: Number,
  pageSize: Number,
  total: String,
  totalPages: String,
  loading: Boolean,
})
const emit = defineEmits(['change', 'size-change'])
// 接口最多允许访问第 100000 页，先截取再转数字，保留 BIGINT 总数的精度。
const pageCount = computed(() => {
  const count = BigInt(props.totalPages)
  return Number(count > 100000n ? 100000n : count) || 1
})
</script>

<template>
  <div class="list-pager">
    <span>共 {{ formatCount(total) }} 条记录</span>
    <el-pagination
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="[10, 15, 20, 50]"
      :page-count="pageCount"
      :pager-count="5"
      :disabled="loading"
      layout="sizes, prev, pager, next, jumper"
      @update:current-page="emit('change', $event)"
      @update:page-size="emit('size-change', $event)"
    />
  </div>
</template>
