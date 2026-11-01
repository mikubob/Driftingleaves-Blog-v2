<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime, nullable } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
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
    await request(id.value ? `/admin/home/social-links/${id.value}` : '/admin/home/social-links', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body: {
        name: form.name,
        icon: nullable(form.icon),
        url: form.url,
        sortOrder: form.sortOrder,
        status: form.status,
      },
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
        <p class="eyebrow">HOME / SOCIAL-LINKS</p>
        <h1>社交链接管理</h1>
        <p class="muted-text">SUB社交链接</p>
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('home:social:update', true)" link @click="edit(row)">
              编辑
            </el-button>
            <el-button v-if="can('home:social:delete', true)" link @click="remove(row)">
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
        <label>
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
