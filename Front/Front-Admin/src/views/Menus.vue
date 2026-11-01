<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOptions, request } from '../api.js'
import { can } from '../session.js'
import { flattenMenus, pageRegistry } from '../navigation.js'
import { nullable } from '../format.js'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'

const rows = ref([])
const permissions = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const dialog = ref(false)
const id = ref('')
const revision = ref('')
const icons = [
  'document',
  'folder',
  'tag',
  'chat',
  'link',
  'picture',
  'music',
  'mail',
  'user',
  'lock',
  'menu',
  'key',
  'setting',
  'home',
  'chart',
]
const form = reactive({
  parentId: null,
  menuCode: '',
  menuName: '',
  menuType: 'M',
  routeName: '',
  path: '',
  component: '',
  icon: '',
  permissionId: null,
  sortOrder: 0,
  isVisible: 1,
  keepAlive: 0,
  status: 1,
})
const parentOptions = computed(() =>
  flattenMenus(rows.value).filter(
    (node) =>
      node.menuId !== id.value &&
      (form.menuType === 'B' ? node.menuType === 'M' : node.menuType === 'D'),
  ),
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await request('/admin/menus')
    if (!Array.isArray(result.data)) {
      throw new Error('菜单树响应格式不正确。')
    }
    rows.value = result.data
    if (can('sys:permission:query')) {
      permissions.value = await getOptions('/admin/permissions')
    }
  } catch (failure) {
    error.value = failure.message
  } finally {
    loading.value = false
  }
}

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.menuId || ''
    if (row) {
      const result = await request(`/admin/menus/${row.menuId}`)
      revision.value = result.revision
      const data = result.data
      Object.assign(form, {
        parentId: data.parentId,
        menuCode: data.menuCode,
        menuName: data.menuName,
        menuType: data.menuType,
        routeName: data.routeName || '',
        path: data.path || '',
        component: data.component || '',
        icon: data.icon || '',
        permissionId: data.permissionId,
        sortOrder: data.sortOrder,
        isVisible: data.isVisible,
        keepAlive: data.keepAlive,
        status: data.status,
      })
    } else {
      Object.assign(form, {
        parentId: null,
        menuCode: '',
        menuName: '',
        menuType: 'M',
        routeName: '',
        path: '',
        component: '',
        icon: '',
        permissionId: null,
        sortOrder: 0,
        isVisible: 1,
        keepAlive: 0,
        status: 1,
      })
    }
    dialog.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

function changeType() {
  form.parentId = null
  form.keepAlive = 0
  if (form.menuType === 'B') {
    form.isVisible = 0
    form.routeName = ''
    form.path = ''
    form.component = ''
  } else {
    form.isVisible = 1
    form.component = form.menuType === 'D' ? 'Layout' : ''
  }
  if (form.menuType === 'D') {
    form.permissionId = null
  }
}

