<script setup>
import DisplayImageField from '../components/DisplayImageField.vue'
import { onMounted, reactive, ref } from 'vue'
import { ElButton, ElCard, ElInput, ElMessage } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import { request } from '../api.js'
import { can } from '../session.js'
import { nullable } from '../form.js'
import RequestError from '../components/RequestError.vue'

const form = reactive({
  nickname: '',
  tagline: '',
  description: '',
  avatar: '',
  website: '',
  publicEmail: '',
  githubUrl: '',
  location: '',
})
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
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
  } catch (failure) {
    error.value = failure.message
  } finally {
    loading.value = false
  }
}

async function save() {
  if (saving.value || uploading.value) {
    return
  }
  saving.value = true
  try {
    const result = await request('/admin/home/profile', {
      method: 'PUT',
      revision: revision.value,
      body: {
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
    ElMessage.success('站长资料已保存')
  } catch (failure) {
    ElMessage.error(failure.message)
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
        <h1>站长资料</h1>
      </div>
    </div>
    <RequestError :message="error" @retry="load" />
    <el-card v-if="!error" shadow="never">
      <form class="edit-form" @submit.prevent="save">
        <div class="form-section-heading">
          <p class="muted-text">填写的资料将公开显示在首页、博客侧栏和简历页。</p>
        </div>
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
        <DisplayImageField
          usage="AVATAR"
          v-model="form.avatar"
          v-model:uploading="uploading"
          label="公开头像"
          :disabled="saving || !can('site:ownerProfile:update', true)"
        />
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
        <div>
          <el-button
            v-if="can('site:ownerProfile:update', true)"
            type="primary"
            native-type="submit"
            :loading="saving"
            :disabled="uploading"
          >
            保存站长资料
          </el-button>
        </div>
      </form>
    </el-card>
  </section>
</template>
