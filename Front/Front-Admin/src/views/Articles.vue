<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOptions, request } from '../api.js'
import { can } from '../session.js'
import { formatCount, formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'
import SafeHtml from '../components/SafeHtml.vue'

const route = useRoute()
const router = useRouter()
const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/articles',
  {
    keyword: '',
    status: '',
    visibility: '',
    categoryId: '',
    tagId: '',
    authorId: '',
    trash: 0,
  },
)
const categories = ref([])
const tags = ref([])
const detail = ref(null)
const detailVisible = ref(false)
const busy = ref(false)
const trash = computed(() => filters.trash === 1)

function switchTrash(value) {
  filters.trash = value
  load(true)
}

async function view(row) {
  try {
    detail.value = (await request(`/admin/articles/${row.articleId}`)).data
    detailVisible.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function action(row, operation) {
  const titles = {
    publish: '发布文章',
    unpublish: '下架文章',
    recycle: '移入回收站',
    restore: '恢复文章',
    purge: '永久删除文章',
  }
  let prompt = `确认${titles[operation]}「${row.title}」？`
  if (operation === 'purge') {
    prompt += '正文、评论和点赞将被彻底删除，无法恢复。'
  }
  try {
    await ElMessageBox.confirm(prompt, titles[operation], {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    const path = `/admin/articles/${row.articleId}`
    const current = await request(path)
    const items = [{ id: row.articleId, revision: current.revision }]
    if (operation === 'publish' || operation === 'unpublish') {
      await request('/admin/articles/batch-status', {
        method: 'PUT',
        body: { items, status: operation === 'publish' ? 1 : 2 },
      })
    } else {
      const actions = { recycle: 'batch-recycle', restore: 'batch-restore', purge: 'batch-permanent-delete' }
      await request(`/admin/articles/${actions[operation]}`, { method: 'POST', body: { items } })
    }
    ElMessage.success(`${titles[operation]}成功`)
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

onActivated(() => load())
onMounted(async () => {
  if (!route.meta.keepAlive) {
    load()
  }
  try {
    if (can('blog:category:query')) {
      categories.value = await getOptions('/admin/categories')
    }
    if (can('blog:tag:query')) {
      tags.value = await getOptions('/admin/tags')
    }
  } catch (failure) {
    ElMessage.error(`筛选选项加载失败：${failure.message}`)
  }
})

const batch = ref(null)
const batchTable = ref(null)
const batchActions = computed(() => {
  const actions = []
  if (!trash.value && can('blog:article:publish')) {
    actions.push({
      label: '批量发布',
      type: 'success',
      path: 'batch-status',
      method: 'PUT',
      body: { status: 1 },
    })
  }
  if (!trash.value && can('blog:article:publish')) {
    actions.push({
      label: '批量下架',
      type: 'warning',
      path: 'batch-status',
      method: 'PUT',
      body: { status: 2 },
      accept: (row) => row.status !== 0,
      invalidMessage: '草稿不能直接下架，请只选择已发布或已下架的文章。',
    })
  }
  if (!trash.value && can('blog:article:delete')) {
    actions.push({
      label: '批量回收',
      path: 'batch-recycle',
      danger: true,
    })
  }
  if (trash.value && can('blog:article:restore')) {
    actions.push({
      label: '批量恢复',
      type: 'success',
      path: 'batch-restore',
    })
  }
  if (trash.value && can('blog:article:delete') && can('blog:article:purge')) {
    actions.push({
      label: '批量永久删除',
      path: 'batch-permanent-delete',
      danger: true,
      warning: '正文、全部评论和点赞将被彻底删除，无法恢复。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">CONTENT / ARTICLES</p>
        <h1>文章管理</h1>
        <p class="muted-text">记录、整理与发布，让每一个想法有所归处。</p>
      </div>
      <el-button
        v-if="can('blog:article:create')"
        type="primary"
        @click="router.push({ name: 'article-editor' })"
      >
        <AdminIcon name="plus" />
        写文章
      </el-button>
    </div>
    <div class="page-tabs">
      <button :class="{ active: !trash }" @click="switchTrash(0)">全部文章</button>
      <button :class="{ active: trash }" @click="switchTrash(1)">
        <AdminIcon name="delete" />
        回收站
      </button>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.keyword"
          aria-label="文章关键词"
          placeholder="搜索文章标题或摘要"
          maxlength="100"
          clearable
        >
          <template #prefix><AdminIcon name="search" /></template>
        </el-input>
        <el-select v-model="filters.status" aria-label="文章状态" placeholder="全部状态" clearable>
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
          <el-option label="已下架" :value="2" />
        </el-select>
        <el-select
          v-if="can('blog:category:query')"
          v-model="filters.categoryId"
          aria-label="分类"
          placeholder="全部分类"
          filterable
          clearable
        >
          <el-option
            v-for="item in categories"
            :key="item.categoryId"
            :value="item.categoryId"
            :label="item.categoryName"
          />
        </el-select>
        <el-select
          v-if="can('blog:tag:query')"
          v-model="filters.tagId"
          aria-label="标签"
          placeholder="全部标签"
          filterable
          clearable
        >
          <el-option
            v-for="item in tags"
            :key="item.tagId"
            :value="item.tagId"
            :label="item.tagName"
          />
        </el-select>
        <el-select
          v-model="filters.visibility"
          aria-label="可见性"
          placeholder="全部可见性"
          clearable
        >
          <el-option label="公开" :value="1" />
          <el-option label="私密" :value="2" />
        </el-select>
        <el-input
          v-model="filters.authorId"
          aria-label="作者 ID"
          placeholder="作者 ID"
          pattern="[1-9][0-9]{0,18}"
          clearable
        />
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>
          {{ trash ? '回收站' : '文章列表' }}
          <span class="heading-count">{{ formatCount(total) }}</span>
        </h2>
        <el-button text aria-label="刷新文章" @click="load()">
          <AdminIcon name="refresh" />
        </el-button>
      </div>
            <BatchActions
        ref="batch" resource="/admin/articles" id-key="articleId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || busy || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || busy || batch?.busy"
        :data="rows"
        row-key="articleId"
        border
        stripe
        empty-text="这里还没有文章"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column label="文章" min-width="290">
          <template #default="{ row }">
            <button class="table-title" @click="view(row)">{{ row.title }}</button>
            <span v-if="row.isTop" class="small-badge">置顶</span>
            <p class="table-secondary">
              {{ row.author?.nickname }}
              <span>·</span>
              {{ row.category?.categoryName || '未分类' }}
              <span>·</span>
              {{ row.visibility === 1 ? '公开' : '私密' }}
            </p>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="article" /></template>
        </el-table-column>
        <el-table-column label="阅读 / 点赞" width="130">
          <template #default="{ row }">
            {{ formatCount(row.viewCount) }} / {{ formatCount(row.likeCount) }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="175">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="235" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              v-if="!trash && can('blog:article:update')"
              link
              @click="router.push({ name: 'article-editor', params: { id: row.articleId } })"
            >
              编辑
            </el-button>
            <el-button
              v-if="!trash && can('blog:article:publish')"
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="action(row, row.status === 1 ? 'unpublish' : 'publish')"
            >
              {{ row.status === 1 ? '下架' : '发布' }}
            </el-button>
            <el-button
              type="danger"
              v-if="!trash && can('blog:article:delete')"
              link
              @click="action(row, 'recycle')"
            >
              回收
            </el-button>
            <el-button
              type="success"
              v-if="trash && can('blog:article:restore')"
              link
              @click="action(row, 'restore')"
            >
              恢复
            </el-button>
            <el-button
              type="danger"
              v-if="trash && can('blog:article:delete') && can('blog:article:purge')"
              link
              class="danger-text"
              @click="action(row, 'purge')"
            >
              永久删除
            </el-button>
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
    <el-dialog v-model="detailVisible" title="文章预览" width="850px" destroy-on-close>
      <template v-if="detail">
        <h1>{{ detail.title }}</h1>
        <p class="muted-text">
          {{ detail.author?.nickname }} · {{ formatTime(detail.updateTime) }}
        </p>
        <SafeHtml :html="detail.htmlContent" />
      </template>
    </el-dialog>
  </section>
</template>
