<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOptions, request } from '../api.js'
import { can } from '../session.js'
import { flattenMenus } from '../navigation.js'
import { nullable } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'

const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/roles',
  { keyword: '', status: '' },
)
const dialog = ref(false)
const grantsDialog = ref(false)
const saving = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const permissions = ref([])
const menus = ref([])
const permissionIds = ref([])
const menuIds = ref([])
const form = reactive({ roleCode: '', roleName: '', description: '', dataScope: 'SELF', status: 1 })

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.roleId || ''
    if (row) {
      const result = await request(`/admin/roles/${row.roleId}`)
      revision.value = result.revision
      const data = result.data
      Object.assign(form, {
        roleCode: data.roleCode,
        roleName: data.roleName,
        description: data.description || '',
        dataScope: data.dataScope,
        status: data.status,
      })
    } else {
      Object.assign(form, {
        roleCode: '',
        roleName: '',
        description: '',
        dataScope: 'SELF',
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
  formError.value = ''
  try {
    const body = {
      roleName: form.roleName,
      description: nullable(form.description),
      dataScope: form.dataScope,
      status: form.status,
    }
    if (!id.value) {
      body.roleCode = form.roleCode
    }
    await request(id.value ? `/admin/roles/${id.value}` : '/admin/roles', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    dialog.value = false
    ElMessage.success('角色已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function grant(row) {
  formError.value = ''
  try {
    const [current, dictionary, menuResult] = await Promise.all([
      request(`/admin/roles/${row.roleId}`),
      getOptions('/admin/permissions'),
      request('/admin/menus'),
    ])
    id.value = row.roleId
    revision.value = current.revision
    permissions.value = dictionary
    menus.value = flattenMenus(menuResult.data)
    permissionIds.value = [...current.data.permissionIds]
    menuIds.value = [...current.data.menuIds]
    grantsDialog.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function saveGrants() {
  saving.value = true
  formError.value = ''
  try {
    await request(`/admin/roles/${id.value}/grants`, {
      method: 'PUT',
      revision: revision.value,
      body: { permissionIds: [...permissionIds.value], menuIds: [...menuIds.value] },
    })
    grantsDialog.value = false
    ElMessage.success('角色授权已更新，受影响会话需重新登录')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除角色「${row.roleName}」？已分配用户将失去此角色的授权。`,
      '删除角色',
      { confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const path = `/admin/roles/${row.roleId}`
    const current = await request(path)
    await request('/admin/roles/batch-delete', { method: 'POST', body: { items: [{ id: row.roleId, revision: current.revision }] } })
    ElMessage.success('角色已删除')
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
  if (can('sys:role:delete')) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
      warning: '用户将失去所选角色授权，受影响账号需要重新登录。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">SYSTEM / ROLES</p>
        <h1>角色管理</h1>
        <p class="muted-text">明确每个角色的权限、菜单与数据访问范围。</p>
      </div>
      <el-button v-if="can('sys:role:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        创建角色
      </el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="角色关键词"
          placeholder="搜索角色名称或编码"
          maxlength="100"
          clearable
        />
        <el-select v-model="filters.status" aria-label="角色状态" placeholder="全部状态" clearable>
          <el-option label="已启用" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
        <el-button native-type="submit" type="primary">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading"><h2>角色列表</h2></div>
            <BatchActions
        ref="batch" resource="/admin/roles" id-key="roleId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
        :selectable="row => !['admin', 'author', 'member'].includes(row.roleCode)"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="roleId"
        empty-text="暂无角色"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="数据范围" width="140">
          <template #default="{ row }">
            {{ row.dataScope === 'ALL' ? '全部数据' : '本人数据' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="enabled" /></template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button v-if="can('sys:role:update')" link @click="edit(row)">编辑</el-button>
            <el-button
              v-if="can('sys:role:grant') && can('sys:permission:query') && can('sys:menu:query')"
              link
              @click="grant(row)"
            >
              授权
            </el-button>
            <el-button v-if="can('sys:role:delete')" link @click="remove(row)">删除</el-button>
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
      :title="id ? '编辑角色' : '创建角色'"
      width="580px"
      :close-on-click-modal="false"
      :show-close="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          角色编码
          <el-input
            v-model="form.roleCode"
            required
            :disabled="Boolean(id)"
            pattern="[a-z][a-z0-9_]{0,63}"
            maxlength="64"
          />
        </label>
        <label>
          角色名称
          <el-input v-model="form.roleName" required maxlength="64" />
        </label>
        <label>
          说明
          <el-input v-model="form.description" type="textarea" maxlength="255" />
        </label>
        <label>
          数据范围
          <el-radio-group v-model="form.dataScope">
            <el-radio value="SELF">本人 SELF</el-radio>
            <el-radio value="ALL">全部 ALL</el-radio>
          </el-radio-group>
        </label>
        <label>
          启用状态
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存角色</el-button>
        </div>
      </form>
    </el-dialog>
    <el-dialog
      v-model="grantsDialog"
      title="配置角色权限与菜单"
      width="800px"
      :close-on-click-modal="false"
      :show-close="!saving"
    >
      <form class="edit-form" @submit.prevent="saveGrants">
        <el-alert
          title="权限与菜单分别授权。选择页面或按钮后，须同时选择其对应权限；保存会整体替换，禁用项需移除。"
          type="info"
          :closable="false"
        />
        <label>
          接口权限
          <el-select
            v-model="permissionIds"
            multiple
            filterable
            aria-label="接口权限"
            collapse-tags
            :max-collapse-tags="8"
          >
            <el-option
              v-for="permission in permissions"
              :key="permission.permissionId"
              :value="permission.permissionId"
              :label="`${permission.permissionName} · ${permission.permissionCode}${permission.status ? '' : '（禁用）'}`"
              :disabled="
                permission.status !== 1 && !permissionIds.includes(permission.permissionId)
              "
            />
          </el-select>
        </label>
        <label>
          菜单与按钮
          <el-select
            v-model="menuIds"
            multiple
            filterable
            aria-label="菜单与按钮"
            collapse-tags
            :max-collapse-tags="8"
          >
            <el-option
              v-for="menu in menus"
              :key="menu.menuId"
              :value="menu.menuId"
              :label="`${menu.menuName} · ${menu.menuType}${menu.permissionCode ? ` · ${menu.permissionCode}` : ''}${menu.status ? '' : '（禁用）'}`"
              :disabled="menu.status !== 1 && !menuIds.includes(menu.menuId)"
            />
          </el-select>
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="grantsDialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存授权</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
