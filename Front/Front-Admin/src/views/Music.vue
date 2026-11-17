<script setup>
import ImagePreview from '../components/ImagePreview.vue'
import FileUploadButton from '../components/FileUploadButton.vue'
import { onBeforeUnmount, onMounted, reactive, ref, computed, watch } from 'vue'
import {
  ElButton,
  ElCard,
  ElDialog,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElRadio,
  ElRadioGroup,
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
import 'element-plus/es/components/radio/style/css'
import 'element-plus/es/components/radio-group/style/css'
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import { request } from '../api.js'
import { prepareBatch, syncRowStatus } from '../batch.js'
import { uploadFile } from '../fileUpload.js'
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
  '/admin/music',
  { keyword: '', status: '' },
)
const dialog = ref(false)
const saving = ref(false)
const uploading = ref('')
const canUpload = computed(() => can('blog:file:upload') && can('blog:file:query'))
const id = ref('')
const revision = ref('')
const initialStatus = ref(1)
const form = reactive({
  title: '',
  artist: '',
  durationSeconds: null,
  coverUrl: '',
  audioUrl: '',
  lyricUrl: '',
  lyricType: '',
  sortOrder: 0,
  status: 1,
})

/** 上传音乐附件，由文件接口检查权限并处理失败清理；成功后填写表单，保存曲目后才建立公开引用。 */
async function upload(file, kind) {
  if (saving.value || uploading.value) {
    return
  }
  uploading.value = kind
  try {
    const usage = { image: 'MUSIC_COVER', audio: 'SONG', lyric: 'LYRIC' }[kind]
    const result = await uploadFile(file, kind, usage)
    const field = { image: 'coverUrl', audio: 'audioUrl', lyric: 'lyricUrl' }[kind]
    form[field] = `/api/v1/blog/music/files/${result.file.fileId}/content`
    if (kind === 'lyric') {
      form.lyricType = result.extension
    }
    ElMessage.success('文件已上传，请保存音乐；未使用文件可在文件管理中清理。')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    uploading.value = ''
  }
}

