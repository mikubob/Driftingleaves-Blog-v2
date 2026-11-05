<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import { request } from '../api.js'
import { can } from '../session.js'
import RequestError from '../components/RequestError.vue'
import { useList } from '../composables/useList.js'
import DiscussionTable from '../components/DiscussionTable.vue'
import DiscussionDetail from '../components/DiscussionDetail.vue'

const initialFilters = { articleId: '', userId: '', status: '' }
const list = useList('/admin/comments', initialFilters)
const { error, load } = list
const filters = reactive({ ...initialFilters })
const table = ref(null)

function search() {
  Object.assign(list.filters, filters)
  return load(true)
}

function reset() {
  Object.assign(filters, initialFilters)
  return list.reset()
}

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
    await load()
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
    await load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}
onMounted(load)

const batchActions = computed(() => {
  const actions = []
  if (can('blog:comment:review')) {
    actions.push({
      label: '批量通过',
      type: 'success',
      path: 'batch-review',
      method: 'PUT',
      body: { status: 1 },
    }, {
      label: '批量驳回',
      type: 'warning',
      path: 'batch-review',
      method: 'PUT',
      body: { status: 2 },
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
        <h1>评论管理</h1>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" aria-label="筛选评论" @submit.prevent="search">
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
        </el-select>
        <el-button native-type="submit" type="primary" :disabled="table?.disabled">查询</el-button>
        <el-button :disabled="table?.disabled" @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <DiscussionTable ref="table" kind="comment" :list="list" :actions="batchActions" :busy="busy">
      <template #actions="{ row, disabled }">
        <el-button link :disabled="disabled" @click="view(row)">查看</el-button>
        <el-button
          v-if="can('blog:comment:review') && row.status !== 1"
          link type="success" :disabled="disabled" @click="review(row, 1)"
        >通过</el-button>
        <el-button
          v-if="can('blog:comment:review') && row.status !== 2"
          link type="warning" :disabled="disabled" @click="review(row, 2)"
        >驳回</el-button>
        <el-button
          v-if="can('blog:comment:moderate')" link type="danger" :disabled="disabled" @click="remove(row)"
        >删除</el-button>
      </template>
    </DiscussionTable>
    <el-dialog v-model="detailVisible" title="评论详情" width="580px" destroy-on-close :close-on-click-modal="false">
      <DiscussionDetail v-if="detail" kind="comment" :detail="detail" />
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>
