<script setup>
import ResourceDetail from '../components/ResourceDetail.vue'
import UserDetail from '../components/UserDetail.vue'
import ImagePreview from '../components/ImagePreview.vue'
import DisplayImageField from '../components/DisplayImageField.vue'
import { onBeforeUnmount, onDeactivated, onMounted, reactive, ref, computed } from 'vue'
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
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
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
import 'element-plus/es/components/radio/style/css'
import 'element-plus/es/components/radio-group/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'
import { getOptions, request } from '../api.js'
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
  '/admin/links',
  { keyword: '', status: '', source: '', applicantId: '' },
)
const detailId = ref('')
const detailUserId = ref('')
const dialog = ref(false)
const saving = ref(false)
const uploading = ref(false)
const id = ref('')
const revision = ref('')
const initialStatus = ref(0)
const users = ref(null)
const loadingUsers = ref(false)
const currentApplicant = ref(null)
const form = reactive({
  linkName: '',
  url: '',
  avatar: '',
  description: '',
  sortOrder: 0,
  status: 0,
  applicantId: '',
})

async function loadUsers() {
  if (!can('sys:user:query') || loadingUsers.value) {
    return
  }
  loadingUsers.value = true
  try {
    users.value = await getOptions('/admin/users', { status: 1 })
  } catch {
    users.value = null
    ElMessage.warning('账号列表加载失败，可直接填写用户 ID。')
  } finally {
    loadingUsers.value = false
  }
}

let editController

/** 取消未完成的友链详情拉取请求，防止快速切换记录时旧响应覆盖表单。 */
function cancelEditLoad() {
  editController?.abort()
  editController = undefined
}

onBeforeUnmount(cancelEditLoad)
onDeactivated(cancelEditLoad)

async function edit(row) {
  cancelEditLoad()
  if (!row) {
    id.value = ''
    revision.value = ''
    currentApplicant.value = null
    initialStatus.value = 0
    Object.assign(form, {
      linkName: '',
      url: '',
      avatar: '',
      description: '',
      sortOrder: 0,
      status: 0,
      applicantId: '',
    })
    dialog.value = true
    loadUsers()
    return
  }
  const controller = new AbortController()
  editController = controller
  try {
    const result = await request(`/admin/links/${row.linkId}`, { signal: controller.signal })
    if (controller.signal.aborted) {
      return
    }
    id.value = row.linkId
    revision.value = result.revision
    const data = result.data
    currentApplicant.value = data.applicant
    initialStatus.value = data.status
    Object.assign(form, {
      linkName: data.linkName,
      url: data.url,
      avatar: data.avatar || '',
      description: data.description || '',
      sortOrder: data.sortOrder,
      status: data.status,
      applicantId: data.applicant?.userId || '',
    })
    dialog.value = true
    loadUsers()
  } catch (failure) {
    if (failure?.name === 'AbortError' || controller.signal.aborted) {
      return
    }
    ElMessage.error(failure.message)
  }
}

