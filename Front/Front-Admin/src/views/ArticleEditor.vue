<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOptions, isId, request } from '../api.js'
import { can } from '../session.js'
import { nullable } from '../format.js'
import RequestError from '../components/RequestError.vue'
import SafeHtml from '../components/SafeHtml.vue'

const route = useRoute()
const router = useRouter()
const id = ref(route.params.id || '')
const revision = ref('')
const status = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const categories = ref([])
const tags = ref([])
const preview = ref('')
const previewVisible = ref(false)
const fileInput = ref(null)
const form = reactive({
  title: '',
  slug: '',
  summary: '',
  categoryId: null,
  coverUrl: '',
  articleType: 1,
  originalUrl: '',
  visibility: 1,
  isTop: 0,
  allowComment: 1,
  markdownContent: '',
  tagIds: [],
})
const saved = ref(JSON.stringify(form))
const dirty = computed(() => JSON.stringify(form) !== saved.value)
const markdownBytes = computed(() => new TextEncoder().encode(form.markdownContent).length)
const editable = computed(() =>
  !id.value
    ? can('blog:article:create')
    : can('blog:article:update') && (status.value !== 1 || can('blog:article:publish')),
)

function applyDetail(result) {
  const data = result.data
  id.value = data.articleId
  revision.value = result.revision
  status.value = data.status
  Object.assign(form, {
    title: data.title,
    slug: data.slug,
    summary: data.summary,
    categoryId: data.categoryId,
    coverUrl: data.coverUrl || '',
    articleType: data.articleType,
    originalUrl: data.originalUrl || '',
    visibility: data.visibility,
    isTop: data.isTop,
    allowComment: data.allowComment,
    markdownContent: data.markdownContent,
    tagIds: [...data.tagIds],
  })
  preview.value = data.htmlContent
  saved.value = JSON.stringify(form)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 1. 已有文章先取完整详情与版本；字典选择不覆盖文章原关联。
    if (id.value) {
      if (!isId(id.value)) {
        throw new Error('文章 ID 格式不正确，请从文章列表重新进入。')
      }
      applyDetail(await request(`/admin/articles/${id.value}`))
    }
    if (can('blog:category:query')) {
      categories.value = await getOptions('/admin/categories')
    }
    if (can('blog:tag:query')) {
      tags.value = await getOptions('/admin/tags')
    }
  } catch (failure) {
    error.value = failure.message
  } finally {
    loading.value = false
  }
}

async function save(publish = false) {
  if (saving.value || !editable.value) {
    return
  }
  formError.value = ''
  if (markdownBytes.value > 1048576) {
    formError.value = 'Markdown 正文不能超过 1 MiB。'
    return
  }
  if (form.articleType !== 1 && !form.originalUrl.trim()) {
    formError.value = '转载或翻译文章必须填写原文地址。'
    return
  }
  saving.value = true
  try {
    // 2. 仅提交允许编辑的字段，正文 HTML、作者及发布时间由后端处理。
    const body = {
      title: form.title,
      slug: form.slug,
      summary: form.summary,
      categoryId: form.categoryId,
      coverUrl: nullable(form.coverUrl),
      articleType: form.articleType,
      originalUrl: nullable(form.originalUrl),
      visibility: form.visibility,
      isTop: form.isTop,
      allowComment: form.allowComment,
      markdownContent: form.markdownContent,
      tagIds: [...form.tagIds],
    }
    const result = await request(id.value ? `/admin/articles/${id.value}` : '/admin/articles', {
      method: id.value ? 'PUT' : 'POST',
      body,
      revision: id.value ? revision.value : undefined,
    })
    applyDetail(result)
    await router.replace({ name: 'article-editor', params: { id: id.value } })
    // 3. 发布是独立请求；发布失败仍保留已保存的草稿。
    if (publish) {
      const published = await request(`/admin/articles/${id.value}/status`, {
        method: 'PUT',
        body: { status: 1 },
        revision: revision.value,
      })
      applyDetail(published)
    }
    ElMessage.success(publish ? '文章已保存并发布' : '文章已保存')
  } catch (failure) {
    formError.value = failure.message
  } finally {
    saving.value = false
  }
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) {
    return
  }
  if (!id.value) {
    ElMessage.warning('请先保存草稿，再上传附件。')
    return
  }
  if (!['image/png', 'image/jpeg'].includes(file.type) || file.size === 0 || file.size > 10485760) {
    ElMessage.error('请选择不超过 10 MiB 的 PNG/JPEG 图片。')
    return
  }
  saving.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    const { data } = await request('/admin/files', { method: 'POST', body })
    const attachment = `/api/v1/blog/articles/${id.value}/files/${data.fileId}/content`
    form.markdownContent += `\n\n![图片](${attachment})\n`
    ElMessage.success('图片已插入正文，请保存文章')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    saving.value = false
  }
}

async function discard() {
  if (!dirty.value) {
    return true
  }
  try {
    await ElMessageBox.confirm('当前修改尚未保存，确认离开？', '未保存的修改', {
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
    })
    return true
  } catch {
    return false
  }
}

