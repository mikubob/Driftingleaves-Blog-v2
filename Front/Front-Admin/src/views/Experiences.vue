<script setup>
import DisplayImageField from '../components/DisplayImageField.vue'
import ImagePreview from '../components/ImagePreview.vue'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import ResourceDetail from '../components/ResourceDetail.vue'
import { onMounted, reactive, ref, computed } from 'vue'
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
  ElSwitch,
  ElTable,
  ElTableColumn,
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
  '/admin/cv/experiences',
  { keyword: '', status: '', experienceType: '' },
)
const experienceTypeMeta = {
  0: { label: '教育经历', tone: 'is-purple', titleLabel: '学校名称', subtitleLabel: '专业 / 学历' },
  1: { label: '工作 / 实习', tone: 'is-success', titleLabel: '公司名称', subtitleLabel: '职位 / 部门' },
  2: { label: '项目经历', tone: 'is-warning', titleLabel: '项目名称', subtitleLabel: '担任角色 / 技术栈' },
}
const detailId = ref('')
const dialog = ref(false)
const saving = ref(false)
const uploading = ref(false)
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
const currentTypeMeta = computed(
  () => experienceTypeMeta[form.experienceType] || experienceTypeMeta[0],
)

/** 切换顶部经历类型分栏并重新加载第一页。 */
function switchType(type) {
  filters.experienceType = type
  load(true)
}

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
  if (saving.value || uploading.value) {
    return
  }
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
    const body = {
      experienceType: form.experienceType,
      title: form.title,
      subtitle: nullable(form.subtitle),
      logoUrl: nullable(form.logoUrl),
      content: form.content,
      startDate: form.startDate,
      endDate: nullable(form.endDate),
      sortOrder: form.sortOrder,
    }
    if (!id.value) {
      body.status = form.status
    }
    await request(id.value ? `/admin/cv/experiences/${id.value}` : '/admin/cv/experiences', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
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
    const payload = await prepareBatch('/admin/cv/experiences', 'experienceId', [row])
    await request('/admin/cv/experiences/batch-delete', { method: 'POST', body: payload })
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
  if (can('cv:experience:update', true)) {
    actions.push(
      { label: '批量展示', type: 'success', path: 'batch-status', method: 'PUT', body: { status: 1 } },
      { label: '批量隐藏', type: 'warning', path: 'batch-status', method: 'PUT', body: { status: 0 } },
    )
  }
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
        <h1>简历经历管理</h1>
      </div>
      <el-button v-if="can('cv:experience:create', true)" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建简历经历
      </el-button>
    </div>
    <div class="page-tabs">
      <button
        :class="{ active: filters.experienceType === '' || filters.experienceType == null }"
        @click="switchType('')"
      >
        全部经历
      </button>
      <button
        v-for="(meta, typeKey) in experienceTypeMeta"
        :key="typeKey"
        :class="{
          active:
            filters.experienceType !== '' &&
            filters.experienceType != null &&
            Number(filters.experienceType) === Number(typeKey),
        }"
        @click="switchType(Number(typeKey))"
      >
        {{ meta.label }}
      </button>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="搜索关键词"
          placeholder="搜索学校、公司、项目或专业"
          maxlength="100"
          clearable
        />
        <el-select v-model="filters.status" aria-label="状态" placeholder="全部状态" clearable>
          <el-option label="展示中" :value="1" />
          <el-option label="已隐藏" :value="0" />
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
        border
        stripe
        empty-text="暂无简历经历"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <span :class="['status-tag', experienceTypeMeta[row.experienceType]?.tone || 'is-muted']">
              <i aria-hidden="true"></i>
              {{ experienceTypeMeta[row.experienceType]?.label || '未知类型' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="title"
          label="学校 / 公司 / 项目"
          min-width="190"
          show-overflow-tooltip
        />
        <el-table-column label="专业 / 职位 / 角色" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.subtitle || '—' }}</template>
        </el-table-column>
        <el-table-column label="起止时间" min-width="200" align="center">
          <template #default="{ row }">{{ row.startDate || '—' }} ~ {{ row.endDate || '至今' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="display" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="can('cv:experience:query', true)"
              type="primary"
              link
              :disabled="batch?.busy"
              @click="detailId = row.experienceId"
            >详情</el-button>
            <RowStatusActions :row="row" :actions="batchActions" :batch="batch" :disabled="loading" />
            <el-button type="primary" v-if="can('cv:experience:update', true)" link @click="edit(row)">
              编辑
            </el-button>
            <el-button type="danger" v-if="can('cv:experience:delete', true)" link @click="remove(row)">
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
    <ResourceDetail
      v-if="detailId"
      :key="detailId"
      :path="`/admin/cv/experiences/${detailId}`"
      title="简历经历详情"
      @close="detailId = ''"
    >
      <template #default="{ detail }">
        <el-descriptions-item label="经历 ID">{{ detail.experienceId }}</el-descriptions-item>
        <el-descriptions-item label="经历类型">
          <span :class="['status-tag', experienceTypeMeta[detail.experienceType]?.tone || 'is-muted']">
            <i aria-hidden="true"></i>
            {{ experienceTypeMeta[detail.experienceType]?.label || '未知类型' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item :label="experienceTypeMeta[detail.experienceType]?.titleLabel || '名称'">
          {{ detail.title }}
        </el-descriptions-item>
        <el-descriptions-item :label="experienceTypeMeta[detail.experienceType]?.subtitleLabel || '副标题'">
          {{ detail.subtitle || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="起止时间">
          {{ detail.startDate || '—' }} ~ {{ detail.endDate || '至今' }}
        </el-descriptions-item>
        <el-descriptions-item label="Logo">
          <ImagePreview v-if="detail.logoUrl" :src="detail.logoUrl" alt="经历 Logo" />
          <span v-else class="muted-text">—</span>
        </el-descriptions-item>
        <el-descriptions-item label="经历内容">
          <MarkdownPreview v-if="detail.content" :markdown="detail.content" />
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="排序">{{ detail.sortOrder }}</el-descriptions-item>
        <el-descriptions-item label="展示状态"><StatusTag :value="detail.status" kind="display" /></el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
      </template>
    </ResourceDetail>
    <el-dialog
      v-model="dialog"
      :title="id ? '编辑简历经历' : '新建简历经历'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving && !uploading"
      :close-on-press-escape="!saving && !uploading"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          经历类型
          <el-radio-group v-model="form.experienceType">
            <el-radio :value="0">教育经历</el-radio>
            <el-radio :value="1">工作 / 实习</el-radio>
            <el-radio :value="2">项目经历</el-radio>
          </el-radio-group>
        </label>
        <label>
          {{ currentTypeMeta.titleLabel }}
          <el-input v-model="form.title" type="text" maxlength="200" required />
        </label>
        <label>
          {{ currentTypeMeta.subtitleLabel }}
          <el-input v-model="form.subtitle" type="text" maxlength="200" />
        </label>
        <DisplayImageField
          usage="EXPERIENCE_LOGO"
          v-model="form.logoUrl"
          v-model:uploading="uploading"
          label="Logo 地址"
          :disabled="saving"
        />
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
        <label v-if="!id">
          展示状态
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving || uploading" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="uploading">保存简历经历</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
