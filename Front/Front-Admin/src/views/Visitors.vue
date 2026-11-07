<script setup>
import { onMounted, ref, computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElDatePicker,
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
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/date-picker/style/css'
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
import { request } from '../api.js'
import { can } from '../session.js'
import { formatCount, formatTime } from '../format.js'
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

/** 有效的 IP 封禁记录要先解封，才能删除。 */
function canDelete(row) {
  return !row.blockedIpAddress || !isBlocked(row)
}

/** 已有封禁时沿用原地址，否则使用最近访问的 IP。 */
function blockTarget(row) {
  return row.blockedIpAddress || row.ipAddress
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
    ElMessage.success('已封禁该 IP')
    load()
  } catch (failure) {
    formError.value = failure.message
  } finally {
    busy.value = false
  }
}

async function action(row, operation) {
  let confirmation = '解除该访客统计标识的采集限制？'
  if (operation === 'delete') {
    if (!canDelete(row)) {
      ElMessage.warning('请先解除该记录的 IP 封禁再删除。')
      return
    }
    confirmation = '删除该访客统计记录？关联浏览明细中的访客 ID 会置空。'
  } else if (row.blockedIpAddress) {
    confirmation = `解除本条记录对 ${row.blockedIpAddress} 的封禁？同 IP 的其他封禁仍然有效。`
  }
  try {
    await ElMessageBox.confirm(confirmation, '管理访客', { confirmButtonText: '确认', cancelButtonText: '取消' })
  } catch {
    return
  }
  busy.value = true
  try {
    const path = `/admin/visitors/${row.visitorId}`
    const result = await request(path)
    if (operation === 'unblock' && row.blockedIpAddress !== result.data.blockedIpAddress) {
      throw new Error('封禁地址已变化，请刷新列表后重试。')
    }
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
      label: '批量封禁 IP',
      type: 'warning',
      path: 'batch-block',
      method: 'PUT',
      block: true,
      accept: (row) => !!blockTarget(row),
      invalidMessage: '所选记录中有访客没有可封禁的 IP。',
      matchDetail: (row, latest) => blockTarget(row) === blockTarget(latest),
      warning: '使用已保存的封禁地址或最近访问 IP。同一 IP 下的访客均无法读取公开内容、评论或留言。',
    })
  }
  if (can('blog:visitor:unblock', true)) {
    actions.push({
      label: '批量解封',
      type: 'success',
      path: 'batch-unblock',
      method: 'PUT',
      matchDetail: (row, latest) => row.blockedIpAddress === latest.blockedIpAddress,
      warning: '解除所选记录的封禁。同 IP 的其他封禁仍然有效。',
    })
  }
  if (can('blog:visitor:delete', true)) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      danger: true,
      accept: canDelete,
      invalidMessage: '请先解除所选记录中的有效 IP 封禁。',
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
        <h1>访客管理</h1>
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
        <span class="muted-text">IP 封禁会影响共用该地址的访客</span>
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
        border
        stripe
        empty-text="暂无访客"
      >
        <el-table-column v-if="batchActions.length" type="selection" width="48" fixed="left" :selectable="batch?.selectable" />
        <el-table-column prop="ipAddress" label="IP 地址" min-width="160" show-overflow-tooltip />
        <el-table-column prop="country" label="国家" min-width="100" show-overflow-tooltip />
        <el-table-column prop="province" label="省份" min-width="100" show-overflow-tooltip />
        <el-table-column prop="city" label="城市" min-width="100" show-overflow-tooltip />
        <el-table-column label="浏览次数" min-width="100" align="center">
          <template #default="{ row }">{{ formatCount(row.totalViews) }}</template>
        </el-table-column>
        <el-table-column label="本条封禁" width="110" align="center">
          <template #default="{ row }">
            <span :class="['status-tag', isBlocked(row) ? 'is-danger' : 'is-success']">
              <i aria-hidden="true"></i>
              <template v-if="isBlocked(row)">{{ row.blockedIpAddress ? 'IP 已封禁' : '限制采集' }}</template>
              <template v-else>正常</template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="首次访问" width="180" align="center">
          <template #default="{ row }">{{ formatTime(row.firstVisitTime) }}</template>
        </el-table-column>
        <el-table-column label="最近访问" width="180" align="center">
          <template #default="{ row }">{{ formatTime(row.lastVisitTime) }}</template>
        </el-table-column>
        <el-table-column label="封禁到期" width="180" align="center">
          <template #default="{ row }">
            <span v-if="row.isBlocked !== 1">—</span>
            <span v-else-if="row.blockedUntil">{{ formatTime(row.blockedUntil) }}</span>
            <span v-else>永久</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="view(row)">详情</el-button>
            <el-button type="warning" v-if="can('blog:visitor:block', true) && blockTarget(row)" link @click="view(row, true)">
              封禁 IP
            </el-button>
            <el-button
              type="success"
              v-if="can('blog:visitor:unblock', true) && row.isBlocked"
              link
              @click="action(row, 'unblock')"
            >
              解封
            </el-button>
            <el-button type="danger" v-if="can('blog:visitor:delete', true)" link @click="action(row, 'delete')">
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
        <el-descriptions-item label="IP 地址">{{ detail.ipAddress || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.blockedIpAddress" label="封禁 IP">
          {{ detail.blockedIpAddress }}
        </el-descriptions-item>
        <el-descriptions-item label="地域">
          {{ [detail.country, detail.province, detail.city].filter(Boolean).join(' / ') || '未知' }}
        </el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ detail.userAgent }}</el-descriptions-item>
        <el-descriptions-item label="浏览次数">{{ formatCount(detail.totalViews) }}</el-descriptions-item>
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
      title="封禁 IP"
      width="500px"
      :close-on-click-modal="false"
      :show-close="!busy"
    >
      <form class="edit-form" @submit.prevent="block">
        <p v-if="detail" class="muted-text">
          {{ blockTarget(detail) }} 下的访客将无法读取公开内容、发表评论或留言。
        </p>
        <label>
          结束时间（留空为永久）
          <el-date-picker v-model="blockedUntil" type="datetime" placeholder="选择结束时间" />
        </label>
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div class="dialog-actions">
          <el-button :disabled="busy" @click="blockVisible = false">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="busy" :disabled="!detail || !blockTarget(detail)">确认封禁</el-button>
        </div>
      </form>
    </el-dialog>
  </section>
</template>
