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
  '/admin/cv/experiences',
  { keyword: '', status: '', experienceType: '' },
)
const dialog = ref(false)
const saving = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const form = reactive({
  experienceType: 0,
  title: '',
  subtitle: '',
  logoUrl: '',
  content: '',
  startDate: '',
  endDate: '',
  sortOrder: 0,
  status: 1,
})

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.experienceId || ''
    if (row) {
      const result = await request(`/admin/cv/experiences/${row.experienceId}`)
      revision.value = result.revision
      const data = result.data
      Object.assign(form, {
        experienceType: data.experienceType,
        title: data.title,
        subtitle: data.subtitle || '',
        logoUrl: data.logoUrl || '',
        content: data.content,
        startDate: data.startDate,
        endDate: data.endDate || '',
        sortOrder: data.sortOrder,
        status: data.status,
      })
    } else {
      Object.assign(form, {
        experienceType: 0,
        title: '',
        subtitle: '',
        logoUrl: '',
        content: '',
        startDate: '',
        endDate: '',
        sortOrder: 0,
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
  if (new TextEncoder().encode(form.content).length > 65535 || !form.content.trim()) {
    formError.value = '经历内容不能为空，UTF-8 不能超过 65,535 字节。'
    return
  }
  if (form.endDate && form.endDate < form.startDate) {
    formError.value = '结束日期不能早于开始日期。'
    return
  }
  saving.value = true
  try {
    await request(id.value ? `/admin/cv/experiences/${id.value}` : '/admin/cv/experiences', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body: {
        experienceType: form.experienceType,
        title: form.title,
        subtitle: nullable(form.subtitle),
        logoUrl: nullable(form.logoUrl),
        content: form.content,
        startDate: form.startDate,
        endDate: nullable(form.endDate),
        sortOrder: form.sortOrder,
        status: form.status,
      },
    })
    dialog.value = false
    ElMessage.success('简历经历已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除简历经历', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const detail = await request(`/admin/cv/experiences/${row.experienceId}`)
    await request('/admin/cv/experiences/batch-delete', { method: 'POST', body: { items: [{ id: row.experienceId, revision: detail.revision }] } })
    ElMessage.success('简历经历已删除')
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
  if (can('cv:experience:delete', true)) {
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
        <p class="eyebrow">CV / EXPERIENCES</p>
        <h1>简历经历管理</h1>
        <p class="muted-text">SUB简历经历</p>
      </div>
      <el-button v-if="can('cv:experience:create', true)" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建简历经历
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
        <el-select
          v-model="filters.experienceType"
          aria-label="经历类型"
          placeholder="全部经历类型"
          clearable
        >
          <el-option label="教育经历" :value="0" />
          <el-option label="工作 / 实习" :value="1" />
          <el-option label="项目经历" :value="2" />
        </el-select>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>简历经历列表</h2>
        <el-button text aria-label="刷新列表" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/cv/experiences" id-key="experienceId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="experienceId"
        empty-text="暂无简历经历"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column
          prop="title"
          label="学校 / 公司 / 项目"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          prop="subtitle"
          label="专业 / 职位"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column prop="startDate" label="开始日期" min-width="120" show-overflow-tooltip />
        <el-table-column prop="endDate" label="结束日期" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="display" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('cv:experience:update', true)" link @click="edit(row)">
              编辑
            </el-button>
            <el-button v-if="can('cv:experience:delete', true)" link @click="remove(row)">
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
      :title="id ? '编辑简历经历' : '新建简历经历'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving"
      :close-on-press-escape="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          经历类型
          <el-select v-model="form.experienceType">
            <el-option label="教育经历" :value="0" />
            <el-option label="工作 / 实习" :value="1" />
            <el-option label="项目经历" :value="2" />
          </el-select>
        </label>
        <label>
          学校 / 公司 / 项目
          <el-input v-model="form.title" type="text" maxlength="200" required />
        </label>
        <label>
          专业 / 职位 / 角色
          <el-input v-model="form.subtitle" type="text" maxlength="200" />
        </label>
        <label>
          标志图片地址
          <el-input v-model="form.logoUrl" type="url" maxlength="2048" />
        </label>
        <label>
          Markdown 内容
          <el-input v-model="form.content" type="textarea" :rows="8" required />
        </label>
        <div class="form-row">
          <label>
            开始日期
            <el-input v-model="form.startDate" type="date" maxlength="10" required />
          </label>
          <label>
            结束日期（留空为至今）
            <el-input v-model="form.endDate" type="date" maxlength="10" />
          </label>
        </div>
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
          <el-button type="primary" native-type="submit" :loading="saving">保存简历经历</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
