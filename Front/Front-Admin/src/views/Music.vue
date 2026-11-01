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
  '/admin/music',
  { keyword: '', status: '' },
)
const dialog = ref(false)
const saving = ref(false)
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
  formError.value = ''
  if (Boolean(form.lyricUrl.trim()) !== Boolean(form.lyricType)) {
    formError.value = '歌词地址和歌词格式必须同时填写或同时清空。'
    return
  }
  saving.value = true
  try {
    await request(id.value ? `/admin/music/${id.value}` : '/admin/music', {
      method: id.value ? 'PUT' : 'POST',
      revision: id.value ? revision.value : undefined,
      body: {
        title: form.title,
        artist: nullable(form.artist),
        durationSeconds: form.durationSeconds ?? null,
        coverUrl: nullable(form.coverUrl),
        audioUrl: form.audioUrl,
        lyricUrl: nullable(form.lyricUrl),
        lyricType: nullable(form.lyricType),
        sortOrder: form.sortOrder,
        status: form.status,
      },
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
        <p class="eyebrow">MUSIC</p>
        <h1>音乐管理</h1>
        <p class="muted-text">SUB音乐</p>
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
          <el-option label="IN展示中" :value="0" />
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
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
      :show-close="!saving"
      :close-on-press-escape="!saving"
    >
      <form class="edit-form" @submit.prevent="save">
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
          <el-input v-model="form.audioUrl" type="url" maxlength="2048" required />
        </label>
        <label>
          封面地址
          <el-input v-model="form.coverUrl" type="url" maxlength="2048" />
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
          <el-input v-model="form.lyricUrl" type="url" maxlength="2048" />
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
        <label>
          展示状态
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </label>
        <p v-if="formError" class="error-text" role="alert">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="saving" @click="dialog = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="saving">保存音乐</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