function beforeUnload(event) {
  if (dirty.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(discard)
onMounted(() => {
  load()
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section v-loading="loading">
    <div class="page-header">
      <div>
        <p class="eyebrow">CONTENT / EDITOR</p>
        <h1>{{ id ? '编辑文章' : '写一篇新文章' }}</h1>
        <p class="muted-text">{{ dirty ? '有未保存的修改' : '内容已同步' }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="router.back()">返回列表</el-button>
        <el-button v-if="id" @click="previewVisible = true">已保存内容预览</el-button>
      </div>
    </div>
    <RequestError :message="error" @retry="load" />
    <el-alert
      v-if="!editable && !error"
      title="当前账号缺少编辑权限；编辑已发布文章还需发布权限。"
      type="warning"
      :closable="false"
    />
    <form v-if="!error" class="editor-layout" @submit.prevent="save(false)">
      <el-card shadow="never">
        <div class="edit-form">
          <label>
            文章标题
            <el-input
              v-model="form.title"
              size="large"
              required
              maxlength="200"
              :disabled="!editable"
              placeholder="给你的想法起个标题"
            />
          </label>
          <label>
            URL 标识
            <el-input
              v-model="form.slug"
              required
              maxlength="200"
              pattern="[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*"
              :disabled="!editable"
              placeholder="例如 a-new-beginning"
            />
          </label>
          <div class="editor-toolbar">
            <strong>Markdown 正文</strong>
            <el-button
              v-if="can('blog:file:upload')"
              size="small"
              :disabled="!id || saving || !editable"
              @click="fileInput.click()"
            >
              插入图片
            </el-button>
            <input
              ref="fileInput"
              type="file"
              accept="image/png,image/jpeg"
              hidden
              @change="upload"
            />
          </div>
          <el-input
            v-model="form.markdownContent"
            class="markdown-input"
            type="textarea"
            :rows="24"
            :disabled="!editable"
            aria-label="Markdown 正文"
            placeholder="从这里开始写作…"
          />
          <p class="muted-text">
            {{ markdownBytes.toLocaleString() }} / 1,048,576 字节 · 先保存草稿即可上传图片
          </p>
        </div>
      </el-card>
      <div class="editor-settings">
        <el-card shadow="never">
          <template #header><h2>文章设置</h2></template>
          <div class="edit-form">
            <label>
              摘要
              <el-input
                v-model="form.summary"
                type="textarea"
                :rows="4"
                maxlength="500"
                :disabled="!editable"
              />
            </label>
            <label>
              分类
              <el-select
                v-model="form.categoryId"
                :disabled="!editable || !can('blog:category:query')"
                clearable
                filterable
                placeholder="未分类"
                @clear="form.categoryId = null"
              >
                <el-option
                  v-for="item in categories"
                  :key="item.categoryId"
                  :value="item.categoryId"
                  :label="item.categoryName"
                />
              </el-select>
            </label>
            <label>
              标签
              <el-select
                v-model="form.tagIds"
                multiple
                :multiple-limit="20"
                filterable
                :disabled="!editable || !can('blog:tag:query')"
              >
                <el-option
                  v-for="item in tags"
                  :key="item.tagId"
                  :value="item.tagId"
                  :label="item.tagName"
                />
              </el-select>
            </label>
            <label>
              封面地址
              <el-input
                v-model="form.coverUrl"
                maxlength="2048"
                :disabled="!editable"
                placeholder="外部 URL 或当前文章附件路径"
              />
            </label>
            <label>
              文章类型
              <el-select v-model="form.articleType" :disabled="!editable">
                <el-option label="原创" :value="1" />
                <el-option label="转载" :value="2" />
                <el-option label="翻译" :value="3" />
              </el-select>
            </label>
            <label v-if="form.articleType !== 1">
              原文地址
              <el-input
                v-model="form.originalUrl"
                type="url"
                required
                maxlength="2048"
                :disabled="!editable"
              />
            </label>
            <label>
              可见性
              <el-radio-group v-model="form.visibility" :disabled="!editable">
                <el-radio :value="1">公开</el-radio>
                <el-radio :value="2">私密</el-radio>
              </el-radio-group>
            </label>
            <div class="switch-row">
              <span>置顶文章</span>
              <el-switch
                v-model="form.isTop"
                :active-value="1"
                :inactive-value="0"
                :disabled="!editable"
              />
            </div>
            <div class="switch-row">
              <span>允许评论</span>
              <el-switch
                v-model="form.allowComment"
                :active-value="1"
                :inactive-value="0"
                :disabled="!editable"
              />
            </div>
            <p v-if="formError" role="alert" class="error-text">{{ formError }}</p>
            <el-button type="primary" native-type="submit" :disabled="!editable" :loading="saving">
              保存文章
            </el-button>
            <el-button
              v-if="can('blog:article:publish') && editable"
              :disabled="saving"
              @click="save(true)"
            >
              保存并发布
            </el-button>
          </div>
        </el-card>
      </div>
    </form>
    <el-dialog v-model="previewVisible" title="已保存内容预览" width="850px" destroy-on-close>
      <p v-if="dirty" class="muted-text">当前修改尚未保存，以下为上次保存时的正文。</p>
      <SafeHtml :html="preview" />
    </el-dialog>
  </section>
</template>
