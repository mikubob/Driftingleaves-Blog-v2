<script setup>
import { computed } from 'vue'
import { ElConfigProvider, ElPagination } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

/** 直接使用接口分页数据，翻页事件交回页面维护 URL 或列表状态。 */
const props = defineProps({ data: { type: Object, required: true } })
defineEmits(['change'])
// 页数先按接口上限截取，再转数字，避免 BIGINT 丢失精度。
const pageCount = computed(() => {
  const count = BigInt(props.data.totalPages)
  return Number(count > 100000n ? 100000n : count) || 1
})
</script>

<template>
  <nav v-if="pageCount > 1" class="v2-pager" aria-label="分页">
    <ElConfigProvider :locale="zhCn">
      <ElPagination
        :current-page="data.page"
        :page-size="data.pageSize"
        :page-count="pageCount"
        :pager-count="5"
        layout="prev, pager, next"
        background
        @update:current-page="$emit('change', $event)"
      />
    </ElConfigProvider>
  </nav>
</template>
