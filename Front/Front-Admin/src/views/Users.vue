<script setup>
import DisplayImageField from '../components/DisplayImageField.vue'
import UserDetail from '../components/UserDetail.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElCard,
  ElDialog,
  ElInput,
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
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import { getOptions, request } from '../api.js'
import { can } from '../session.js'
import { prepareBatch, syncRowStatus } from '../batch.js'
import { formatTime } from '../format.js'
import { nullable, validatePassword } from '../form.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import BatchActions from '../components/BatchActions.vue'
import RowStatusActions from '../components/RowStatusActions.vue'
import RequestError from '../components/RequestError.vue'
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'

const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/users',
  { keyword: '', status: '', roleId: '' },
)
const batch = ref(null)
const batchTable = ref(null)
const deleting = ref(false)
const batchActions = computed(() => {
  const actions = []
  if (can('sys:user:update')) {
    actions.push(
      { label: '批量启用', type: 'success', path: 'batch-enable', method: 'PUT', targetStatus: 1 },
      {
        label: '批量禁用', type: 'warning', path: 'batch-disable', method: 'PUT', targetStatus: 0,
        warning: '将撤销目标账号的全部会话，必须保留至少一个有效管理员。',
      },
    )
  }
  if (can('sys:user:delete')) {
    actions.push({
      label: '批量删除', path: 'batch-delete', danger: true,
      warning: '用户及角色、绑定、点赞和订阅将永久删除。存在内容引用或会失去最后管理员时整批拒绝。',
    })
  }
  return actions
})
const detailUserId = ref('')
const dialog = ref(false)
const roleDialog = ref(false)
const saving = ref(false)
const uploading = ref(false)
const id = ref('')
const revision = ref('')
const initialStatus = ref(1)
const roles = ref([])
const roleIds = ref([])
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  avatar: '',
  intro: '',
  status: 1,
})

