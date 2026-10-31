<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import StatusTag from '../components/StatusTag.vue'

const { filters, rows, page, total, totalPages, loading, error, load, reset, changePage } = useList(
  '/admin/rss-subscriptions',
  { email: '', userId: '', status: '' },
)
const busy = ref(false)
const detail = ref(null)
const visible = ref(false)

async function view(row) {
  try {
    detail.value = (await request(`/admin/rss-subscriptions/${row.subscriptionId}`)).data
    visible.value = true
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function change(row, status) {
  try {
    await ElMessageBox.confirm(
      status === 1 ? '重新激活这条已验证的订阅？' : '将这条订阅设为退订？',
      '管理订阅状态',
      { confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const path = `/admin/rss-subscriptions/${row.subscriptionId}`
    const current = await request(path)
    await request(`${path}/status`, { method: 'PUT', revision: current.revision, body: { status } })
    ElMessage.success('订阅状态已更新')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除「${row.email}」的订阅？旧邮件链接也将失效。`, '删除订阅', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    const path = `/admin/rss-subscriptions/${row.subscriptionId}`
    const current = await request(path)
    await request(path, { method: 'DELETE', revision: current.revision })
    ElMessage.success('订阅已删除')
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
        <p class="eyebrow">CONTENT / SUBSCRIPTIONS</p>
        <h1>邮件订阅</h1>
        <p class="muted-text">管理经过邮箱确认的内容订阅，保持与读者的连接。</p>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.email"
          aria-label="订阅邮箱"
          placeholder="搜索订阅邮箱"
          maxlength="254"
          clearable
        />
        <el-input
          v-model="filters.userId"
          aria-label="用户 ID"
          placeholder="用户 ID"
          pattern="[1-9][0-9]{0,18}"
          clearable
        />
        <el-select v-model="filters.status" aria-label="订阅状态" placeholder="全部状态" clearable>
          <el-option label="待验证" :value="0" />
          <el-option label="已订阅" :value="1" />
          <el-option label="已退订" :value="2" />
        </el-select>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>订阅列表</h2>
        <span class="muted-text">待验证邮箱需要用户自行确认</span>
      </div>
      <el-table
        v-if="!error"
        v-loading="loading || busy"
        :data="rows"
        row-key="subscriptionId"
        empty-text="暂无订阅"
      >
        <el-table-column prop="email" label="订阅邮箱" min-width="230" />
        <el-table-column prop="userId" label="所属用户 ID" min-width="180" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.status" kind="subscription" />
          </template>
        </el-table-column>
        <el-table-column label="订阅时间" width="180">
          <template #default="{ row }">{{ formatTime(row.subscribeTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button link @click="view(row)">详情</el-button>
            <el-button
              v-if="can('blog:rss:update', true) && row.status !== 2"
              link
              @click="change(row, 2)"
            >
              退订
            </el-button>
            <el-button
              v-if="can('blog:rss:update', true) && row.status === 2 && row.verifiedAt"
              link
              @click="change(row, 1)"
            >
              激活
            </el-button>
            <el-button v-if="can('blog:rss:delete', true)" link @click="remove(row)">
              删除
            </el-button>
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
    <el-dialog v-model="visible" title="订阅详情" width="650px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="邮箱">{{ detail.email }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ detail.userId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :value="detail.status" kind="subscription" />
        </el-descriptions-item>
        <el-descriptions-item label="验证时间">
          {{ formatTime(detail.verifiedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="订阅时间">
          {{ formatTime(detail.subscribeTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="退订时间">
          {{ formatTime(detail.unsubscribeTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </section>
</template>
