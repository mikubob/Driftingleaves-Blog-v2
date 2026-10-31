<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOptions, request } from '../api.js'
import { can } from '../session.js'
import { formatTime, nullable, validatePassword } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'

const { filters, rows, page, total, totalPages, loading, error, load, reset, changePage } = useList(
  '/admin/users',
  { keyword: '', status: '', roleId: '' },
)
const dialog = ref(false)
const roleDialog = ref(false)
const saving = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
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
  formError.value = ''
  form.password = ''
  try {
    id.value = row?.userId || ''
    if (row) {
      const result = await request(`/admin/users/${row.userId}`)
      revision.value = result.revision
      const user = result.data
      Object.assign(form, {
        username: user.username,
        nickname: user.nickname,
        email: user.email || '',
        avatar: user.avatar || '',
        intro: user.intro || '',
        status: user.status,
      })
    } else {
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
  formError.value = ''
  if (!id.value && !validatePassword(form.password)) {
    formError.value = '密码须为 8–64 个字符且 UTF-8 不超过 72 字节。'
    return
  }
  saving.value = true
  try {
    const body = {
      nickname: form.nickname,
      email: nullable(form.email),
      avatar: nullable(form.avatar),
      intro: nullable(form.intro),
      status: form.status,
    }
    if (!id.value) {
      body.username = form.username
      body.password = form.password
    }
    await request(id.value ? `/admin/users/${id.value}` : '/admin/users', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    form.password = ''
    dialog.value = false
    ElMessage.success('用户资料已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function assignRoles(row) {
  formError.value = ''
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
  formError.value = ''
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
    formError.value = failure.message
  } finally {
    saving.value = false
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
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">SYSTEM / USERS</p>
        <h1>用户管理</h1>
        <p class="muted-text">管理本站账号、启用状态与角色分配。</p>
      </div>
      <el-button v-if="can('sys:user:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        创建用户
      </el-button>
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
        <el-select v-model="filters.status" aria-label="账号状态" placeholder="全部状态" clearable>
          <el-option label="已启用" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
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
        <span class="muted-text">停用账号将撤销该账号的全部会话</span>
      </div>
      <el-table
        v-if="!error"
        v-loading="loading"
        :data="rows"
        row-key="userId"
        empty-text="暂无用户"
      >
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
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button v-if="can('sys:user:update')" link @click="edit(row)">编辑</el-button>
            <el-button
              v-if="can('sys:user:assignRole') && can('sys:role:query')"
              link
              @click="assignRoles(row)"
            >
              分配角色
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPager
        :page="page"
        :total="total"
        :total-pages="totalPages"
        :loading="loading"
        @change="changePage"
      />
    </el-card>
    <el-dialog
      v-model="dialog"
      :title="id ? '编辑用户' : '创建用户'"
      width="600px"
      :close-on-click-modal="false"
      :show-close="!saving"
      :close-on-press-escape="!saving"
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
        <label>
          头像地址
          <el-input v-model="form.avatar" type="url" maxlength="2048" />
        </label>
        <label>
          个人简介
          <el-input v-model="form.intro" type="textarea" :rows="3" maxlength="500" />
        </label>
        <label>
          账号状态
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </label>
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存用户</el-button>
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
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="roleDialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存角色</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
