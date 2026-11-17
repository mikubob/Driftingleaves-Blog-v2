<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import { request } from '../api.js'
import { prepareBatch, syncRowStatus } from '../batch.js'
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
  '/admin/permissions',
  { keyword: '', status: '', module: '' },
)
const dialog = ref(false)
const saving = ref(false)
const id = ref('')
const revision = ref('')
const initialStatus = ref(1)
const form = reactive({
  permissionCode: '',
  permissionName: '',
  module: '',
  description: '',
  status: 1,
})

async function edit(row) {
  try {
    id.value = row?.permissionId || ''
    if (row) {
      const result = await request(`/admin/permissions/${row.permissionId}`)
      revision.value = result.revision
      const data = result.data
      initialStatus.value = data.status
      Object.assign(form, {
        permissionCode: data.permissionCode,
        permissionName: data.permissionName,
        module: data.module,
        description: data.description || '',
        status: data.status,
      })
    } else {
      initialStatus.value = 1
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
  saving.value = true
  try {
    const body = {
      permissionName: form.permissionName,
      module: form.module,
      description: nullable(form.description),
    }
    if (!id.value) {
      body.status = form.status
      body.permissionCode = form.permissionCode
    }
    const saved = await request(id.value ? `/admin/permissions/${id.value}` : '/admin/permissions', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    if (saved?.revision) {
      revision.value = saved.revision
    }
    if (id.value && form.status !== initialStatus.value) {
      await syncRowStatus('/admin/permissions', 'permissionId', id.value, saved?.revision, 'batch-status', {
        status: form.status,
      })
      initialStatus.value = form.status
    }
    dialog.value = false
    ElMessage.success('权限已保存')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
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
    const payload = await prepareBatch('/admin/permissions', 'permissionId', [row])
    await request('/admin/permissions/batch-delete', { method: 'POST', body: payload })
    ElMessage.success('权限已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

onMounted(load)

/** 切换顶部启用状态分栏并立即查询第一页。 */
function switchStatus(status) {
  filters.status = status
  load(true)
}

const batch = ref(null)
const batchTable = ref(null)
const batchActions = computed(() => {
  const actions = []
  if (can('sys:permission:update')) {
    actions.push(
      { label: '批量启用', type: 'success', path: 'batch-status', method: 'PUT', body: { status: 1 } },
      { label: '批量禁用', type: 'warning', path: 'batch-status', method: 'PUT', body: { status: 0 }, warning: '状态变化，相关用户的会话将被撤销，必须保留至少一个有效管理员。' },
    )
  }
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
        <h1>权限管理</h1>
      </div>
      <el-button v-if="can('sys:permission:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建权限
      </el-button>
    </div>
    <div class="page-tabs">
      <button
        v-for="tab in [
          { value: '', label: '全部权限' },
          { value: 1, label: '已启用' },
          { value: 0, label: '已禁用' },
        ]"
        :key="String(tab.value)"
        :class="{ active: filters.status === tab.value }"
        @click="switchStatus(tab.value)"
      >
        {{ tab.label }}
      </button>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="搜索关键词"
          placeholder="搜索权限名称或权限码"
          maxlength="100"
          clearable
        />
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
        <el-table-column label="说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="enabled" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" fixed="right">
          <template #default="{ row }">
            <RowStatusActions :row="row" :actions="batchActions" :batch="batch" :disabled="loading" />
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
          启用权限
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            :disabled="Boolean(id) && !can('sys:permission:update')"
          />
        </label>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存权限</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
