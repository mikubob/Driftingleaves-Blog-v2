<script setup>
import ImagePreview from '../components/ImagePreview.vue'
import { onMounted, reactive, ref, computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElDialog,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/switch/style/css'
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
  '/admin/home/social-links',
  { keyword: '', status: '' },
)
const dialog = ref(false)
const saving = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const form = reactive({ name: '', icon: '', url: '', sortOrder: 0, status: 1 })

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.socialId || ''
    if (row) {
      const result = await request(`/admin/home/social-links/${row.socialId}`)
      revision.value = result.revision
      const data = result.data
      Object.assign(form, {
        name: data.name,
        icon: data.icon || '',
        url: data.url,
        sortOrder: data.sortOrder,
        status: data.status,
      })
    } else {
      Object.assign(form, { name: '', icon: '', url: '', sortOrder: 0, status: 1 })
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
      name: form.name,
      icon: nullable(form.icon),
      url: form.url,
      sortOrder: form.sortOrder,
    }
    if (!id.value) {
      body.status = form.status
    }
    await request(id.value ? `/admin/home/social-links/${id.value}` : '/admin/home/social-links', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    dialog.value = false
    ElMessage.success('社交链接已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除社交链接', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const detail = await request(`/admin/home/social-links/${row.socialId}`)
    await request('/admin/home/social-links/batch-delete', { method: 'POST', body: { items: [{ id: row.socialId, revision: detail.revision }] } })
    ElMessage.success('社交链接已删除')
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
  if (can('home:social:update', true)) {
    actions.push(
      { label: '批量展示', type: 'success', path: 'batch-status', method: 'PUT', body: { status: 1 } },
      { label: '批量隐藏', type: 'warning', path: 'batch-status', method: 'PUT', body: { status: 0 } },
    )
  }
  if (can('home:social:delete', true)) {
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
        <h1>社交链接管理</h1>
      </div>
      <el-button v-if="can('home:social:create', true)" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建社交链接
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
          <el-option label="IN展示中" :value="0" />
        </el-select>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>社交链接列表</h2>
        <el-button text aria-label="刷新列表" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/home/social-links" id-key="socialId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="socialId"
        border
        stripe
        empty-text="暂无社交链接"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="name" label="平台名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="url" label="链接地址" min-width="280" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" min-width="80" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="display" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <RowStatusActions :row="row" :actions="batchActions" :batch="batch" :disabled="loading" />
            <el-button type="primary" v-if="can('home:social:update', true)" link @click="edit(row)">
              编辑
            </el-button>
            <el-button type="danger" v-if="can('home:social:delete', true)" link @click="remove(row)">
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
        :loading="loading"
        @change="changePage"
        @size-change="changePageSize"
      />
    </el-card>
    <el-dialog
      v-model="dialog"
      :title="id ? '编辑社交链接' : '新建社交链接'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving"
      :close-on-press-escape="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          平台名称
          <el-input v-model="form.name" type="text" maxlength="64" required />
        </label>
        <label>
          链接地址（HTTP / HTTPS / mailto）
          <el-input v-model="form.url" type="text" maxlength="2048" required />
        </label>
        <label>
          图标键或外部图片地址
          <el-input v-model="form.icon" type="text" maxlength="2048" />
          <ImagePreview :src="form.icon" alt="社交图标预览" />
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
          展示状态
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存社交链接</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
