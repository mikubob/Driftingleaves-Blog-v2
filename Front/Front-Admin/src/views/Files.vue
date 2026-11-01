<script setup>
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getImage, request } from '../api.js'
import { can } from '../session.js'
import { formatCount, formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
import AdminIcon from '../components/AdminIcon.vue'

const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/files',
  { keyword: '', uploaderId: '', mimeType: '' },
)
const input = ref(null)
const busy = ref(false)
const imageUrl = ref('')
const imageName = ref('')
const previewVisible = ref(false)
let controller

function releaseImage() {
  controller?.abort()
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }
}

async function preview(row) {
  releaseImage()
  controller = new AbortController()
  try {
    const result = await getImage(`/api/v1/admin/files/${row.fileId}/content`, controller.signal)
    imageUrl.value = result
    imageName.value = row.originalName
    previewVisible.value = true
  } catch (failure) {
    if (failure.name !== 'AbortError') {
      ElMessage.error(failure.message)
    }
  }
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) {
    return
  }
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size === 0 || file.size > 10485760) {
    ElMessage.error('请选择不超过 10 MiB 的 PNG/JPEG 图片。')
    return
  }
  busy.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    await request('/admin/files', { method: 'POST', body })
    ElMessage.success('图片已上传')
    load(true)
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除文件「${row.originalName}」？仍被文章（含回收站）引用的文件无法删除。`,
      '删除文件',
      { confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const { data } = await request('/admin/files/batch-delete', { method: 'POST', body: { ids: [row.fileId] } })
    if (data.results[0].code !== 'OK') {
      throw new Error(data.results[0].msg)
    }
    ElMessage.success('文件已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}
onMounted(load)
onBeforeUnmount(releaseImage)

const batch = ref(null)
const batchTable = ref(null)
const batchActions = computed(() => {
  const actions = []
  if (can('blog:file:delete')) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      revision: false,
      fileResults: true,
      danger: true,
      warning: '文件将逐项清理，仍被文章引用的文件不会删除；每个文件的结果会单独显示。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">CONTENT / FILES</p>
        <h1>文件管理</h1>
        <p class="muted-text">安全管理图片附件，文件访问由后端核验授权。</p>
      </div>
      <div>
        <input ref="input" type="file" accept="image/png,image/jpeg" hidden @change="upload" />
        <el-button
          v-if="can('blog:file:upload')"
          type="primary"
          :loading="busy"
          @click="input.click()"
        >
          <AdminIcon name="plus" />
          上传图片
        </el-button>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="文件名"
          placeholder="搜索文件名"
          clearable
          maxlength="100"
        />
        <el-input
          v-model="filters.uploaderId"
          aria-label="上传者 ID"
          placeholder="上传者 ID"
          clearable
          pattern="[1-9][0-9]{0,18}"
        />
        <el-select
          v-model="filters.mimeType"
          aria-label="图片格式"
          placeholder="全部格式"
          clearable
        >
          <el-option label="PNG" value="image/png" />
          <el-option label="JPEG" value="image/jpeg" />
        </el-select>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>文件列表</h2>
        <span class="muted-text">PNG / JPEG · 单张不超过 10 MiB</span>
      </div>
            <BatchActions
        ref="batch" resource="/admin/files" id-key="fileId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || busy || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || busy || batch?.busy"
        :data="rows"
        row-key="fileId"
        empty-text="暂无文件"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="originalName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fileId" label="文件 ID" min-width="175" />
        <el-table-column prop="mimeType" label="类型" width="120" />
        <el-table-column label="大小" width="130">
          <template #default="{ row }">{{ formatCount(row.sizeBytes) }} B</template>
        </el-table-column>
        <el-table-column prop="storageType" label="存储" width="90" />
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link @click="preview(row)">预览</el-button>
            <el-button v-if="can('blog:file:delete')" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPager
        :page="page"
        :page-size="pageSize"
        :total="total"
        :total-pages="totalPages"
        :loading="loading || busy"
        @change="changePage"
        @size-change="changePageSize"
      />
    </el-card>
    <el-dialog v-model="previewVisible" :title="imageName" width="760px" @closed="releaseImage">
      <img v-if="imageUrl" :src="imageUrl" :alt="imageName" class="file-preview" />
    </el-dialog>
  </section>
</template>
