<script setup>
import DisplayImageField from '../components/DisplayImageField.vue'
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
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import { request } from '../api.js'
import { prepareBatch } from '../batch.js'
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
const uploading = ref(false)
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
  if (saving.value || uploading.value) {
    return
  }
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
    const payload = await prepareBatch('/admin/home/social-links', 'socialId', [row])
    await request('/admin/home/social-links/batch-delete', { method: 'POST', body: payload })
    ElMessage.success('社交链接已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

onMounted(load)

/** 切换顶部展示状态分栏并立即查询第一页。 */
function switchStatus(status) {
  filters.status = status
  load(true)
}

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
    <div class="page-tabs">
      <button
        v-for="tab in [
          { value: '', label: '全部链接' },
          { value: 1, label: '展示中' },
          { value: 0, label: '已隐藏' },
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
          placeholder="搜索平台名称"
          maxlength="100"
          clearable
        />
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
        <el-table-column label="图标" width="110" align="center">
          <template #default="{ row }">
            <ImagePreview
              v-if="/^(?:https?:\/\/|\/api\/v1\/(?:admin|blog\/music)\/)/i.test(row.icon?.trim() || '')"
              :src="row.icon"
              :alt="row.name"
              compact
            />
            <span v-else class="muted-text">{{ row.icon?.trim() || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="平台名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="url" label="链接地址" min-width="260" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="display" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="240" fixed="right">
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
      :show-close="!saving && !uploading"
      :close-on-press-escape="!saving && !uploading"
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
        <DisplayImageField
          usage="ICON"
          v-model="form.icon"
          v-model:uploading="uploading"
          label="图标键或图片地址"
          type="text"
          :disabled="saving"
        />
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
          <el-button :disabled="saving || uploading" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="uploading">保存社交链接</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
