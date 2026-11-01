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
  '/admin/categories',
  { keyword: '', status: '' },
)
const dialog = ref(false)
const saving = ref(false)
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
        status: result.data.status,
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
    await request(id.value ? `/admin/categories/${id.value}` : '/admin/categories', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body: {
        categoryName: form.categoryName,
        slug: form.slug,
        description: nullable(form.description),
        sortOrder: form.sortOrder,
        status: form.status,
      },
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
        <p class="eyebrow">CONTENT / CATEGORIES</p>
        <h1>分类管理</h1>
        <p class="muted-text">让内容井然有序，管理文章的分类与展示顺序。</p>
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
        :disabled="loading || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="categoryId"
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
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('blog:category:update')" link @click="edit(row)">编辑</el-button>
            <el-button v-if="can('blog:category:delete')" link @click="remove(row)">删除</el-button>
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
          <label>
            展示状态
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