async function save() {
  formError.value = ''
  if (form.menuType !== 'D' && !form.permissionId) {
    formError.value = '页面或按钮必须选择对应权限。'
    return
  }
  if (form.menuType === 'B' && !form.parentId) {
    formError.value = '按钮必须归属于一个页面。'
    return
  }
  saving.value = true
  try {
    const body = {
      parentId: form.parentId,
      menuName: form.menuName,
      menuType: form.menuType,
      routeName: form.menuType === 'B' ? null : form.routeName,
      path: form.menuType === 'B' ? null : form.path,
      component: form.menuType === 'B' ? null : form.component,
      icon: nullable(form.icon),
      permissionId: form.menuType === 'D' ? null : form.permissionId,
      sortOrder: form.sortOrder,
      isVisible: form.menuType === 'B' ? 0 : form.isVisible,
      keepAlive: form.menuType === 'M' ? form.keepAlive : 0,
      status: form.status,
    }
    if (!id.value) {
      body.menuCode = form.menuCode
    }
    await request(id.value ? `/admin/menus/${id.value}` : '/admin/menus', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    dialog.value = false
    ElMessage.success('菜单已保存')
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
      `删除菜单「${row.menuName}」？请先移除子节点与相关角色授权。`,
      '删除菜单',
      { confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const path = `/admin/menus/${row.menuId}`
    const current = await request(path)
    await request('/admin/menus/batch-delete', { method: 'POST', body: { items: [{ id: row.menuId, revision: current.revision }] } })
    ElMessage.success('菜单已删除')
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
  if (can('sys:menu:delete')) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
      warning: '仅可删除叶子节点，同时清理角色菜单关联，不删除接口权限。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">SYSTEM / MENUS</p>
        <h1>菜单管理</h1>
        <p class="muted-text">组织目录、页面和按钮；菜单隐藏不会撤销接口权限。</p>
      </div>
      <el-button v-if="can('sys:menu:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建菜单
      </el-button>
    </div>
    <RequestError :message="error" @retry="load" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>完整菜单树</h2>
        <el-button text aria-label="刷新菜单" @click="load"><AdminIcon name="refresh" /></el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/menus" id-key="menuId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
        :selectable="row => !row.children?.length"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="menuId"
        :tree-props="{ children: 'children' }"
        empty-text="暂无菜单"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="menuName" label="菜单名称" min-width="230" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag
              :type="{ D: 'warning', M: 'primary', B: 'success' }[row.menuType]"
              :class="{ 'tag-page': row.menuType === 'M' }"
              size="small"
            >
              {{ { D: '目录', M: '页面', B: '按钮' }[row.menuType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="180" />
        <el-table-column prop="component" label="组件键" min-width="210" show-overflow-tooltip />
        <el-table-column label="显示" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isVisible ? 'success' : 'info'" size="small">
              {{ row.isVisible ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="enabled" /></template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button type="primary" v-if="can('sys:menu:update')" link @click="edit(row)">编辑</el-button>
            <el-button type="danger" v-if="can('sys:menu:delete')" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog
      v-model="dialog"
      :title="id ? '编辑菜单' : '新建菜单'"
      width="680px"
      :close-on-click-modal="false"
      :show-close="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
        <div class="form-row">
          <label>
            菜单编码
            <el-input
              v-model="form.menuCode"
              :disabled="Boolean(id)"
              required
              maxlength="64"
              pattern="[A-Za-z0-9:_-]+"
            />
          </label>
          <label>
            菜单名称
            <el-input v-model="form.menuName" required maxlength="64" />
          </label>
        </div>
        <label>
          菜单类型
          <el-radio-group v-model="form.menuType" @change="changeType">
            <el-radio value="D">目录</el-radio>
            <el-radio value="M">页面</el-radio>
            <el-radio value="B">按钮</el-radio>
          </el-radio-group>
        </label>
        <label>
          上级节点
          <el-select
            v-model="form.parentId"
            clearable
            filterable
            placeholder="根节点"
            @clear="form.parentId = null"
          >
            <el-option
              v-for="parent in parentOptions"
              :key="parent.menuId"
              :value="parent.menuId"
              :label="parent.menuName"
            />
          </el-select>
        </label>
        <template v-if="form.menuType !== 'B'">
          <div class="form-row">
            <label>
              路由名称
              <el-input
                v-model="form.routeName"
                required
                maxlength="64"
                pattern="[A-Za-z][A-Za-z0-9_]*"
              />
            </label>
            <label>
              路由路径
              <el-input
                v-model="form.path"
                required
                maxlength="255"
                pattern="/(?!/)[A-Za-z0-9/_-]*"
              />
            </label>
          </div>
          <label>
            本地组件键
            <el-select v-model="form.component" filterable>
              <el-option v-if="form.menuType === 'D'" value="Layout" label="目录布局 · Layout" />
              <el-option
                v-for="(entry, componentKey) in pageRegistry"
                v-else
                :key="componentKey"
                :value="componentKey"
                :label="`${entry.title} · ${componentKey}`"
              />
            </el-select>
          </label>
        </template>
        <label v-if="form.menuType !== 'D' && can('sys:permission:query')">
          对应权限
          <el-select v-model="form.permissionId" filterable>
            <el-option
              v-for="permission in permissions"
              :key="permission.permissionId"
              :value="permission.permissionId"
              :label="`${permission.permissionName} · ${permission.permissionCode}`"
            />
          </el-select>
        </label>
        <label v-else-if="form.menuType !== 'D'">
          权限 ID
          <el-input v-model="form.permissionId" required pattern="[1-9][0-9]{0,18}" />
        </label>
        <div class="form-row">
          <label>
            图标
            <el-select v-model="form.icon" clearable>
              <el-option v-for="icon in icons" :key="icon" :value="icon" :label="icon" />
            </el-select>
          </label>
          <label>
            排序
            <el-input-number
              v-model="form.sortOrder"
              :min="-2147483648"
              :max="2147483647"
              :precision="0"
            />
          </label>
        </div>
        <div class="form-row">
          <label v-if="form.menuType !== 'B'">
            菜单显示
            <el-switch v-model="form.isVisible" :active-value="1" :inactive-value="0" />
          </label>
          <label v-if="form.menuType === 'M'">
            缓存页面
            <el-switch v-model="form.keepAlive" :active-value="1" :inactive-value="0" />
          </label>
          <label>
            启用状态
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          </label>
        </div>
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存菜单</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>

<style scoped>
/* 主题把 --el-color-primary 改成了黑色，type="primary" 的「页面」标签会渲染成灰白；这里单独给回蓝色 */
.el-tag.el-tag--primary.tag-page {
  --el-tag-bg-color: #ecf5ff;
  --el-tag-border-color: #d9ecff;
  --el-tag-text-color: #2b6cb0;
}
</style>