async function edit(row) {
  try {
    id.value = row?.musicId || ''
    if (row) {
      const result = await request(`/admin/music/${row.musicId}`)
      revision.value = result.revision
      const data = result.data
      initialStatus.value = data.status
      Object.assign(form, {
        title: data.title,
        artist: data.artist || '',
        durationSeconds: data.durationSeconds,
        coverUrl: data.coverUrl || '',
        audioUrl: data.audioUrl,
        lyricUrl: data.lyricUrl || '',
        lyricType: data.lyricType || '',
        sortOrder: data.sortOrder,
        status: data.status,
      })
    } else {
      initialStatus.value = 1
      Object.assign(form, {
        title: '',
        artist: '',
        durationSeconds: null,
        coverUrl: '',
        audioUrl: '',
        lyricUrl: '',
        lyricType: '',
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
  if (uploading.value || saving.value) {
    return
  }
  if (Boolean(form.lyricUrl.trim()) !== Boolean(form.lyricType)) {
    ElMessage.error('歌词地址和歌词格式必须同时填写或同时清空。')
    return
  }
  saving.value = true
  try {
    const body = {
      title: form.title,
      artist: nullable(form.artist),
      durationSeconds: form.durationSeconds ?? null,
      coverUrl: nullable(form.coverUrl),
      audioUrl: form.audioUrl,
      lyricUrl: nullable(form.lyricUrl),
      lyricType: nullable(form.lyricType),
      sortOrder: form.sortOrder,
    }
    if (!id.value) {
      body.status = form.status
    }
    const saved = await request(id.value ? `/admin/music/${id.value}` : '/admin/music', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    if (saved?.revision) {
      revision.value = saved.revision
    }
    if (id.value && form.status !== initialStatus.value) {
      await syncRowStatus('/admin/music', 'musicId', id.value, saved?.revision, 'batch-status', {
        status: form.status,
      })
      initialStatus.value = form.status
    }
    dialog.value = false
    ElMessage.success('音乐已保存')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？`, '删除音乐', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const payload = await prepareBatch('/admin/music', 'musicId', [row])
    await request('/admin/music/batch-delete', { method: 'POST', body: payload })
    ElMessage.success('音乐已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

onMounted(load)

const playingId = ref('')
let audioInstance = null

/** 停止当前试听并释放音频实例。 */
function stopAudio() {
  if (audioInstance) {
    audioInstance.onended = null
    audioInstance.onerror = null
    audioInstance.pause()
    audioInstance.removeAttribute('src')
    audioInstance.load()
    audioInstance = null
  }
  playingId.value = ''
}

/** 切换列表曲目的试听与暂停状态。 */
async function togglePlay(row) {
  if (playingId.value === row.musicId) {
    stopAudio()
    return
  }
  stopAudio()

  // 站内音频用后台会话 Cookie 读取，浏览器负责缓冲和分段请求。
  const fileId = row.audioUrl.match(/^\/api\/v1\/blog\/music\/files\/([1-9]\d*)\/content$/)?.[1]
  const source = fileId ? `/api/v1/admin/files/${fileId}/content` : row.audioUrl
  const audio = new Audio(source)
  audioInstance = audio
  playingId.value = row.musicId
  const fail = () => {
    if (audioInstance !== audio) {
      return
    }
    stopAudio()
    ElMessage.error('播放失败，请检查音频地址或访问权限。')
  }
  audio.onended = stopAudio
  audio.onerror = fail
  try {
    await audio.play()
  } catch {
    fail()
  }
}

/** 将秒数格式化为 mm:ss，空值或无效值显示占位符。 */
function formatDuration(seconds) {
  if (typeof seconds !== 'number' || !Number.isFinite(seconds) || seconds < 0) {
    return '—'
  }
  const totalSeconds = Math.floor(seconds)
  const mins = Math.floor(totalSeconds / 60)
  const secs = String(totalSeconds % 60).padStart(2, '0')
  return `${mins}:${secs}`
}

watch(rows, stopAudio)
onBeforeUnmount(stopAudio)

/** 切换顶部展示状态分栏并立即查询第一页。 */
function switchStatus(status) {
  filters.status = status
  load(true)
}

const batch = ref(null)
const batchTable = ref(null)
const batchActions = computed(() => {
  const actions = []
  if (can('blog:music:update', true)) {
    actions.push(
      { label: '批量展示', type: 'success', path: 'batch-status', method: 'PUT', body: { status: 1 } },
      { label: '批量隐藏', type: 'warning', path: 'batch-status', method: 'PUT', body: { status: 0 } },
    )
  }
  if (can('blog:music:delete', true)) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
      warning: '仅清理曲目元数据，不删除外部音频。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>音乐管理</h1>
      </div>
      <el-button v-if="can('blog:music:create', true)" type="primary" @click="edit()">
        <AdminIcon name="plus" />
        新建音乐
      </el-button>
    </div>
    <div class="page-tabs">
      <button
        v-for="tab in [
          { value: '', label: '全部音乐' },
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
          placeholder="搜索曲目名称或歌手"
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
        <h2>音乐列表</h2>
        <el-button text aria-label="刷新列表" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/music" id-key="musicId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || batch?.busy"
        :data="rows"
        row-key="musicId"
        border
        stripe
        empty-text="暂无音乐"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column label="封面" width="76" align="center">
          <template #default="{ row }">
            <ImagePreview v-if="row.coverUrl" :src="row.coverUrl" :alt="row.title" compact />
            <span v-else class="muted-text">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="曲目名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="歌手" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.artist || '—' }}</template>
        </el-table-column>
        <el-table-column label="时长" width="80" align="center">
          <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
        </el-table-column>
        <el-table-column label="歌词" width="85" align="center">
          <template #default="{ row }">{{ row.lyricUrl ? (row.lyricType || '有').toUpperCase() : '—' }}</template>
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
              type="primary"
              link
              :disabled="!row.audioUrl?.trim() || batch?.busy"
              @click="togglePlay(row)"
            >
              {{ playingId === row.musicId ? '暂停' : '试听' }}
            </el-button>
            <RowStatusActions :row="row" :actions="batchActions" :batch="batch" :disabled="loading" />
            <el-button type="primary" v-if="can('blog:music:update', true)" link @click="edit(row)">
              编辑
            </el-button>
            <el-button type="danger" v-if="can('blog:music:delete', true)" link @click="remove(row)">
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
      :title="id ? '编辑音乐' : '新建音乐'"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!saving && !uploading"
      :close-on-press-escape="!saving && !uploading"
    >
      <form class="edit-form" @submit.prevent="save" :aria-busy="saving || !!uploading">
        <label>
          曲目标题
          <el-input v-model="form.title" type="text" maxlength="200" required />
        </label>
        <label>
          歌手 / 作者
          <el-input v-model="form.artist" type="text" maxlength="200" />
        </label>
        <div class="file-url-row">
          <label>
            音频地址
            <el-input v-model="form.audioUrl" maxlength="2048" required :disabled="uploading === 'audio'" />
          </label>
          <FileUploadButton
            v-if="canUpload"
            kind="audio"
            :disabled="saving || !!uploading"
            :loading="uploading === 'audio'"
            @select="upload($event, 'audio')"
          >上传音频</FileUploadButton>
        </div>
        <div class="file-url-field">
          <div class="file-url-row">
            <label>
              封面地址
              <el-input v-model="form.coverUrl" maxlength="2048" :disabled="uploading === 'image'" />
            </label>
            <FileUploadButton
              v-if="canUpload"
              kind="image"
              :disabled="saving || !!uploading"
              :loading="uploading === 'image'"
              @select="upload($event, 'image')"
            >上传封面</FileUploadButton>
          </div>
          <ImagePreview :src="form.coverUrl" alt="音乐封面预览" />
        </div>
        <label>
          时长（秒，留空为未知）
          <el-input-number
            v-model="form.durationSeconds"
            :min="0"
            :max="4294967295"
            :precision="0"
          />
        </label>
        <div class="file-url-row">
          <label>
            歌词地址
            <el-input v-model="form.lyricUrl" maxlength="2048" :disabled="uploading === 'lyric'" />
          </label>
          <FileUploadButton
            v-if="canUpload"
            kind="lyric"
            :disabled="saving || !!uploading"
            :loading="uploading === 'lyric'"
            @select="upload($event, 'lyric')"
          >上传歌词</FileUploadButton>
        </div>
        <label>
          歌词格式
          <el-radio-group v-model="form.lyricType" :disabled="uploading === 'lyric'">
            <el-radio value="">无</el-radio>
            <el-radio value="lrc">LRC</el-radio>
            <el-radio value="json">JSON</el-radio>
            <el-radio value="txt">TXT</el-radio>
          </el-radio-group>
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
          展示音乐
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            :disabled="Boolean(id) && !can('blog:music:update', true)"
          />
        </label>
        <div class="dialog-actions">
          <el-button :disabled="saving || !!uploading" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="!!uploading">{{ uploading ? '正在上传文件' : '保存音乐' }}</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
