<script setup>
import { onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import BatchActions from '../components/BatchActions.vue'

const {
  filters, rows, page, pageSize, total, totalPages, loading, error, load, reset, changePage, changePageSize,
} = useList(
  '/admin/page-views',
  {
    visitorId: '',
    userId: '',
    articleId: '',
    siteCode: '',
    pagePath: '',
    referer: '',
    begin: '',
    end: '',
  },
)
const busy = ref(false)
const detail = ref(null)
const visible = ref(false)

async function view(row) {
  try {
    detail.value = { ...row }
    visible.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      '删除这条浏览明细？日期范围内的报表将同步变化，文章累计阅读数不回退。',
      '删除浏览记录',
      { confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  busy.value = true
  try {
    await request('/admin/page-views/batch-delete', { method: 'POST', body: { ids: [row.viewId] } })
    ElMessage.success('记录已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}
onMounted(load)

const batch = ref(null)
const batchTable = ref(null)
const batchActions = computed(() => {
  const actions = []
  if (can('blog:view:delete', true)) {
    actions.push({
      label: '批量清理',
      path: 'batch-delete',
      revision: false,
      danger: true,
      warning: '清理会影响基于明细的PV/UV报表，不扣减文章累计阅读数。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">ANALYTICS / PAGE VIEWS</p>
        <h1>浏览记录</h1>
        <p class="muted-text">SUB浏览记录</p>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.visitorId"
          aria-label="访客 ID"
          placeholder="访客 ID"
          maxlength="100"
          clearable
          pattern="[1-9][0-9]{0,18}"
        />
        <el-input
          v-model="filters.userId"
          aria-label="用户 ID"
          placeholder="用户 ID"
          maxlength="100"
          clearable
          pattern="[1-9][0-9]{0,18}"
        />
        <el-input
          v-model="filters.articleId"
          aria-label="文章 ID"
          placeholder="文章 ID"
          maxlength="100"
          clearable
          pattern="[1-9][0-9]{0,18}"
        />
        <el-select v-model="filters.siteCode" aria-label="站点" placeholder="全部站点" clearable>
          <el-option label="主页" value="home" />
          <el-option label="博客" value="blog" />
          <el-option label="简历" value="cv" />
        </el-select>
        <el-input
          v-model="filters.pagePath"
          aria-label="页面路径（精确）"
          placeholder="页面路径（精确）"
          maxlength="2048"
          clearable
        />
        <el-input
          v-model="filters.referer"
          aria-label="来源地址"
          placeholder="来源地址"
          maxlength="2048"
          clearable
        />
        <el-date-picker
          v-model="filters.begin"
          type="datetime"
          aria-label="开始时间"
          placeholder="开始时间（含）"
          value-format="YYYY-MM-DDTHH:mm:ss.SSSZ"
        />
        <el-date-picker
          v-model="filters.end"
          type="datetime"
          aria-label="结束时间"
          placeholder="结束时间（不含）"
          value-format="YYYY-MM-DDTHH:mm:ss.SSSZ"
        />
        <el-button native-type="submit" type="primary">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading"><h2>浏览记录列表</h2></div>
            <BatchActions
        ref="batch" resource="/admin/page-views" id-key="viewId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || busy || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || busy || batch?.busy"
        :data="rows"
        row-key="viewId"
        border
        stripe
        empty-text="暂无记录"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="pageTitle" label="页面标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="siteCode" label="站点" min-width="80" show-overflow-tooltip />
        <el-table-column prop="pagePath" label="页面路径" min-width="240" show-overflow-tooltip />
        <el-table-column prop="referer" label="来源" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP 地址" min-width="160" show-overflow-tooltip />
        <el-table-column label="访问时间" width="180">
          <template #default="{ row }">{{ formatTime(row.viewTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="view(row)">详情</el-button>
            <el-button type="danger" v-if="can('blog:view:delete', true)" link @click="remove(row)">
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
        :loading="loading || busy"
        @change="changePage"
        @size-change="changePageSize"
      />
    </el-card>
    <el-dialog v-model="visible" title="浏览记录详情" width="760px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="记录 ID">{{ detail.viewId }}</el-descriptions-item>
        <el-descriptions-item label="访客 ID">{{ detail.visitorId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ detail.userId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="文章 ID">{{ detail.articleId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="页面标题">{{ detail.pageTitle || '—' }}</el-descriptions-item>
        <el-descriptions-item label="站点">{{ detail.siteCode }}</el-descriptions-item>
        <el-descriptions-item label="页面路径">{{ detail.pagePath }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.referer || '—' }}</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ detail.ipAddress || '—' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ detail.userAgent || '—' }}</el-descriptions-item>
        <el-descriptions-item label="访问时间">
          {{ formatTime(detail.viewTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </section>
</template>
