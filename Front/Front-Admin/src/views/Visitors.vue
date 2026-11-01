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
  '/admin/visitors',
  { country: '', province: '', city: '', isBlocked: '' },
)
const busy = ref(false)
const detail = ref(null)
const detailVisible = ref(false)
const blockVisible = ref(false)
const blockedUntil = ref(null)
const revision = ref('')
const formError = ref('')

function isBlocked(row) {
  return (
    row.isBlocked === 1 && (!row.blockedUntil || new Date(row.blockedUntil).getTime() > Date.now())
  )
}

async function view(row, block = false) {
  try {
    const result = await request(`/admin/visitors/${row.visitorId}`)
    detail.value = result.data
    revision.value = result.revision
    if (block) {
      blockedUntil.value = null
      formError.value = ''
      blockVisible.value = true
    } else {
      detailVisible.value = true
    }
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function block() {
  formError.value = ''
  if (blockedUntil.value && new Date(blockedUntil.value).getTime() <= Date.now()) {
    formError.value = '封禁结束时间必须晚于当前时间。'
    return
  }
  busy.value = true
  try {
    await request('/admin/visitors/batch-block', {
      method: 'PUT',
      body: {
        items: [{ id: detail.value.visitorId, revision: revision.value }],
        blockedUntil: blockedUntil.value ? new Date(blockedUntil.value).toISOString() : null,
      },
    })
    blockVisible.value = false
    ElMessage.success('已限制该访客统计标识')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    busy.value = false
  }
}

async function action(row, operation) {
  try {
    await ElMessageBox.confirm(
      operation === 'delete'
        ? '删除该访客统计记录？关联浏览明细中的访客 ID 会置空。'
        : '解除该访客统计标识的限制？',
      '管理访客',
      { confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const path = `/admin/visitors/${row.visitorId}`
    const result = await request(path)
    await request(`/admin/visitors/${operation === 'delete' ? 'batch-delete' : 'batch-unblock'}`, {
      method: operation === 'delete' ? 'POST' : 'PUT',
      body: { items: [{ id: row.visitorId, revision: result.revision }] },
    })
    ElMessage.success('操作已完成')
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
  if (can('blog:visitor:block', true)) {
    actions.push({
      label: '批量封禁',
      path: 'batch-block',
      method: 'PUT',
      block: true,
    })
  }
  if (can('blog:visitor:unblock', true)) {
    actions.push({
      label: '批量解封',
      path: 'batch-unblock',
      method: 'PUT',
    })
  }
  if (can('blog:visitor:delete', true)) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
      warning: '浏览明细保留，访客关联置空，UV可能减少，PV不变。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">ANALYTICS / VISITORS</p>
        <h1>访客管理</h1>
        <p class="muted-text">查看访客地域与访问时间，管理统计标识的采集限制。</p>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.country"
          aria-label="国家"
          placeholder="国家（精确）"
          maxlength="64"
          clearable
        />
        <el-input
          v-model="filters.province"
          aria-label="省份"
          placeholder="省份（精确）"
          maxlength="64"
          clearable
        />
        <el-input
          v-model="filters.city"
          aria-label="城市"
          placeholder="城市（精确）"
          maxlength="64"
          clearable
        />
        <el-select
          v-model="filters.isBlocked"
          aria-label="持久化封禁标记"
          placeholder="全部封禁标记"
          clearable
        >
          <el-option label="已标记封禁" :value="1" />
          <el-option label="未标记封禁" :value="0" />
        </el-select>
        <el-button native-type="submit" type="primary">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>访客列表</h2>
        <span class="muted-text">统计标识不代表登录身份</span>
      </div>
            <BatchActions
        ref="batch" resource="/admin/visitors" id-key="visitorId"
        :rows="rows" :table="batchTable" :actions="batchActions"
        :disabled="loading || busy || !!error" @completed="load()"
      />
      <el-table
        ref="batchTable"
        @selection-change="batch?.select"
        v-if="!error"
        v-loading="loading || busy || batch?.busy"
        :data="rows"
        row-key="visitorId"
        empty-text="暂无访客"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="visitorId" label="访客 ID" min-width="180" />
        <el-table-column label="地域" min-width="180">
          <template #default="{ row }">
            {{ [row.country, row.province, row.city].filter(Boolean).join(' / ') || '未知' }}
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP 网段" min-width="150" />
        <el-table-column label="采集状态" width="130">
          <template #default="{ row }">
            <span :class="['status-tag', { 'is-active': !isBlocked(row) }]">
              <i></i>
              {{ isBlocked(row) ? '已限制' : '正常' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最近访问" width="180">
          <template #default="{ row }">{{ formatTime(row.lastVisitTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link @click="view(row)">详情</el-button>
            <el-button v-if="can('blog:visitor:block', true)" link @click="view(row, true)">
              限制
            </el-button>
            <el-button
              v-if="can('blog:visitor:unblock', true) && row.isBlocked"
              link
              @click="action(row, 'unblock')"
            >
              解除
            </el-button>
            <el-button v-if="can('blog:visitor:delete', true)" link @click="action(row, 'delete')">
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
    <el-dialog v-model="detailVisible" title="访客详情" width="680px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="访客 ID">{{ detail.visitorId }}</el-descriptions-item>
        <el-descriptions-item label="统计标识">{{ detail.fingerprint }}</el-descriptions-item>
        <el-descriptions-item label="IP 网段">{{ detail.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ detail.userAgent }}</el-descriptions-item>
        <el-descriptions-item label="首次访问">
          {{ formatTime(detail.firstVisitTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="最近访问">
          {{ formatTime(detail.lastVisitTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="封禁结束">
          {{
            detail.isBlocked
              ? detail.blockedUntil
                ? formatTime(detail.blockedUntil)
                : '永久'
              : '未封禁'
          }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
    <el-dialog
      v-model="blockVisible"
      title="限制访客统计标识"
      width="500px"
      :close-on-click-modal="false"
      :show-close="!busy"
    >
      <form class="edit-form" @submit.prevent="block">
        <p class="muted-text">仅限制该统计标识继续采集访问数据。</p>
        <label>
          结束时间（留空为永久）
          <el-date-picker v-model="blockedUntil" type="datetime" placeholder="选择结束时间" />
        </label>
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="busy" @click="blockVisible = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="busy">确认限制</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
