<script setup>
import { onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
import StatusTag from '../components/StatusTag.vue'

const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/comments',
  { articleId: '', userId: '', status: '' },
  20,
)
const detail = ref(null)
const detailVisible = ref(false)
const busy = ref(false)

async function view(row) {
  try {
    detail.value = (await request(`/admin/comments/${row.commentId}`)).data
    detailVisible.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function review(row, status) {
  busy.value = true
  try {
    const path = `/admin/comments/${row.commentId}`
    const current = await request(path)
    await request('/admin/comments/batch-review', {
      method: 'PUT',
      body: { items: [{ id: row.commentId, revision: current.revision }], status },
    })
    ElMessage.success(status === 1 ? '评论已通过' : '评论已驳回')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('删除这条评论及其全部子回复？删除后无法恢复。', '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await request('/admin/comments/batch-delete', { method: 'POST', body: { ids: [row.commentId] } })
    ElMessage.success('评论已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}
onMounted(load)

const batch = ref(null)
const batchTable = ref(null)
const batchActions = computed(() => {
  const actions = []
  if (can('blog:comment:review')) {
    actions.push({
      label: '批量通过',
      path: 'batch-review',
      method: 'PUT',
      body: { status: 1 },
      accept: (row) => row.status !== 3,
      invalidMessage: '历史删除占位不能重新审核。',
    })
  }
  if (can('blog:comment:review')) {
    actions.push({
      label: '批量驳回',
      path: 'batch-review',
      method: 'PUT',
      body: { status: 2 },
      accept: (row) => row.status !== 3,
      invalidMessage: '历史删除占位不能重新审核。',
    })
  }
  if (can('blog:comment:moderate')) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      revision: false,
      danger: true,
      warning: '所选评论及其全部层级回复都会删除，包括其他用户的回复，无法恢复。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">CONTENT / COMMENTS</p>
        <h1>评论管理</h1>
        <p class="muted-text">认真对待每一次交流，审核与管理文章评论。</p>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.articleId"
          aria-label="文章 ID"
          placeholder="文章 ID"
          pattern="[1-9][0-9]{0,18}"
          clearable
        />
        <el-input
          v-model="filters.userId"
          aria-label="评论者 ID"
          placeholder="评论者 ID"
          pattern="[1-9][0-9]{0,18}"
          clearable
        />
        <el-select v-model="filters.status" aria-label="审核状态" placeholder="全部状态" clearable>
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="已删除" :value="3" />
        </el-select>
        <el-button native-type="submit" type="primary">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>评论列表</h2>
        <span class="muted-text">删除将同时移除全部子回复</span>
      </div>
            <BatchActions
        ref="batch" resource="/admin/comments" id-key="commentId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || busy || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || busy || batch?.busy"
        :data="rows"
        row-key="commentId"
        empty-text="暂无评论"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column label="评论内容" min-width="280">
          <template #default="{ row }">
            <button class="table-title" @click="view(row)">{{ row.content }}</button>
            <p class="table-secondary">
              {{ row.author?.nickname || '已删除用户' }} · {{ row.articleTitle }}
            </p>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="review" /></template>
        </el-table-column>
        <el-table-column label="发表时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link @click="view(row)">详情</el-button>
            <template v-if="row.status !== 3">
              <el-button
                v-if="can('blog:comment:review') && row.status !== 1"
                link
                @click="review(row, 1)"
              >
                通过
              </el-button>
              <el-button
                v-if="can('blog:comment:review') && row.status !== 2"
                link
                @click="review(row, 2)"
              >
                驳回
              </el-button>
            </template>
            <el-button v-if="can('blog:comment:moderate')" link @click="remove(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPager
        :page="page"
        :page-size="pageSize"
        :total="total"
        :total-pages="totalPages"
        :loading="loading || busy"
        @change="changePage"
        @size-change="changePageSize"
      />
    </el-card>
    <el-dialog v-model="detailVisible" title="评论详情" width="650px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="评论 ID">{{ detail.commentId }}</el-descriptions-item>
          <el-descriptions-item label="所属文章">
            {{ detail.articleTitle }}（{{ detail.articleId }}）
          </el-descriptions-item>
          <el-descriptions-item label="回复目标">
            {{ detail.parentId || '根评论' }}
          </el-descriptions-item>
          <el-descriptions-item label="作者">
            {{ detail.author?.nickname || '已删除用户' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" kind="review" />
          </el-descriptions-item>
        </el-descriptions>
        <p class="plain-content">{{ detail.content }}</p>
      </template>
    </el-dialog>
  </section>
</template>
