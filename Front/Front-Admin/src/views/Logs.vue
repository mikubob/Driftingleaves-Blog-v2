<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'

const { filters, rows, page, total, totalPages, loading, error, load, reset, changePage } = useList(
  '/admin/operation-logs',
  {
    userId: '',
    operationType: '',
    operationTarget: '',
    targetId: '',
    result: '',
    begin: '',
    end: '',
  },
)
const busy = ref(false)
const detail = ref(null)
const visible = ref(false)

async function view(row) {
  try {
    detail.value = (await request(`/admin/operation-logs/${row.logId}`)).data
    visible.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      '清理这条操作日志？本次清理操作会新增一条审计记录。',
      '删除操作日志',
      { confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  busy.value = true
  try {
    await request(`/admin/operation-logs/${row.logId}`, { method: 'DELETE' })
    ElMessage.success('记录已删除')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">SYSTEM / AUDIT LOGS</p>
        <h1>操作日志</h1>
        <p class="muted-text">SUB操作日志</p>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.userId"
          aria-label="操作人 ID"
          placeholder="操作人 ID"
          maxlength="100"
          clearable
          pattern="[1-9][0-9]{0,18}"
        />
        <el-input
          v-model="filters.operationType"
          aria-label="操作类型"
          placeholder="操作类型"
          maxlength="32"
          clearable
        />
        <el-input
          v-model="filters.operationTarget"
          aria-label="操作目标"
          placeholder="操作目标"
          maxlength="100"
          clearable
        />
        <el-input
          v-model="filters.targetId"
          aria-label="目标 ID"
          placeholder="目标 ID"
          maxlength="100"
          clearable
          pattern="[1-9][0-9]{0,18}"
        />
        <el-select v-model="filters.result" aria-label="操作结果" placeholder="全部结果" clearable>
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
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
      <div class="card-heading"><h2>操作日志列表</h2></div>
      <el-table
        v-if="!error"
        v-loading="loading || busy"
        :data="rows"
        row-key="logId"
        empty-text="暂无记录"
      >
        <el-table-column
          prop="operationType"
          label="操作类型"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column
          prop="operationTarget"
          label="操作目标"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column prop="userId" label="操作人 ID" min-width="180" show-overflow-tooltip />
        <el-table-column
          prop="permissionCode"
          label="权限码"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">{{ row.result === 1 ? '成功' : '失败' }}</template>
        </el-table-column>
        <el-table-column label="操作时间" width="180">
          <template #default="{ row }">{{ formatTime(row.operationTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link @click="view(row)">详情</el-button>
            <el-button v-if="can('sys:log:delete', true)" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <ListPager
        :page="page"
        :total="total"
        :total-pages="totalPages"
        :loading="loading || busy"
        @change="changePage"
      />
    </el-card>
    <el-dialog v-model="visible" title="操作日志详情" width="760px">
      <el-descriptions v-if="detail" :column="1" border>
        detail.value = (await request(`/admin/operation-logs/${row.logId}`)).dataS
      </el-descriptions>
    </el-dialog>
  </section>
</template>
