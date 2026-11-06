<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/descriptions-item/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'
import { getOptions, request } from '../api.js'
import { can } from '../session.js'
import { formatCount, formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'
import StatusTag from '../components/StatusTag.vue'
import AdminIcon from '../components/AdminIcon.vue'
import MarkdownPreview from '../components/MarkdownPreview.vue'

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
const detailLoading = ref(false)
const detailError = ref('')
const selectedArticle = ref(null)
let detailController
const busy = ref(false)
const trash = computed(() => filters.trash === 1)

function switchTrash(value) {
  filters.trash = value
  load(true)
}

/** 查询文章完整详情，切换文章时取消上次请求，避免显示旧正文。 */
async function view(row) {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  selectedArticle.value = row
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  detailVisible.value = true
  try {
    const result = await request(`/admin/articles/${row.articleId}`, { signal: controller.signal })
    if (!controller.signal.aborted) {
      detail.value = result.data
    }
  } catch (failure) {
    if (!controller.signal.aborted) {
      detailError.value = failure.message
    }
  } finally {
    if (detailController === controller) {
      detailLoading.value = false
    }
  }
}

/** 关闭详情并取消未完成的查询。 */
function closeDetail() {
  detailController?.abort()
  detailVisible.value = false
}

onBeforeUnmount(closeDetail)
onDeactivated(closeDetail)

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
        <h1>文章管理</h1>
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
        <el-table-column label="阅读量" width="90" align="center">
          <template #default="{ row }">{{ formatCount(row.viewCount) }}</template>
        </el-table-column>
        <el-table-column label="点赞数" width="90" align="center">
          <template #default="{ row }">{{ formatCount(row.likeCount) }}</template>
        </el-table-column>
        <el-table-column label="评论数" width="90" align="center">
          <template #default="{ row }">{{ formatCount(row.commentCount) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="175">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" min-width="235" fixed="right">
          <template #default="{ row }">
            <el-button v-if="can('blog:article:query')" type="primary" link @click="view(row)">
              详情
            </el-button>
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
    <el-dialog
      v-model="detailVisible"
      title="文章详情"
      width="min(960px, 94vw)"
      destroy-on-close
      @close="closeDetail"
    >
      <div v-loading="detailLoading" class="article-detail" :aria-busy="detailLoading">
        <p v-if="detailLoading" role="status">正在加载文章详情…</p>
        <RequestError :message="detailError" @retry="view(selectedArticle)" />
        <template v-if="detail">
          <h2>{{ detail.title }}</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="文章 ID">{{ detail.articleId }}</el-descriptions-item>
            <el-descriptions-item label="作者">
              {{ detail.author?.nickname }}（ID：{{ detail.authorId }}）
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <StatusTag :value="detail.status" kind="article" />
              <el-tag v-if="detail.deletedAt" type="danger">回收站</el-tag>
              <el-tag v-if="detail.isTop">置顶</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="可见性">{{ detail.visibility === 1 ? '公开' : '私密' }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ detail.category?.categoryName || '未分类' }}</el-descriptions-item>
            <el-descriptions-item label="标签">
              <template v-if="detail.tags.length">
                <el-tag v-for="tag in detail.tags" :key="tag.tagId">{{ tag.tagName }}</el-tag>
              </template>
              <span v-else>无标签</span>
            </el-descriptions-item>
            <el-descriptions-item label="URL 标识">{{ detail.slug }}</el-descriptions-item>
            <el-descriptions-item label="文章类型">{{ { 1: '原创', 2: '转载', 3: '翻译' }[detail.articleType] }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.originalUrl" label="原文地址">{{ detail.originalUrl }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.coverUrl" label="封面地址">{{ detail.coverUrl }}</el-descriptions-item>
            <el-descriptions-item label="摘要"><span class="article-summary">{{ detail.summary || '暂无摘要' }}</span></el-descriptions-item>
            <el-descriptions-item label="允许评论">{{ detail.allowComment ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="阅读 / 点赞 / 评论">
              {{ formatCount(detail.viewCount) }} / {{ formatCount(detail.likeCount) }} / {{ formatCount(detail.commentCount) }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
            <el-descriptions-item label="首次发布时间">{{ formatTime(detail.publishTime) }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.deletedAt" label="回收时间">{{ formatTime(detail.deletedAt) }}</el-descriptions-item>
          </el-descriptions>
          <h3>文章正文</h3>
          <MarkdownPreview v-if="detail.markdownContent" :markdown="detail.markdownContent" />
          <el-empty v-else description="暂无正文" :image-size="60" />
        </template>
      </div>
      <template #footer>
        <el-button @click="closeDetail">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.article-detail {
  min-height: 120px;
  overflow-wrap: anywhere;
}
.article-detail :deep(.el-descriptions__label) {
  width: 145px;
}
.article-detail .el-tag + .el-tag {
  margin-left: 6px;
}
.article-summary {
  white-space: pre-wrap;
}
</style>
