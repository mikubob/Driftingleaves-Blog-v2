<script setup>
import { ElPagination } from 'element-plus'
import 'element-plus/es/components/pagination/style/css'
import { computed } from 'vue'
import { formatCount } from '../format.js'

/** 页数来自接口；切换每页条数由列表重置到第一页并重新查询。 */
const props = defineProps({
  page: Number,
  pageSize: Number,
  total: String,
  totalPages: String,
  loading: Boolean,
  preservePage: Boolean,
})
const emit = defineEmits(['change', 'size-change'])
// 接口最多允许访问第 100000 页，先截取再转数字，保留 BIGINT 总数的精度。
const pageCount = computed(() => {
  const count = BigInt(props.totalPages)
  const available = Number(count > 100000n ? 100000n : count) || 1
  // 讨论审核保留当前页，防止删除末页记录后组件自动跳回并覆盖列表页码。
  return props.preservePage ? Math.max(available, props.page) : available
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
