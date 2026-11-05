<script setup>
import ResourceDetail from '../components/ResourceDetail.vue'
import ImagePreview from '../components/ImagePreview.vue'
import { onMounted, reactive, ref, computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElDescriptionsItem,
  ElDialog,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime } from '../format.js'
import { nullable } from '../form.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
import RowStatusActions from '../components/RowStatusActions.vue'
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'

const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/links',
  { keyword: '', status: '' },
)
const detailId = ref('')
const dialog = ref(false)
const saving = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const form = reactive({
  linkName: '',
  url: '',
  avatar: '',
  description: '',
  sortOrder: 0,
  status: 0,
})

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.linkId || ''
    if (row) {
      const result = await request(`/admin/links/${row.linkId}`)
      revision.value = result.revision
      const data = result.data
      Object.assign(form, {
        linkName: data.linkName,
        url: data.url,
        avatar: data.avatar || '',
        description: data.description || '',
        sortOrder: data.sortOrder,
        status: data.status,
      })
    } else {
      Object.assign(form, {
        linkName: '',
        url: '',
        avatar: '',
        description: '',
        sortOrder: 0,
        status: 0,
      })
    }
    dialog.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function save() {
  formError.value = ''

  saving.value = true
  try {
    const body = {
      linkName: form.linkName,
      url: form.url,
      avatar: nullable(form.avatar),
      description: nullable(form.description),
      sortOrder: form.sortOrder,
    }
    if (!id.value) {
      body.status = form.status
    }
    await request(id.value ? `/admin/links/${id.value}` : '/admin/links', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    dialog.value = false
    ElMessage.success('友链已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.linkName}」？`, '删除友链', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const detail = await request(`/admin/links/${row.linkId}`)
    await request('/admin/links/batch-delete', { method: 'POST', body: { items: [{ id: row.linkId, revision: detail.revision }] } })
    ElMessage.success('友链已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

onMounted(load)

const batch = ref(null)
const batchTable = ref(null)
const batchActions = computed(() => {
  const actions = []
  if (can('blog:link:update')) {
    actions.push(
      { label: '批量通过', type: 'success', path: 'batch-status', method: 'PUT', body: { status: 1 } },
      { label: '批量下架', type: 'warning', path: 'batch-status', method: 'PUT', body: { status: 2 } },
      { label: '批量设为待审', path: 'batch-status', method: 'PUT', body: { status: 0 } },
    )
  }
  if (can('blog:link:delete', true)) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>友链管理</h1>
      </div>
      <el-button v-if="can('blog:link:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建友链
      </el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="搜索关键词"
          placeholder="搜索名称或标题"
          maxlength="100"
          clearable
        />
        <el-select v-model="filters.status" aria-label="状态" placeholder="全部状态" clearable>
          <el-option label="展示中" :value="1" />
          <el-option label="待审核" :value="0" />
          <el-option label="已下架" :value="2" />
        </el-select>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>友链列表</h2>
        <el-button text aria-label="刷新列表" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/links" id-key="linkId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="linkId"
        border
        stripe
        empty-text="暂无友链"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="linkName" label="站点名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="url" label="站点地址" min-width="240" show-overflow-tooltip />
        <el-table-column prop="description" label="介绍" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="link" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="can('blog:link:query')"
              type="primary"
              link
              :disabled="batch?.busy"
              @click="detailId = row.linkId"
            >详情</el-button>
            <RowStatusActions :row="row" :actions="batchActions" :batch="batch" :disabled="loading" />
            <el-button type="primary" v-if="can('blog:link:update')" link @click="edit(row)">编辑</el-button>
            <el-button type="danger" v-if="can('blog:link:delete')" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPager
        :page="page"
        :page-size="pageSize"
        :total="total"
        :total-pages="totalPages"
        :loading="loading"
        @change="changePage"
        @size-change="changePageSize"
      />
    </el-card>
    <ResourceDetail
      v-if="detailId"
      :key="detailId"
      :path="`/admin/links/${detailId}`"
      title="友链详情"
      @close="detailId = ''"
    >
      <template #default="{ detail }">
        <el-descriptions-item label="友链 ID">{{ detail.linkId }}</el-descriptions-item>
        <el-descriptions-item label="站点名称">{{ detail.linkName }}</el-descriptions-item>
        <el-descriptions-item label="站点地址">{{ detail.url }}</el-descriptions-item>
        <el-descriptions-item label="头像">
          <ImagePreview v-if="detail.avatar" :src="detail.avatar" alt="友链头像" />
          <span v-else>未填写</span>
        </el-descriptions-item>
        <el-descriptions-item label="站点介绍">
          <p class="detail-text">{{ detail.description || '未填写' }}</p>
        </el-descriptions-item>
        <el-descriptions-item label="排序">{{ detail.sortOrder }}</el-descriptions-item>
        <el-descriptions-item label="审核状态"><StatusTag :value="detail.status" kind="link" /></el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
      </template>
    </ResourceDetail>
    <el-dialog
      v-model="dialog"
      :title="id ? '编辑友链' : '新建友链'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving"
      :close-on-press-escape="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          站点名称
          <el-input v-model="form.linkName" type="text" maxlength="100" required />
        </label>
        <label>
          站点地址
          <el-input v-model="form.url" type="url" maxlength="2048" required />
        </label>
        <label>
          头像地址
          <el-input v-model="form.avatar" type="url" maxlength="2048" />
          <ImagePreview :src="form.avatar" alt="友链头像预览" />
        </label>
        <label>
          站点介绍
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="255" />
        </label>
        <label>
          排序
          <el-input-number
            v-model="form.sortOrder"
            :precision="0"
            :min="-2147483648"
            :max="2147483647"
          />
        </label>
        <label v-if="!id">
          审核状态
          <el-select v-model="form.status">
            <el-option label="待审核" :value="0" />
            <el-option label="展示中" :value="1" />
            <el-option label="驳回 / 下架" :value="2" />
          </el-select>
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存友链</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
