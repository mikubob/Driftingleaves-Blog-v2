<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getOptions, request } from '../api.js'
import { can } from '../session.js'
import { nullable } from '../format.js'
import RequestError from '../components/RequestError.vue'

const form = reactive({
  userId: '',
  nickname: '',
  tagline: '',
  description: '',
  avatar: '',
  website: '',
  publicEmail: '',
  githubUrl: '',
  location: '',
})
const users = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const revision = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await request('/admin/home/profile')
    revision.value = result.revision
    if (result.data) {
      const data = result.data
      Object.assign(form, {
        userId: data.userId,
        nickname: data.nickname,
        tagline: data.tagline,
        description: data.description || '',
        avatar: data.avatar || '',
        website: data.website || '',
        publicEmail: data.publicEmail || '',
        githubUrl: data.githubUrl || '',
        location: data.location || '',
      })
    }
    if (can('sys:user:query')) {
      users.value = await getOptions('/admin/users', { status: 1 })
    }
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
    const result = await request('/admin/home/profile', {
      method: 'PUT',
      revision: revision.value,
      body: {
        userId: form.userId,
        nickname: form.nickname,
        tagline: form.tagline,
        description: nullable(form.description),
        avatar: nullable(form.avatar),
        website: nullable(form.website),
        publicEmail: nullable(form.publicEmail),
        githubUrl: nullable(form.githubUrl),
        location: nullable(form.location),
      },
    })
    revision.value = result.revision
    ElMessage.success('主页资料已保存')
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
        <p class="eyebrow">HOME / PROFILE</p>
        <h1>主页资料</h1>
        <p class="muted-text">维护主页与简历使用的公开身份和联系信息。</p>
      </div>
    </div>
    <RequestError :message="error" @retry="load" />
    <el-card v-if="!error" shadow="never" class="profile-card">
      <form class="edit-form" @submit.prevent="save">
        <div class="form-section-heading">
          <h2>公开展示</h2>
          <p class="muted-text">此处的信息会公开显示，请主动选择需要展示的内容。</p>
        </div>
        <label v-if="can('sys:user:query')">
          站点主人账号
          <el-select v-model="form.userId" filterable placeholder="请选择启用的本站账号">
            <el-option
              v-for="user in users"
              :key="user.userId"
              :label="`${user.nickname} · ${user.username}`"
              :value="user.userId"
            />
          </el-select>
        </label>
        <label v-else>
          站点主人用户 ID
          <el-input v-model="form.userId" required pattern="[1-9][0-9]{0,18}" />
        </label>
        <div class="form-row">
          <label>
            公开昵称
            <el-input v-model="form.nickname" required maxlength="64" />
          </label>
          <label>
            一句话介绍
            <el-input v-model="form.tagline" maxlength="100" />
          </label>
        </div>
        <label>
          公开简介
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" />
        </label>
        <label>
          公开头像 URL
          <el-input v-model="form.avatar" type="url" maxlength="2048" />
        </label>
        <div class="form-row">
          <label>
            个人网站
            <el-input v-model="form.website" type="url" maxlength="2048" />
          </label>
          <label>
            GitHub 主页
            <el-input v-model="form.githubUrl" type="url" maxlength="2048" />
          </label>
        </div>
        <div class="form-row">
          <label>
            自愿公开的联系邮箱
            <el-input v-model="form.publicEmail" type="email" maxlength="254" />
          </label>
          <label>
            所在地
            <el-input v-model="form.location" maxlength="100" />
          </label>
        </div>
        <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
        <div>
          <el-button
            v-if="can('home:profile:update', true)"
            type="primary"
            native-type="submit"
            :loading="saving"
          >
            保存公开资料
          </el-button>
        </div>
      </form>
    </el-card>
  </section>
</template>