async function edit(row) {
  form.password = ''
  try {
    id.value = row?.userId || ''
    if (row) {
      const result = await request(`/admin/users/${row.userId}`)
      revision.value = result.revision
      const user = result.data
      initialStatus.value = user.status
      Object.assign(form, {
        username: user.username,
        nickname: user.nickname,
        email: user.email || '',
        avatar: user.avatar || '',
        intro: user.intro || '',
        status: user.status,
      })
    } else {
      initialStatus.value = 1
      Object.assign(form, {
        username: '',
        password: '',
        nickname: '',
        email: '',
        avatar: '',
        intro: '',
        status: 1,
      })
    }
    dialog.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function save() {
  if (saving.value || uploading.value) {
    return
  }
  if (!id.value && !validatePassword(form.password)) {
    ElMessage.error('密码须为 8–64 个字符且 UTF-8 不超过 72 字节。')
    return
  }
  saving.value = true
  try {
    const body = {
      nickname: form.nickname,
      email: nullable(form.email),
      avatar: nullable(form.avatar),
      intro: nullable(form.intro),
    }
    if (!id.value) {
      body.status = form.status
      body.username = form.username
      body.password = form.password
    }
    const saved = await request(id.value ? `/admin/users/${id.value}` : '/admin/users', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    if (saved?.revision) {
      revision.value = saved.revision
    }
    if (id.value && form.status !== initialStatus.value) {
      await syncRowStatus(
        '/admin/users',
        'userId',
        id.value,
        saved?.revision,
        form.status === 1 ? 'batch-enable' : 'batch-disable',
      )
      initialStatus.value = form.status
    }
    form.password = ''
    dialog.value = false
    ElMessage.success('用户资料已保存')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    saving.value = false
  }
}

async function assignRoles(row) {
  try {
    const [current, options] = await Promise.all([
      request(`/admin/users/${row.userId}`),
      getOptions('/admin/roles'),
    ])
    id.value = row.userId
    revision.value = current.revision
    roles.value = options
    roleIds.value = [...current.data.roleIds]
    roleDialog.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function saveRoles() {
  saving.value = true
  try {
    await request(`/admin/users/${id.value}/roles`, {
      method: 'PUT',
      revision: revision.value,
      body: { roleIds: [...roleIds.value] },
    })
    roleDialog.value = false
    ElMessage.success('用户角色已更新，受影响会话需重新登录')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    saving.value = false
  }
}

/** 单个删除也使用批量接口，先取得版本再确认，失败时保留当前列表。 */
async function remove(row) {
  if (deleting.value || batch.value?.busy) {
    return
  }
  deleting.value = true
  try {
    const body = await prepareBatch('/admin/users', 'userId', [row])
    try {
      await ElMessageBox.confirm(
        `永久删除用户「${row.nickname}」（${row.username}）？角色、绑定、点赞和订阅将一并删除；存在内容引用时无法删除。`,
        '删除用户',
        { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
    await request('/admin/users/batch-delete', { method: 'POST', body })
    ElMessage.success('用户已删除')
    await load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  load()
  if (can('sys:role:query')) {
    try {
      roles.value = await getOptions('/admin/roles')
    } catch (failure) {
      ElMessage.error(`角色选项加载失败：${failure.message}`)
    }
  }
})

/** 切换顶部账号状态分栏并立即查询第一页。 */
function switchStatus(status) {
  filters.status = status
  load(true)
}
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>用户管理</h1>
      </div>
      <el-button v-if="can('sys:user:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        创建用户
      </el-button>
    </div>
    <div class="page-tabs">
      <button
        v-for="tab in [
          { value: '', label: '全部用户' },
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
          aria-label="用户关键词"
          placeholder="搜索用户名、昵称或邮箱"
          maxlength="100"
          clearable
        />
        <el-select
          v-if="can('sys:role:query')"
          v-model="filters.roleId"
          aria-label="角色"
          placeholder="全部角色"
          clearable
          filterable
        >
          <el-option
            v-for="role in roles"
            :key="role.roleId"
            :label="role.roleName"
            :value="role.roleId"
          />
        </el-select>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>用户列表</h2>
        <div class="heading-actions">
          <span class="muted-text">停用账号将撤销该账号的全部会话</span>
          <el-button text aria-label="刷新列表" @click="load()">
            <AdminIcon name="refresh" />
          </el-button>
        </div>
      </div>
      <BatchActions
        ref="batch"
        resource="/admin/users"
        id-key="userId"
        :rows="rows"
        :table="batchTable"
        :actions="batchActions"
        :disabled="loading || !!error || deleting"
        @completed="load()"
      />
      <el-table
        ref="batchTable"
        v-if="!error"
        v-loading="loading || deleting || batch?.busy"
        @selection-change="batch?.select"
        :data="rows"
        row-key="userId"
        border
        stripe
        empty-text="暂无用户"
      >
        <el-table-column
          v-if="batchActions.length"
          type="selection"
          width="48"
          fixed="left"
          :selectable="batch?.selectable"
        />
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <strong>{{ row.nickname }}</strong>
            <p class="table-secondary">{{ row.username }}</p>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户 ID" min-width="180" />
        <el-table-column prop="email" label="联系邮箱" min-width="210" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="enabled" /></template>
        </el-table-column>
        <el-table-column label="最近登录" width="180">
          <template #default="{ row }">{{ formatTime(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="最近登录 IP" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastLoginIp || '—' }}</template>
        </el-table-column>
        <el-table-column label="最近登录地域" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastLoginLocation || '未知' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="320">
          <template #default="{ row }">
            <el-button
              v-if="can('sys:user:query')"
              type="primary"
              link
              :disabled="deleting || batch?.busy"
              @click="detailUserId = row.userId"
            >详情</el-button>
            <el-button
              type="primary"
              v-if="can('sys:user:update')"
              link
              :disabled="deleting || batch?.busy"
              @click="edit(row)"
            >编辑</el-button>
            <RowStatusActions
              :row="row"
              :actions="batchActions"
              :batch="batch"
              :target-label="`用户「${row.username}」`"
              :disabled="loading || deleting"
            />
            <el-button
              type="primary"
              v-if="can('sys:user:assignRole') && can('sys:role:query')"
              link
              :disabled="deleting || batch?.busy"
              @click="assignRoles(row)"
            >
              分配角色
            </el-button>
            <el-button
              v-if="can('sys:user:delete')"
              link
              type="danger"
              :disabled="deleting || batch?.busy"
              @click="remove(row)"
            >删除</el-button>
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
    <UserDetail
      v-if="detailUserId"
      :key="detailUserId"
      :user-id="detailUserId"
      @close="detailUserId = ''"
    />
    <el-dialog
      v-model="dialog"
      :title="id ? '编辑用户' : '创建用户'"
      width="600px"
      :close-on-click-modal="false"
      :show-close="!saving && !uploading"
      :close-on-press-escape="!saving && !uploading"
      @closed="form.password = ''"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          用户名
          <el-input
            v-model="form.username"
            :disabled="Boolean(id)"
            required
            minlength="3"
            maxlength="64"
            pattern="[A-Za-z0-9_]{3,64}"
            autocomplete="off"
          />
        </label>
        <label v-if="!id">
          初始密码
          <el-input
            v-model="form.password"
            type="password"
            show-password
            required
            autocomplete="new-password"
          />
        </label>
        <label>
          昵称
          <el-input v-model="form.nickname" required maxlength="64" />
        </label>
        <label>
          联系邮箱
          <el-input v-model="form.email" type="email" maxlength="254" />
        </label>
        <DisplayImageField
          usage="AVATAR"
          v-model="form.avatar"
          v-model:uploading="uploading"
          label="头像地址"
          :disabled="saving"
        />
        <label>
          个人简介
          <el-input v-model="form.intro" type="textarea" :rows="3" maxlength="500" />
        </label>
        <label>
          启用账号
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            :disabled="Boolean(id) && !can('sys:user:update')"
          />
        </label>
        <div class="dialog-actions">
          <el-button :disabled="saving || uploading" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="uploading">保存用户</el-button>
        </div>
      </form>
    </el-dialog>
    <el-dialog
      v-model="roleDialog"
      title="分配用户角色"
      width="600px"
      :close-on-click-modal="false"
      :show-close="!saving"
    >
      <form class="edit-form" @submit.prevent="saveRoles">
        <p class="muted-text">
          整体替换当前角色，清空选择将移除全部角色。禁用角色需取消后才能保存。
        </p>
        <el-select v-model="roleIds" multiple filterable aria-label="用户角色">
          <el-option
            v-for="role in roles"
            :key="role.roleId"
            :label="`${role.roleName}${role.status ? '' : '（已禁用）'}`"
            :value="role.roleId"
            :disabled="role.status !== 1 && !roleIds.includes(role.roleId)"
          />
        </el-select>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="roleDialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存角色</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
