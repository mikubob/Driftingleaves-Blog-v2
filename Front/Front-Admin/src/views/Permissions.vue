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
  '/admin/permissions',
  { keyword: '', status: '', module: '' },
)
const dialog = ref(false)
const saving = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const form = reactive({
  permissionCode: '',
  permissionName: '',
  module: '',
  description: '',
  status: 1,
})

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.permissionId || ''
    if (row) {
      const result = await request(`/admin/permissions/${row.permissionId}`)
      revision.value = result.revision
      const data = result.data
      Object.assign(form, {
        permissionCode: data.permissionCode,
        permissionName: data.permissionName,
        module: data.module,
        description: data.description || '',
        status: data.status,
      })
    } else {
      Object.assign(form, {
        permissionCode: '',
        permissionName: '',
        module: '',
        description: '',
        status: 1,
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
    await request(id.value ? `/admin/permissions/${id.value}` : '/admin/permissions', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body: {
        ...(id.value ? {} : { permissionCode: form.permissionCode }),
        permissionName: form.permissionName,
        module: form.module,
        description: nullable(form.description),
        status: form.status,
      },
    })
    dialog.value = false
    ElMessage.success('权限已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.permissionName}」？`, '删除权限', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const detail = await request(`/admin/permissions/${row.permissionId}`)
    await request('/admin/permissions/batch-delete', { method: 'POST', body: { items: [{ id: row.permissionId, revision: detail.revision }] } })
    ElMessage.success('权限已删除')
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
  if (can('sys:permission:delete')) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
      warning: '固定后端权限和被菜单引用的权限不可删除，受影响会话将退出登录。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">PERMISSIONS</p>
        <h1>权限管理</h1>
        <p class="muted-text">SUB权限</p>
      </div>
      <el-button v-if="can('sys:permission:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建权限
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
          <el-option label="已启用" :value="1" />
          <el-option label="IN已启用" :value="0" />
        </el-select>
        <el-input
          v-model="filters.module"
          aria-label="权限模块"
          placeholder="权限模块，例如 sys"
          pattern="[a-z][a-z0-9_]{0,31}"
          maxlength="32"
          clearable
        />
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>权限列表</h2>
        <el-button text aria-label="刷新列表" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/permissions" id-key="permissionId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="permissionId"
        border
        stripe
        empty-text="暂无权限"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column
          prop="permissionName"
          label="权限名称"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="permissionCode"
          label="权限码"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column prop="module" label="模块" min-width="100" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="enabled" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" v-if="can('sys:permission:update')" link @click="edit(row)">编辑</el-button>
            <el-button type="danger" v-if="can('sys:permission:delete')" link @click="remove(row)">
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
      :title="id ? '编辑权限' : '新建权限'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving"
      :close-on-press-escape="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          权限码
          <el-input
            v-model="form.permissionCode"
            required
            maxlength="100"
            :disabled="Boolean(id)"
            placeholder="模块:资源:动作"
          />
        </label>
        <label>
          权限名称
          <el-input v-model="form.permissionName" type="text" maxlength="64" required />
        </label>
        <label>
          模块标识
          <el-input v-model="form.module" type="text" maxlength="32" required />
        </label>
        <label>
          说明
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="255" />
        </label>
        <label>
          展示状态
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存权限</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