async function save() {
  if (saving.value || uploading.value) {
    return
  }

  saving.value = true
  try {
    const body = {
      linkName: form.linkName,
      url: form.url,
      avatar: nullable(form.avatar),
      description: nullable(form.description),
      sortOrder: form.sortOrder,
      applicantId: form.applicantId || null,
    }
    if (!id.value) {
      body.status = form.status
    }
    const saved = await request(id.value ? `/admin/links/${id.value}` : '/admin/links', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    if (saved?.revision) {
      revision.value = saved.revision
    }
    if (id.value && form.status !== initialStatus.value) {
      await syncRowStatus('/admin/links', 'linkId', id.value, saved?.revision, 'batch-status', {
        status: form.status,
      })
      initialStatus.value = form.status
    }
    dialog.value = false
    ElMessage.success('友链已保存')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
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
    const payload = await prepareBatch('/admin/links', 'linkId', [row])
    await request('/admin/links/batch-delete', { method: 'POST', body: payload })
    ElMessage.success('友链已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

onMounted(load)

/** 切换顶部状态分栏并立即查询第一页。 */
function switchStatus(status) {
  filters.status = status
  load(true)
}

const sourceLabels = {
  APPLICATION: '博客申请',
  ADMIN: '后台创建',
  UNKNOWN: '未知来源',
}

const sourceTagTypes = {
  APPLICATION: 'primary',
  ADMIN: 'success',
  UNKNOWN: 'info',
}

/** 格式化友链来源名称。 */
function sourceLabel(source) {
  return sourceLabels[source] || '未知来源'
}

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
  if (can('blog:link:delete')) {
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
    <div class="page-tabs">
      <button
        v-for="tab in [
          { value: '', label: '全部友链' },
          { value: 1, label: '展示中' },
          { value: 0, label: '待审核' },
          { value: 2, label: '已下架' },
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
          placeholder="搜索站点名称或网址"
          maxlength="100"
          clearable
        />
        <el-select v-model="filters.source" aria-label="友链来源" placeholder="全部来源" clearable>
          <el-option label="博客申请" value="APPLICATION" />
          <el-option label="后台创建" value="ADMIN" />
          <el-option label="未知来源" value="UNKNOWN" />
        </el-select>
        <el-input
          v-model.trim="filters.applicantId"
          aria-label="申请人 ID"
          placeholder="申请人 ID"
          pattern="[1-9][0-9]{0,18}"
          clearable
        />
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
        <el-table-column prop="url" label="站点地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="来源" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="sourceTagTypes[row.source] || 'info'" effect="plain">
              {{ sourceLabel(row.source) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请人" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button
              v-if="row.applicant && can('sys:user:query')"
              type="primary"
              link
              @click="detailUserId = row.applicant.userId"
            >{{ row.applicant.nickname }}（{{ row.applicant.userId }}）</el-button>
            <span v-else-if="row.applicant">{{ row.applicant.nickname }}（{{ row.applicant.userId }}）</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="介绍" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="link" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="340" fixed="right">
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
        <el-descriptions-item label="友链来源">
          <el-tag :type="sourceTagTypes[detail.source] || 'info'" effect="plain">
            {{ sourceLabel(detail.source) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">
          <template v-if="detail.applicant">
            <span>{{ detail.applicant.nickname }}（ID：{{ detail.applicant.userId }}）</span>
            <el-button
              v-if="can('sys:user:query')"
              type="primary"
              link
              @click="detailUserId = detail.applicant.userId"
            >查看账号</el-button>
          </template>
          <span v-else>无</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
      </template>
    </ResourceDetail>
    <UserDetail
      v-if="detailUserId"
      :key="detailUserId"
      :user-id="detailUserId"
      @close="detailUserId = ''"
    />
    <el-dialog
      v-model="dialog"
      :title="id ? '编辑友链' : '新建友链'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving && !uploading"
      :close-on-press-escape="!saving && !uploading"
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
          绑定为本人申请（选填）
          <el-select
            v-if="can('sys:user:query') && (users || loadingUsers)"
            v-model="form.applicantId"
            :loading="loadingUsers"
            :disabled="saving"
            filterable
            clearable
            placeholder="请选择本站账号"
          >
            <el-option
              v-if="currentApplicant && !users?.some(user => user.userId === currentApplicant.userId)"
              :label="`${currentApplicant.nickname}（${currentApplicant.userId}）`"
              :value="currentApplicant.userId"
            />
            <el-option
              v-for="user in users"
              :key="user.userId"
              :label="`${user.nickname} · ${user.username}（${user.userId}）`"
              :value="user.userId"
            />
          </el-select>
          <el-input
            v-else
            v-model.trim="form.applicantId"
            :disabled="saving"
            pattern="[1-9][0-9]{0,18}"
            placeholder="填写用户 ID"
            clearable
          />
          <span class="muted-text">绑定后，该用户可在博客查看和撤回这条友链。</span>
        </label>
        <DisplayImageField
          usage="LINK_AVATAR"
          v-model="form.avatar"
          v-model:uploading="uploading"
          label="头像地址"
          :disabled="saving"
        />
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
        <label>
          审核状态
          <el-radio-group v-model="form.status" :disabled="Boolean(id) && !can('blog:link:update')">
            <el-radio :value="0">待审核</el-radio>
            <el-radio :value="1">展示中</el-radio>
            <el-radio :value="2">驳回 / 下架</el-radio>
          </el-radio-group>
        </label>
        <div class="dialog-actions">
          <el-button :disabled="saving || uploading" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="uploading">保存友链</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
