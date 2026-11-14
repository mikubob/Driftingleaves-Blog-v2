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
  '/admin/cv/skills',
  { keyword: '', status: '' },
)
const dialog = ref(false)
const saving = ref(false)
const uploading = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const form = reactive({ name: '', description: '', icon: '', sortOrder: 0, status: 1 })

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.skillId || ''
    if (row) {
      const result = await request(`/admin/cv/skills/${row.skillId}`)
      revision.value = result.revision
      const data = result.data
      Object.assign(form, {
        name: data.name,
        description: data.description || '',
        icon: data.icon || '',
        sortOrder: data.sortOrder,
        status: data.status,
      })
    } else {
      Object.assign(form, { name: '', description: '', icon: '', sortOrder: 0, status: 1 })
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
      description: nullable(form.description),
      icon: nullable(form.icon),
      sortOrder: form.sortOrder,
    }
    if (!id.value) {
      body.status = form.status
    }
    await request(id.value ? `/admin/cv/skills/${id.value}` : '/admin/cv/skills', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    dialog.value = false
    ElMessage.success('简历技能已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除简历技能', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const payload = await prepareBatch('/admin/cv/skills', 'skillId', [row])
    await request('/admin/cv/skills/batch-delete', { method: 'POST', body: payload })
    ElMessage.success('简历技能已删除')
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
  if (can('cv:skill:update', true)) {
    actions.push(
      { label: '批量展示', type: 'success', path: 'batch-status', method: 'PUT', body: { status: 1 } },
      { label: '批量隐藏', type: 'warning', path: 'batch-status', method: 'PUT', body: { status: 0 } },
    )
  }
  if (can('cv:skill:delete', true)) {
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
        <h1>简历技能管理</h1>
      </div>
      <el-button v-if="can('cv:skill:create', true)" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建简历技能
      </el-button>
    </div>
    <div class="page-tabs">
      <button
        v-for="tab in [
          { value: '', label: '全部技能' },
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
          placeholder="搜索技能名称"
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
        <h2>简历技能列表</h2>
        <el-button text aria-label="刷新列表" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/cv/skills" id-key="skillId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="skillId"
        border
        stripe
        empty-text="暂无简历技能"
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
        <el-table-column prop="name" label="技能名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="技能描述" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
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
            <el-button type="primary" v-if="can('cv:skill:update', true)" link @click="edit(row)">编辑</el-button>
            <el-button type="danger" v-if="can('cv:skill:delete', true)" link @click="remove(row)">
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
      :title="id ? '编辑简历技能' : '新建简历技能'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving && !uploading"
      :close-on-press-escape="!saving && !uploading"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          技能名称
          <el-input v-model="form.name" type="text" maxlength="64" required />
        </label>
        <label>
          技能描述
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" />
        </label>
        <DisplayImageField
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
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="uploading">保存简历技能</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
