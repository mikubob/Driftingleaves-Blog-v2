<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { formatTime } from '../format.js'
import { useList } from '../composables/useList.js'
import ListPager from '../components/ListPager.vue'
import RequestError from '../components/RequestError.vue'
import StatusTag from '../components/StatusTag.vue'
import SafeHtml from '../components/SafeHtml.vue'

const { filters, rows, page, total, totalPages, loading, error, load, reset, changePage } = useList(
  '/admin/messages',
  { status: '', userId: '', isSecret: '', begin: '', end: '' },
)
const detail = ref(null)
const detailVisible = ref(false)
const replyVisible = ref(false)
const busy = ref(false)
const replyError = ref('')
const reply = reactive({ content: '', contentFormat: 0, notifyReply: 0 })

async function view(row, replying = false) {
  try {
    detail.value = (await request(`/admin/messages/${row.messageId}`)).data
    if (replying) {
      Object.assign(reply, { content: '', contentFormat: 0, notifyReply: 0 })
      replyError.value = ''
      replyVisible.value = true
    } else {
      detailVisible.value = true
    }
  } catch (failure) {
    ElMessage.error(failure.message)
  }
}

async function review(row, status) {
  busy.value = true
  try {
    const path = `/admin/messages/${row.messageId}`
    const current = await request(path)
    await request(`${path}/review`, { method: 'PUT', revision: current.revision, body: { status } })
    ElMessage.success('审核状态已更新')
    load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}

async function sendReply() {
  busy.value = true
  replyError.value = ''
  try {
    await request(`/admin/messages/${detail.value.messageId}/replies`, {
      method: 'POST',
      body: {
        content: reply.content,
        contentFormat: reply.contentFormat,
        notifyReply: reply.notifyReply,
      },
    })
    replyVisible.value = false
    ElMessage.success('回复已提交')
    load()
  } catch (failure) {
    replyError.value = failure.message
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('删除这条留言？回复链将保留删除占位。', '删除留言', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await request(`/admin/messages/${row.messageId}`, { method: 'DELETE' })
    ElMessage.success('留言已删除')
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
        <p class="eyebrow">CONTENT / MESSAGES</p>
        <h1>留言管理</h1>
        <p class="muted-text">查看访客的问候，审核留言并给予回复。</p>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load(true)">
        <el-input
          v-model="filters.userId"
          aria-label="作者 ID"
          placeholder="作者 ID"
          pattern="[1-9][0-9]{0,18}"
          clearable
        />
        <el-select v-model="filters.status" aria-label="审核状态" placeholder="全部状态" clearable>
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="已删除" :value="3" />
        </el-select>
        <el-select
          v-model="filters.isSecret"
          aria-label="私密状态"
          placeholder="全部可见性"
          clearable
        >
          <el-option label="公开留言" :value="0" />
          <el-option label="悄悄话" :value="1" />
        </el-select>
        <el-date-picker
          v-model="filters.begin"
          type="datetime"
          placeholder="开始时间（含）"
          aria-label="开始时间"
          value-format="YYYY-MM-DDTHH:mm:ss.SSSZ"
        />
        <el-date-picker
          v-model="filters.end"
          type="datetime"
          placeholder="结束时间（不含）"
          aria-label="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss.SSSZ"
        />
        <el-button native-type="submit" type="primary">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <el-card shadow="never">
      <div class="card-heading">
        <h2>留言列表</h2>
        <span class="muted-text">悄悄话仅在授权范围内可见</span>
      </div>
      <el-table
        v-if="!error"
        v-loading="loading || busy"
        :data="rows"
        row-key="messageId"
        empty-text="暂无留言"
      >
        <el-table-column label="留言" min-width="300">
          <template #default="{ row }">
            <button class="table-title" @click="view(row)">{{ row.content }}</button>
            <p class="table-secondary">
              {{ row.author?.nickname || '已删除用户' }} ·
              {{ row.isSecret ? '悄悄话' : '公开留言' }}
              <span v-if="row.parentId">· 回复 {{ row.parentId }}</span>
            </p>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" kind="review" /></template>
        </el-table-column>
        <el-table-column label="发表时间" width="175">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button link @click="view(row)">详情</el-button>
            <template v-if="row.status !== 3">
              <el-button
                v-if="can('blog:message:review', true) && row.status !== 1"
                link
                @click="review(row, 1)"
              >
                通过
              </el-button>
              <el-button
                v-if="can('blog:message:review', true) && row.status !== 2"
                link
                @click="review(row, 2)"
              >
                驳回
              </el-button>
              <el-button v-if="can('blog:message:review', true)" link @click="view(row, true)">
                回复
              </el-button>
              <el-button v-if="can('blog:message:moderate', true)" link @click="remove(row)">
                删除
              </el-button>
            </template>
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
    <el-dialog v-model="detailVisible" title="留言详情" width="720px" destroy-on-close>
      <template v-if="detail">
        <p class="muted-text">
          {{ detail.author?.nickname || '已删除用户' }} · {{ formatTime(detail.createTime) }} ·
          {{ detail.isSecret ? '悄悄话' : '公开' }}
        </p>
        <p>留言 ID：{{ detail.messageId }} · 回复目标：{{ detail.parentId || '根留言' }}</p>
        <StatusTag :value="detail.status" kind="review" />
        <SafeHtml :html="detail.htmlContent" />
      </template>
    </el-dialog>
    <el-dialog
      v-model="replyVisible"
      title="回复留言"
      width="620px"
      :close-on-click-modal="false"
      :show-close="!busy"
      :close-on-press-escape="!busy"
    >
      <template v-if="detail">
        <blockquote class="reply-target">{{ detail.content }}</blockquote>
        <form class="edit-form" @submit.prevent="sendReply">
          <label>
            回复内容
            <el-input v-model="reply.content" type="textarea" :rows="6" required maxlength="2000" />
          </label>
          <label>
            内容格式
            <el-radio-group v-model="reply.contentFormat">
              <el-radio :value="0">纯文本</el-radio>
              <el-radio :value="1">Markdown</el-radio>
            </el-radio-group>
          </label>
          <label>
            邮件通知
            <el-switch v-model="reply.notifyReply" :active-value="1" :inactive-value="0" />
          </label>
          <p v-if="replyError" role="alert" class="error-text">{{ replyError }}</p>
          <div class="dialog-actions">
            <el-button :disabled="busy" @click="replyVisible = false">取消</el-button>
            <el-button type="primary" native-type="submit" :loading="busy">发送回复</el-button>
          </div>
        </form>
      </template>
    </el-dialog>
  </section>
</template>
