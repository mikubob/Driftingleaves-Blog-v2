<script setup>
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
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'

const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/categories',
  { keyword: '', status: '' },
)
const detailId = ref('')
const dialog = ref(false)
const saving = ref(false)
const changingStatus = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const form = reactive({ categoryName: '', slug: '', description: '', sortOrder: 0, status: 1 })

async function edit(row) {
  formError.value = ''
  try {
    if (row) {
      const result = await request(`/admin/categories/${row.categoryId}`)
      id.value = row.categoryId
      revision.value = result.revision
      Object.assign(form, {
        categoryName: result.data.categoryName,
        slug: result.data.slug,
        description: result.data.description || '',
        sortOrder: result.data.sortOrder,
      })
    } else {
      id.value = ''
      Object.assign(form, { categoryName: '', slug: '', description: '', sortOrder: 0, status: 1 })
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
      categoryName: form.categoryName,
      slug: form.slug,
      description: nullable(form.description),
      sortOrder: form.sortOrder,
    }
    if (!id.value) {
      body.status = form.status
    }
    await request(id.value ? `/admin/categories/${id.value}` : '/admin/categories', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    dialog.value = false
    ElMessage.success('分类已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

/** 单个分类也使用批量状态接口，不随资料编辑提交。 */
async function changeStatus(row) {
  const status = row.status === 1 ? 0 : 1
  const title = status === 1 ? '展示分类' : '隐藏分类'
  try {
    await ElMessageBox.confirm(`确认${title}「${row.categoryName}」？`, title, {
      confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  changingStatus.value = true
  try {
    const payload = await prepareBatch('/admin/categories', 'categoryId', [row])
    await request('/admin/categories/batch-status', {
      method: 'PUT',
      body: { ...payload, status },
    })
    ElMessage.success(`${title}成功`)
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    changingStatus.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除分类「${row.categoryName}」？已有文章会保留并移除分类关联。`,
      '删除分类',
      { confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const detail = await request(`/admin/categories/${row.categoryId}`)
    await request('/admin/categories/batch-delete', { method: 'POST', body: { items: [{ id: row.categoryId, revision: detail.revision }] } })
    ElMessage.success('分类已删除')
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
  if (can('blog:category:update')) {
    actions.push(
      { label: '批量展示', type: 'success', path: 'batch-status', method: 'PUT', body: { status: 1 } },
      { label: '批量隐藏', type: 'warning', path: 'batch-status', method: 'PUT', body: { status: 0 } },
    )
  }
  if (can('blog:category:delete')) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
      warning: '文章保留，所属分类将置空。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>分类管理</h1>
      </div>
      <el-button v-if="can('blog:category:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建分类
      </el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="分类关键词"
          placeholder="搜索分类名称"
          clearable
          maxlength="100"
        />
        <el-select v-model="filters.status" aria-label="展示状态" placeholder="全部状态" clearable>
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
        <h2>分类列表</h2>
        <el-button text aria-label="刷新分类" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/categories" id-key="categoryId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || changingStatus || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || changingStatus || batch?.busy"
        :data="rows"
        row-key="categoryId"
        border
        stripe
        empty-text="暂无分类"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="categoryName" label="分类名称" min-width="160" />
        <el-table-column prop="slug" label="URL 标识" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="can('blog:category:query')"
              type="primary"
              link
              :disabled="changingStatus || batch?.busy"
              @click="detailId = row.categoryId"
            >详情</el-button>
            <el-button type="primary" v-if="can('blog:category:update')" link @click="edit(row)">编辑</el-button>
            <el-button
              v-if="can('blog:category:update')"
              :type="row.status === 1 ? 'warning' : 'success'"
              :disabled="changingStatus || batch?.busy"
              link
              @click="changeStatus(row)"
            >
              {{ row.status === 1 ? '隐藏' : '展示' }}
            </el-button>
            <el-button type="danger" v-if="can('blog:category:delete')" link @click="remove(row)">删除</el-button>
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
      :path="`/admin/categories/${detailId}`"
      title="分类详情"
      @close="detailId = ''"
    >
      <template #default="{ detail }">
        <el-descriptions-item label="分类 ID">{{ detail.categoryId }}</el-descriptions-item>
        <el-descriptions-item label="分类名称">{{ detail.categoryName }}</el-descriptions-item>
        <el-descriptions-item label="URL 标识">{{ detail.slug }}</el-descriptions-item>
        <el-descriptions-item label="描述">
          <p class="detail-text">{{ detail.description || '未填写' }}</p>
        </el-descriptions-item>
        <el-descriptions-item label="排序">{{ detail.sortOrder }}</el-descriptions-item>
        <el-descriptions-item label="展示状态"><StatusTag :value="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
      </template>
    </ResourceDetail>
    <el-dialog
      v-model="dialog"
      :title="id ? '编辑分类' : '新建分类'"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          分类名称
          <el-input v-model="form.categoryName" required maxlength="64" />
        </label>
        <label>
          URL 标识
          <el-input
            v-model="form.slug"
            required
            maxlength="100"
            pattern="[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*"
            placeholder="例如 technology"
          />
        </label>
        <label>
          描述
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" />
        </label>
        <div class="form-row">
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
            初始展示状态
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          </label>
        </div>
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存分类</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
