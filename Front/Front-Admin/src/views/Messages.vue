<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/radio/style/css'
import 'element-plus/es/components/radio-group/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/switch/style/css'
import { request } from '../api.js'
import { can } from '../session.js'
import RequestError from '../components/RequestError.vue'
import { useList } from '../composables/useList.js'
import DiscussionTable from '../components/DiscussionTable.vue'
import DiscussionDetail from '../components/DiscussionDetail.vue'

const initialFilters = { status: '', userId: '', isSecret: '', begin: '', end: '' }
const list = useList('/admin/messages', initialFilters)
const { error, load } = list
const filters = reactive({ ...initialFilters })
const table = ref(null)

function search() {
  Object.assign(list.filters, filters)
  return load(true)
}

function reset() {
  Object.assign(filters, initialFilters)
  return list.reset()
}

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
    await request('/admin/messages/batch-review', {
      method: 'PUT',
      body: { items: [{ id: row.messageId, revision: current.revision }], status },
    })
    ElMessage.success('审核状态已更新')
    await load()
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
    await load()
  } catch (failure) {
    replyError.value = failure.message
  } finally {
    busy.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('删除这条留言及其全部子回复？删除后无法恢复。', '删除留言', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    await request('/admin/messages/batch-delete', { method: 'POST', body: { ids: [row.messageId] } })
    ElMessage.success('留言已删除')
    await load()
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    busy.value = false
  }
}
onMounted(load)

const batchActions = computed(() => {
  const actions = []
  if (can('blog:message:review', true)) {
    actions.push({
      label: '批量通过',
      type: 'success',
      path: 'batch-review',
      method: 'PUT',
      body: { status: 1 },
    }, {
      label: '批量驳回',
      type: 'warning',
      path: 'batch-review',
      method: 'PUT',
      body: { status: 2 },
    })
  }
  if (can('blog:message:moderate', true)) {
    actions.push({
      label: '批量删除',
      path: 'batch-delete',
      revision: false,
      danger: true,
      warning: '所选留言及其全部层级回复（包括悄悄话）都会删除，无法恢复。',
    })
  }
  return actions
})
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>留言管理</h1>
      </div>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" aria-label="筛选留言" @submit.prevent="search">
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
        <el-button native-type="submit" type="primary" :disabled="table?.disabled">查询</el-button>
        <el-button :disabled="table?.disabled" @click="reset">重置</el-button>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load()" />
    <DiscussionTable ref="table" kind="message" :list="list" :actions="batchActions" :busy="busy">
      <template #actions="{ row, disabled }">
        <el-button link :disabled="disabled" @click="view(row)">查看</el-button>
        <el-button
          v-if="can('blog:message:review', true) && row.status !== 1"
          link type="success" :disabled="disabled" @click="review(row, 1)"
        >通过</el-button>
        <el-button
          v-if="can('blog:message:review', true) && row.status !== 2"
          link type="warning" :disabled="disabled" @click="review(row, 2)"
        >驳回</el-button>
        <el-button
          v-if="can('blog:message:review', true)" link :disabled="disabled" @click="view(row, true)"
        >回复</el-button>
        <el-button
          v-if="can('blog:message:moderate', true)" link type="danger" :disabled="disabled" @click="remove(row)"
        >删除</el-button>
      </template>
    </DiscussionTable>
    <el-dialog v-model="detailVisible" title="留言详情" width="580px" destroy-on-close :close-on-click-modal="false">
      <DiscussionDetail v-if="detail" kind="message" :detail="detail" />
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
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
