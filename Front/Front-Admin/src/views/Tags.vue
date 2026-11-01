<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
import AdminIcon from '../components/AdminIcon.vue'

const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/tags',
  { keyword: '' },
)
const dialog = ref(false)
const saving = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const form = reactive({ tagName: '', slug: '' })

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.tagId || ''
    if (row) {
      const result = await request(`/admin/tags/${row.tagId}`)
      revision.value = result.revision
      Object.assign(form, { tagName: result.data.tagName, slug: result.data.slug })
    } else {
      Object.assign(form, { tagName: '', slug: '' })
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
    await request(id.value ? `/admin/tags/${id.value}` : '/admin/tags', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body: { tagName: form.tagName, slug: form.slug },
    })
    dialog.value = false
    ElMessage.success('标签已保存')
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
      `删除标签「${row.tagName}」及文章关联？文章内容会保留。`,
      '删除标签',
      { confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const detail = await request(`/admin/tags/${row.tagId}`)
    await request('/admin/tags/batch-delete', { method: 'POST', body: { items: [{ id: row.tagId, revision: detail.revision }] } })
    ElMessage.success('标签已删除')
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
  if (can('blog:tag:delete')) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
      warning: '同时移除文章中的标签关联，文章保留。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">CONTENT / TAGS</p>
        <h1>标签管理</h1>
        <p class="muted-text">用标签串联想法，让文章更容易被发现。</p>
      </div>
      <el-button v-if="can('blog:tag:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建标签
      </el-button>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="标签关键词"
          placeholder="搜索标签名称"
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
        <h2>标签列表</h2>
        <span class="muted-text">文章的另一种索引</span>
      </div>
            <BatchActions
        ref="batch" resource="/admin/tags" id-key="tagId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="tagId"
        border
        stripe
        empty-text="暂无标签"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column label="标签名称" min-width="200">
          <template #default="{ row }">
            <span class="plain-tag"># {{ row.tagName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="slug" label="URL 标识" min-width="180" />
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" v-if="can('blog:tag:update')" link @click="edit(row)">编辑</el-button>
            <el-button type="danger" v-if="can('blog:tag:delete')" link @click="remove(row)">删除</el-button>
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
      :title="id ? '编辑标签' : '新建标签'"
      width="500px"
      :close-on-click-modal="false"
      :show-close="!saving"
      :close-on-press-escape="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
        <label>
          标签名称
          <el-input v-model="form.tagName" required maxlength="64" />
        </label>
        <label>
          URL 标识
          <el-input
            v-model="form.slug"
            required
            maxlength="100"
            pattern="[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*"
          />
        </label>
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存标签</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
