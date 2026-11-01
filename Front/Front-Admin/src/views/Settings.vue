<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { request } from '../api.js'
import { can } from '../session.js'
import { nullable } from '../format.js'
import RequestError from '../components/RequestError.vue'

const key = ref('site')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const revision = ref('')
const form = reactive({
  title: '',
  description: '',
  icp: '',
  publicSecurityRecord: '',
  startDate: null,
  enabled: true,
  reviewRequired: true,
  remark: '',
  isPublic: 1,
})

function selectTab(value) {
  key.value = value
  load()
}

async function load() {
  loading.value = true
  error.value = ''
  formError.value = ''
  try {
    const result = await request(`/admin/configs/${key.value}`)
    revision.value = result.revision
    Object.assign(form, result.data.configValue, {
      remark: result.data.description || '',
      isPublic: result.data.isPublic,
    })
  } catch (failure) {
    error.value = failure.message
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  formError.value = ''
  try {
    const configValue =
      key.value === 'site'
        ? {
            title: form.title,
            description: form.description,
            icp: form.icp,
            publicSecurityRecord: form.publicSecurityRecord,
            startDate: form.startDate || null,
          }
        : { enabled: form.enabled, reviewRequired: form.reviewRequired }
    const result = await request(`/admin/configs/${key.value}`, {
      method: 'PUT',
      revision: revision.value,
      body: {
        configValue,
        description: nullable(form.remark),
        isPublic: key.value === 'site' ? form.isPublic : 0,
      },
    })
    revision.value = result.revision
    ElMessage.success('配置已保存')
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<template>
  <section v-loading="loading">
    <div class="page-header">
      <div>
        <p class="eyebrow">SYSTEM / CONFIGURATION</p>
        <h1>站点配置</h1>
        <p class="muted-text">维护站点信息、互动开关与审核规则。</p>
      </div>
    </div>
    <div class="page-tabs">
      <button
        v-for="tab in [
          { key: 'site', label: '站点信息' },
          { key: 'comment', label: '评论设置' },
          { key: 'message', label: '留言设置' },
        ]"
        :key="tab.key"
        :class="{ active: key === tab.key }"
        :disabled="saving || loading"
        @click="selectTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>
    <RequestError :message="error" @retry="load" />
    <el-card v-if="!error" shadow="never">
      <form class="edit-form" @submit.prevent="save">
        <template v-if="key === 'site'">
          <label>
            站点标题
            <el-input v-model="form.title" required maxlength="100" />
          </label>
          <label>
            站点描述
            <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" />
          </label>
          <div class="form-row">
            <label>
              ICP备案
              <el-input v-model="form.icp" maxlength="100" />
            </label>
            <label>
              公安备案
              <el-input v-model="form.publicSecurityRecord" maxlength="100" />
            </label>
          </div>
          <label>
            建站日期
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
          </label>
          <div class="switch-row">
            <span>允许公开读取站点信息</span>
            <el-switch v-model="form.isPublic" :active-value="1" :inactive-value="0" />
          </div>
        </template>
        <template v-else>
          <div class="switch-row">
            <div>
              <strong>开启{{ key === 'comment' ? '评论' : '留言' }}</strong>
              <p class="muted-text">关闭后限制新增和编辑，历史查询与管理仍保留。</p>
            </div>
            <el-switch v-model="form.enabled" />
          </div>
          <div class="switch-row">
            <div>
              <strong>新内容需要审核</strong>
              <p class="muted-text">开启后先进入待审核状态。</p>
            </div>
            <el-switch v-model="form.reviewRequired" />
          </div>
        </template>
        <label>
          后台说明
          <el-input v-model="form.remark" type="textarea" maxlength="255" />
        </label>
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div>
          <el-button
            v-if="can('blog:config:update')"
            type="primary"
            native-type="submit"
            :loading="saving"
          >
            保存配置
          </el-button>
        </div>
      </form>
    </el-card>
  </section>
</template>
