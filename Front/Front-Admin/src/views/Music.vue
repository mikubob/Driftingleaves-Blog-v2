<script setup>
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
  ElOption,
  ElSelect,
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
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import { request } from '../api.js'
import { fileAccept, uploadFile } from '../fileUpload.js'
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
const formError = ref('')
const id = ref('')
const revision = ref('')
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
async function upload(event, kind) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || saving.value || uploading.value) {
    return
  }
  uploading.value = kind
  formError.value = ''
  try {
    const result = await uploadFile(file, kind)
    const field = { image: 'coverUrl', audio: 'audioUrl', lyric: 'lyricUrl' }[kind]
    form[field] = `/api/v1/blog/music/files/${result.file.fileId}/content`
    if (kind === 'lyric') {
      form.lyricType = result.extension
    }
    ElMessage.success('文件已上传，请保存音乐；未使用文件可在文件管理中清理。')
  } catch (failure) {
    formError.value = failure.message
  } finally {
    uploading.value = ''
  }
}

async function edit(row) {
  formError.value = ''
  try {
    id.value = row?.musicId || ''
    if (row) {
      const result = await request(`/admin/music/${row.musicId}`)
      revision.value = result.revision
      const data = result.data
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
  formError.value = ''
  if (Boolean(form.lyricUrl.trim()) !== Boolean(form.lyricType)) {
    formError.value = '歌词地址和歌词格式必须同时填写或同时清空。'
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
    await request(id.value ? `/admin/music/${id.value}` : '/admin/music', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body,
    })
    dialog.value = false
    ElMessage.success('音乐已保存')
    load()
  } catch (failure) {
    formError.value = failure.message
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
    const detail = await request(`/admin/music/${row.musicId}`)
    await request('/admin/music/batch-delete', { method: 'POST', body: { items: [{ id: row.musicId, revision: detail.revision }] } })
    ElMessage.success('音乐已删除')
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
          <el-option label="隐藏" :value="0" />
        </el-select>
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
        <el-table-column prop="title" label="曲目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="artist" label="歌手" min-width="150" show-overflow-tooltip />
        <el-table-column prop="audioUrl" label="音频地址" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="display" /></template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
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
        <label>
          音频地址
          <el-input v-model="form.audioUrl" maxlength="2048" required placeholder="外部 HTTP(S) 地址或上传文件" />
        </label>
        <label v-if="can('blog:file:upload') && can('blog:file:query')">
          上传音频（MP3/WAV/OGG/FLAC，≤60 MiB）
          <input type="file" :accept="fileAccept.audio" :disabled="saving || !!uploading" @change="upload($event, 'audio')" />
        </label>
        <label>
          封面地址
          <el-input v-model="form.coverUrl" maxlength="2048" />
          <ImagePreview :src="form.coverUrl" alt="音乐封面预览" />
        </label>
        <label v-if="can('blog:file:upload') && can('blog:file:query')">
          上传封面（PNG/JPEG，≤10 MiB）
          <input type="file" :accept="fileAccept.image" :disabled="saving || !!uploading" @change="upload($event, 'image')" />
        </label>
        <label>
          时长（秒，留空为未知）
          <el-input-number
            v-model="form.durationSeconds"
            :min="0"
            :max="4294967295"
            :precision="0"
          />
        </label>
        <label>
          歌词地址
          <el-input v-model="form.lyricUrl" maxlength="2048" />
        </label>
        <label v-if="can('blog:file:upload') && can('blog:file:query')">
          上传歌词（UTF-8 LRC/TXT/JSON，≤1 MiB，自动填写格式）
          <input type="file" :accept="fileAccept.lyric" :disabled="saving || !!uploading" @change="upload($event, 'lyric')" />
          <span class="muted-text">JSON 为 [{"time":0,"text":"歌词"}]，time 单位为秒。</span>
        </label>
        <label>
          歌词格式
          <el-select v-model="form.lyricType" clearable>
            <el-option label="LRC" value="lrc" />
            <el-option label="JSON" value="json" />
            <el-option label="TXT" value="txt" />
          </el-select>
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
        <label v-if="!id">
          展示状态
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving || !!uploading" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving" :disabled="!!uploading">{{ uploading ? '正在上传文件' : '保存音乐' }}</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
