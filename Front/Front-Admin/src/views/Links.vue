<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime, nullable } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'

const { filters, rows, page, total, totalPages, loading, error, load, reset, changePage } = useList(
  '/admin/links',
  { keyword: '', status: '' },
)
const dialog = ref(false)
const saving = ref(false)
const formError = ref('')
const id = ref('')
const revision = ref('')
const form = reactive({
  linkName: '',
  url: '',
  avatar: '',
  description: '',
  sortOrder: 0,
  status: 0,
})

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.linkId || ''
    if (row) {
      const result = await request(`/admin/links/${row.linkId}`)
      revision.value = result.revision
      const data = result.data
      Object.assign(form, {
        linkName: data.linkName,
        url: data.url,
        avatar: data.avatar || '',
        description: data.description || '',
        sortOrder: data.sortOrder,
        status: data.status,
      })
    } else {
      Object.assign(form, {
        linkName: '',
        url: '',
        avatar: '',
        description: '',
        sortOrder: 0,
        status: 0,
      })
    }
    dialog.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function save() {
  formError.value = ''

  saving.value = true
  try {
    await request(id.value ? `/admin/links/${id.value}` : '/admin/links', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body: {
        linkName: form.linkName,
        url: form.url,
        avatar: nullable(form.avatar),
        description: nullable(form.description),
        sortOrder: form.sortOrder,
        status: form.status,
      },
    })
    dialog.value = false
    ElMessage.success('友链已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
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
    const detail = await request(`/admin/links/${row.linkId}`)
    await request(`/admin/links/${row.linkId}`, { method: 'DELETE', revision: detail.revision })
    ElMessage.success('友链已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">LINKS</p>
        <h1>友链管理</h1>
        <p class="muted-text">SUB友链</p>
      </div>
      <el-button v-if="can('blog:link:create')" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建友链
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
          <el-option label="已下架" :value="2" />
        </el-select>
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
      <el-table
        v-if="!error"
        v-loading="loading"
        :data="rows"
        row-key="linkId"
        empty-text="暂无友链"
      >
        <el-table-column prop="linkName" label="站点名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="url" label="站点地址" min-width="240" show-overflow-tooltip />
        <el-table-column prop="description" label="介绍" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="link" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('blog:link:update')" link @click="edit(row)">编辑</el-button>
            <el-button v-if="can('blog:link:delete')" link @click="remove(row)">删除</el-button>
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
      :title="id ? '编辑友链' : '新建友链'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving"
      :close-on-press-escape="!saving"
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
          头像地址
          <el-input v-model="form.avatar" type="url" maxlength="2048" />
        </label>
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
          <el-select v-model="form.status">
            <el-option label="待审核" :value="0" />
            <el-option label="展示中" :value="1" />
            <el-option label="驳回 / 下架" :value="2" />
          </el-select>
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存友链</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
