<script setup>
import { ElCard, ElTable, ElTableColumn } from 'element-plus'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import { computed, ref } from 'vue'
import { formatTime } from '../format.js'
import BatchActions from './BatchActions.vue'
import DiscussionAuthor from './DiscussionAuthor.vue'
import ListPager from './ListPager.vue'
import StatusTag from './StatusTag.vue'

/** 按列表接口的顺序平铺评论或留言，批量栏和表头复选框只操作本页。 */
const props = defineProps({
  kind: { type: String, required: true },
  list: { type: Object, required: true },
  actions: { type: Array, required: true },
  busy: Boolean,
})
const { rows, page, pageSize, total, totalPages, loading, error, load, changePage, changePageSize } = props.list
const batch = ref(null)
const table = ref(null)
const isMessage = computed(() => props.kind === 'message')
const label = computed(() => isMessage.value ? '留言' : '评论')
const idKey = computed(() => isMessage.value ? 'messageId' : 'commentId')
const resource = computed(() => isMessage.value ? '/admin/messages' : '/admin/comments')
const disabled = computed(() => loading.value || props.busy || !!batch.value?.busy)
defineExpose({ disabled })
</script>

<template>
  <el-card shadow="never" class="discussion-table-card">
    <div class="card-heading">
      <h2>{{ label }}列表</h2>
    </div>
    <BatchActions
      ref="batch" :resource="resource" :id-key="idKey" :rows="rows" :table="table" :actions="actions"
      :disabled="loading || busy || !!error" @completed="load()"
    />
    <el-table
      v-if="!error" ref="table" v-loading="disabled" :data="rows"
      :row-key="idKey" border stripe :empty-text="`暂无${label}`" @selection-change="batch?.select"
    >
      <el-table-column v-if="actions.length" type="selection" width="48" :selectable="batch?.selectable" />
      <el-table-column prop="content" :label="`${label}内容`" min-width="240" show-overflow-tooltip />
      <el-table-column label="昵称" width="160">
        <template #default="{ row }"><DiscussionAuthor :record="row" /></template>
      </el-table-column>
      <el-table-column label="回复对象" width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.replyTarget?.nickname || (row.parentId ? '已删除用户' : '—') }}</template>
      </el-table-column>
      <el-table-column v-if="!isMessage" prop="articleTitle" label="所属文章" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="isMessage" label="可见性" width="100">
        <template #default="{ row }">
          <span :class="['status-tag', { 'is-purple': row.isSecret }]">{{ row.isSecret ? '悄悄话' : '公开留言' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }"><StatusTag :value="row.status" kind="review" /></template>
      </el-table-column>
      <el-table-column label="时间" width="180" align="center">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" :width="isMessage ? 260 : 215" align="center" fixed="right">
        <template #default="{ row }">
          <slot name="actions" :row="row" :disabled="disabled" />
        </template>
      </el-table-column>
    </el-table>
    <ListPager
      preserve-page :page="page" :page-size="pageSize" :total="total" :total-pages="totalPages"
      :loading="disabled" @change="changePage" @size-change="changePageSize"
    />
  </el-card>
</template>

<style scoped>
.discussion-table-card .el-table {
  border-radius: 8px;
}
.discussion-table-card .list-pager {
  justify-content: flex-end;
}
.discussion-table-card :deep(.el-pagination__sizes) {
  min-width: 100px;
}
@media (max-width: 768px) {
  .discussion-table-card :deep(.el-table-fixed-column--right) {
    position: static !important;
  }
  .discussion-table-card :deep(.el-table-fixed-column--right::before) {
    display: none;
  }
}
</style>
