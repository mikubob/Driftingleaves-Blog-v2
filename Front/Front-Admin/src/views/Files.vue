<script setup>
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTooltip,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tooltip/style/css'
import { getFile, request } from '../api.js'
import { fileAccept, uploadFile } from '../fileUpload.js'
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
const detailVisible = ref(false)
const detailId = ref('')
const detail = ref(null)
const detailError = ref('')
const previewUrl = ref('')
const previewText = ref('')
const previewLoading = ref(false)
const previewError = ref('')
let controller

const usageLabels = {
  ARTICLE_COVER: '文章封面',
  ARTICLE_IMAGE: '文章配图',
  MUSIC_COVER: '音乐封面',
  AVATAR: '头像',
  LINK_AVATAR: '友链头像',
  EXPERIENCE_LOGO: '经历标志',
  ICON: '图标',
  IMAGE: '其他图片',
  SONG: '歌曲',
  LYRIC: '歌词',
  ATTACHMENT: '附件',
}

/** 显示上传时选择的用途。 */
function fileUsage(file) {
  const usage = file.usage ?? file.usages?.[0]
  if (!usage) {
    return file.publicImage ? '公开图片' : '附件'
  }
  return usageLabels[usage] ?? usage
}

/** 取消详情和预览请求，释放预览使用的临时地址。 */
function releasePreview() {
  controller?.abort()
  previewText.value = ''
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

/** 查询文件详情，资料加载成功后再读取预览。 */
async function openDetail(fileId) {
  releasePreview()
  controller = new AbortController()
  const { signal } = controller
  detailId.value = fileId
  detail.value = null
  detailError.value = ''
  detailVisible.value = true

  try {
    const { data } = await request(`/admin/files/${fileId}`, { signal })
    if (signal.aborted) {
      return
    }
    detail.value = data
    loadPreview()
  } catch (failure) {
    if (!signal.aborted) {
      detailError.value = failure.message
    }
  }
}

/** 读取预览内容，失败时保留已加载的文件资料。 */
async function loadPreview() {
  const { signal } = controller
  const { mimeType, previewUrl: contentPath } = detail.value
  previewError.value = ''
  if (mimeType.startsWith('audio/')) {
    previewLoading.value = false
    return
  }
  previewLoading.value = true
  try {
    const file = await getFile(contentPath, signal)
    if (mimeType.startsWith('image/')) {
      if (!signal.aborted) {
        previewUrl.value = URL.createObjectURL(file)
      }
    } else {
      const content = await file.text()
      if (!signal.aborted) {
        previewText.value = content
      }
    }
  } catch (failure) {
    if (!signal.aborted) {
      previewError.value = failure.message
    }
  } finally {
    if (!signal.aborted) {
      previewLoading.value = false
    }
  }
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) {
    return
  }
  busy.value = true
  try {
    await uploadFile(file)
    ElMessage.success('文件已上传')
    load(true)
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  const consequence = row.publicImage
    ? '删除后，引用此公开图片的页面将无法显示图片。'
    : '仍被文章（含回收站）或音乐（含隐藏曲目）引用的文件无法删除。'
  try {
    await ElMessageBox.confirm(
      `删除文件「${row.originalName}」？${consequence}`,
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
onBeforeUnmount(releasePreview)

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
      warning: '文件将逐项清理。删除公开图片会使其地址失效；受文章或音乐保护的附件无法删除。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>文件管理</h1>
      </div>
      <div>
        <input ref="input" type="file" :accept="Object.values(fileAccept).join(',')" hidden @change="upload" />
        <el-tooltip
          v-if="can('blog:file:upload')"
          :trigger="['hover', 'focus']"
          content="图片 ≤10 MiB；MP3/WAV/OGG/FLAC ≤60 MiB；UTF-8 LRC/TXT/JSON ≤1 MiB"
          placement="bottom"
        >
          <el-button type="primary" :loading="busy" @click="input.click()">
            <AdminIcon name="plus" />
            上传文件
          </el-button>
        </el-tooltip>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="原始文件名或存储文件名"
          placeholder="原始文件名 / 存储文件名"
          clearable
          maxlength="255"
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
          aria-label="文件格式"
          placeholder="全部格式"
          clearable
        >
          <el-option label="PNG" value="image/png" />
          <el-option label="JPEG" value="image/jpeg" />
          <el-option label="MP3" value="audio/mpeg" />
          <el-option label="WAV" value="audio/wav" />
          <el-option label="OGG" value="audio/ogg" />
          <el-option label="FLAC" value="audio/flac" />
          <el-option label="LRC / TXT" value="text/plain" />
          <el-option label="JSON 歌词" value="application/json" />
        </el-select>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>文件列表</h2>
        <el-button text aria-label="刷新列表" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
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
        border
        stripe
        empty-text="暂无文件"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="originalName" label="原始文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="storedName" label="存储文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="fileId" label="文件 ID" min-width="175" />
        <el-table-column prop="mimeType" label="类型" width="120" />
        <el-table-column label="用途" width="100">
          <template #default="{ row }">{{ fileUsage(row) }}</template>
        </el-table-column>
        <el-table-column label="大小" width="130">
          <template #default="{ row }">{{ formatCount(row.sizeBytes) }} B</template>
        </el-table-column>
        <el-table-column prop="storageType" label="存储" width="90" />
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="140">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.fileId)">详情</el-button>
            <el-button type="danger" v-if="can('blog:file:delete')" link @click="remove(row)">删除</el-button>
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
    <el-dialog v-model="detailVisible" title="文件详情" width="760px" @close="releasePreview">
      <RequestError v-if="detailError" :message="detailError" @retry="openDetail(detailId)" />
      <p v-else-if="!detail" role="status">正在加载文件详情…</p>
      <template v-else>
        <el-descriptions class="file-detail" :column="2" border>
          <el-descriptions-item label="原始文件名" :span="2">{{ detail.originalName }}</el-descriptions-item>
          <el-descriptions-item label="存储文件名" :span="2">{{ detail.storedName }}</el-descriptions-item>
          <el-descriptions-item label="文件 ID">{{ detail.fileId }}</el-descriptions-item>
          <el-descriptions-item label="上传者 ID">{{ detail.uploaderId }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.mimeType }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ formatCount(detail.sizeBytes) }} B</el-descriptions-item>
          <el-descriptions-item label="用途">{{ fileUsage(detail) }}</el-descriptions-item>
          <el-descriptions-item label="存储">{{ detail.storageType }}</el-descriptions-item>
          <el-descriptions-item label="上传时间" :span="2">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        </el-descriptions>
        <div class="card-heading preview-heading"><h2>文件预览</h2></div>
        <RequestError v-if="previewError" :message="previewError" @retry="loadPreview" />
        <p v-else-if="previewLoading" role="status">正在加载预览…</p>
        <img
          v-else-if="previewUrl && detail.mimeType.startsWith('image/')"
          :src="previewUrl" :alt="detail.originalName" class="file-preview"
        />
        <audio
          v-else-if="detailVisible && detail.mimeType.startsWith('audio/')"
          :src="`/api/v1/admin/files/${detail.fileId}/content`"
          :aria-label="detail.originalName"
          controls preload="metadata"
          @error="previewError = '播放失败，请检查音频文件或访问权限。'"
        />
        <pre v-else class="lyric-preview">{{ previewText }}</pre>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.file-detail {
  overflow-wrap: anywhere;
}

.preview-heading {
  margin-top: 20px;
}

.file-preview {
  display: block;
  max-height: 45vh;
  object-fit: contain;
}

audio {
  max-width: 100%;
}

.lyric-preview {
  max-height: 480px;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
