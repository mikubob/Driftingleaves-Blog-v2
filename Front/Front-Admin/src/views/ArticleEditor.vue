<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import filterXSS from 'xss'
import 'md-editor-v3/lib/style.css'
import { getOptions, isId, request } from '../api.js'
import { can } from '../session.js'
import { nullable } from '../format.js'
import RequestError from '../components/RequestError.vue'
import SafeHtml from '../components/SafeHtml.vue'
import EmojiPicker from '../components/EmojiPicker.vue'

const route = useRoute()
const router = useRouter()
const id = ref(route.params.id || '')
const revision = ref('')
const status = ref(0)
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const formError = ref('')
const categories = ref([])
const tags = ref([])
const preview = ref('')
const previewVisible = ref(false)
const mdEditor = ref(null)
const initialForm = {
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
}
const form = reactive({ ...initialForm, tagIds: [] })
const saved = ref(JSON.stringify(form))
const dirty = computed(() => JSON.stringify(form) !== saved.value)
const markdownBytes = computed(() => new TextEncoder().encode(form.markdownContent).length)
const editable = computed(() => {
  if (loading.value || error.value) {
    return false
  }
  if (!id.value) {
    return can('blog:article:create')
  }
  return can('blog:article:update') && (status.value !== 1 || can('blog:article:publish'))
})

function updateVersion(result) {
  id.value = result.data.articleId
  revision.value = result.revision
  status.value = result.data.status
  preview.value = result.data.htmlContent
}

function applyDetail(result) {
  const data = result.data
  updateVersion(result)
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
  saved.value = JSON.stringify(form)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 已有文章先取完整详情与版本，失败时禁止保存，避免空白覆盖。
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

/** 保存提交快照；发布成功且没有新输入时返回列表，草稿沿用创建后的 ID。 */
async function save(publish = false, { navigate = true } = {}) {
  if (saving.value || uploading.value || !editable.value) {
    return false
  }
  formError.value = ''
  if (!form.title.trim() || !/^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$/.test(form.slug)) {
    formError.value = '请输入文章标题和有效的 URL 标识（字母、数字及单个连字符）。'
    return false
  }
  if (publish && !form.markdownContent.trim()) {
    formError.value = '发布前请输入文章正文。'
    return false
  }
  if (markdownBytes.value > 1048576) {
    formError.value = 'Markdown 正文不能超过 1 MiB。'
    return false
  }
  if (form.articleType !== 1 && !form.originalUrl.trim()) {
    formError.value = '转载或翻译文章必须填写原文地址。'
    return false
  }

  // 1. 只提交可编辑字段，记录提交快照，不覆盖请求期间的新输入。
  const snapshot = JSON.stringify(form)
  const body = {
    ...JSON.parse(snapshot),
    coverUrl: nullable(form.coverUrl),
    originalUrl: nullable(form.originalUrl),
  }
  saving.value = true
  try {
    const result = await request(id.value ? `/admin/articles/${id.value}` : '/admin/articles', {
      method: id.value ? 'PUT' : 'POST',
      body,
      revision: id.value ? revision.value : undefined,
    })
    updateVersion(result)
    saved.value = snapshot

    // 2. 使用保存返回的版本发布，失败保留草稿及 ID，重试不会重复创建。
    if (publish) {
      const published = await request('/admin/articles/batch-status', {
        method: 'PUT',
        body: { items: [{ id: id.value, revision: revision.value }], status: 1 },
      })
      revision.value = published.data.items[0].revision
      status.value = 1
    }
    ElMessage.success(publish ? '文章已保存并发布' : '文章已保存')
  } catch (failure) {
    formError.value = failure.message
    return false
  } finally {
    saving.value = false
  }

  // 3. 全部写入结束后再导航，避免路由替换卸载仍在发布的编辑组件。
  if (navigate && !dirty.value) {
    if (publish) {
      await router.push({ name: route.meta.articleListRoute })
    } else if (route.params.id !== id.value) {
      await router.replace({ name: 'article-editor', params: { id: id.value } })
    }
  }
  return !dirty.value
}

/** 附件需先保存文章；正文和封面使用当前后端支持的文章附件路径。 */
async function uploadImages(files, callback, cover = false) {
  if (!editable.value || saving.value || uploading.value) {
    return
  }
  if (!id.value) {
    ElMessage.warning('请先保存草稿，再上传图片。')
    return
  }
  for (const file of files) {
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size === 0 || file.size > 10485760) {
      ElMessage.error('请选择不超过 10 MiB 的 PNG/JPEG 图片。')
      return
    }
  }
  uploading.value = true
  try {
    const urls = []
    for (const file of files) {
      const body = new FormData()
      body.append('file', file)
      const { data } = await request('/admin/files', { method: 'POST', body })
      urls.push(`/api/v1/blog/articles/${id.value}/files/${data.fileId}/content`)
    }
    if (cover) {
      form.coverUrl = urls[0]
    } else {
      callback(urls)
    }
    ElMessage.success('图片已插入，请保存文章')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    uploading.value = false
  }
}

function autoSlug() {
  if (!form.slug && form.title.trim()) {
    form.slug = form.title.toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '')
      .slice(0, 50)
      .replace(/-$/, '') || `article-${Date.now()}`
  }
}

function insertEmoji(char) {
  mdEditor.value?.insert(() => ({ targetValue: char }))
}

function onKeydown(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    save(false)
  }
}

async function discard() {
  if (saving.value || uploading.value) {
    ElMessage.warning('请等待保存或上传完成。')
    return false
  }
  if (!dirty.value) {
    return true
  }
  try {
    await ElMessageBox.confirm('你有未保存的内容，是否保存后离开？', '未保存的修改', {
      confirmButtonText: '保存并离开',
      cancelButtonText: '不保存',
      distinguishCancelAndClose: true,
    })
    return await save(false, { navigate: false })
  } catch (action) {
    return action === 'cancel'
  }
}

function beforeUnload(event) {
  if (dirty.value || saving.value || uploading.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(discard)
// 创建草稿后仅同步 URL；切换到另一篇文章时，先处理未保存内容再重置状态。
onBeforeRouteUpdate(async (to) => {
  const nextId = to.params.id || ''
  if (nextId === id.value) {
    return true
  }
  if (!(await discard())) {
    return false
  }
  id.value = nextId
  revision.value = ''
  status.value = 0
  Object.assign(form, initialForm, { tagIds: [] })
  saved.value = JSON.stringify(form)
  await load()
  return true
})
onMounted(() => {
  load()
  window.addEventListener('beforeunload', beforeUnload)
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <section v-loading="loading" class="article-edit">
    <div class="edit-topbar">
      <h1 class="edit-title">{{ id ? '编辑文章' : '新建文章' }}</h1>
      <div class="edit-actions">
        <span class="save-state">{{ dirty ? '有未保存的修改' : '内容已同步' }}</span>
        <el-button size="small" @click="router.push({ name: route.meta.articleListRoute })">返回列表</el-button>
        <el-button v-if="id" size="small" @click="previewVisible = true">已保存内容预览</el-button>
        <el-button
          size="small"
          type="primary"
          :disabled="!editable || uploading"
          :loading="saving"
          @click="save(false)"
        >
          保存文章
        </el-button>
        <el-button
          v-if="can('blog:article:publish')"
          size="small"
          type="success"
          :disabled="!editable || uploading"
          :loading="saving"
          @click="save(true)"
        >
          发布
        </el-button>
      </div>
    </div>
    <RequestError :message="error" @retry="load" />
    <el-alert
      v-if="!editable && !loading && !error"
      title="当前账号缺少编辑权限；编辑已发布文章还需发布权限。"
      type="warning"
      :closable="false"
    />
    <p v-if="formError" role="alert" class="error-text editor-error">{{ formError }}</p>
    <div class="title-row">
      <el-input
        v-model="form.title"
        aria-label="文章标题"
        placeholder="请输入文章标题…"
        class="title-input"
        size="large"
        maxlength="200"
        :disabled="!editable"
        @blur="autoSlug"
      />
    </div>
    <div v-if="!error" class="edit-body">
      <div class="editor-panel">
        <MdEditor
          ref="mdEditor"
          v-model="form.markdownContent"
          preview-theme="github"
          :toolbars-exclude="['mermaid', 'katex', 'github', 'save']"
          :sanitize="filterXSS"
          :disabled="!editable"
          no-mermaid
          no-katex
          no-echarts
          class="md-editor-fill"
          @on-change="form.markdownContent = $event"
          @on-upload-img="uploadImages"
        />
        <div v-if="editable" class="editor-toolbar-emoji">
          <EmojiPicker @select="insertEmoji" />
        </div>
        <p class="editor-hint">
          {{ markdownBytes.toLocaleString() }} / 1,048,576 字节 · 实时预览仅用于编辑，
          以保存后的服务端预览为准 · 先保存草稿再上传图片
        </p>
      </div>
      <aside class="edit-aside">
        <div class="aside-mobile-head">
          <span class="aside-mobile-title">发布设置</span>
        </div>
        <div class="edit-form">
          <label>
            URL 标识
            <el-input
              v-model="form.slug"
              aria-label="URL 标识"
              maxlength="200"
              :disabled="!editable"
              placeholder="例如 a-new-beginning"
            />
          </label>
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
            封面图
            <el-upload
              :show-file-list="false"
              :http-request="({ file }) => uploadImages([file], null, true)"
              :disabled="!editable || saving || uploading"
              accept="image/png,image/jpeg"
              class="cover-uploader"
            >
              <div class="cover-placeholder">{{ uploading ? '上传中…' : '点击上传封面' }}</div>
            </el-upload>
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
        </div>
      </aside>
    </div>
    <el-dialog v-model="previewVisible" title="已保存内容预览" width="850px" destroy-on-close>
      <p v-if="dirty" class="muted-text">当前修改尚未保存，以下为上次保存时的正文。</p>
      <SafeHtml :html="preview" />
    </el-dialog>
  </section>
</template>
<style scoped>
.article-edit {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #f0f2f5;
}

/* 顶部操作与标题沿用旧后台布局。 */
.edit-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  gap: 10px;
  padding: 8px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.edit-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.edit-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.save-state,
.editor-hint {
  color: #909399;
  font-size: 12px;
}

.editor-hint {
  padding: 6px 12px;
  background: #fff;
}

.editor-error {
  padding: 8px 16px;
  background: #fff;
}

.title-row {
  flex-shrink: 0;
  padding: 10px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.title-input :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 600;
}

/* 编辑器填满剩余空间，设置侧栏独立滚动。 */
.edit-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.editor-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.md-editor-fill {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.editor-panel :deep(.md-editor) {
  border: none;
  border-radius: 0;
}

.editor-panel :deep(.md-editor-toolbar-wrapper) {
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
}

/* 代码块头部不能遮挡离页确认弹窗。 */
.editor-panel :deep(.md-editor-code-head) {
  z-index: 1 !important;
}

.editor-toolbar-emoji {
  position: absolute;
  right: 8px;
  bottom: 78px;
  z-index: 20;
}

.editor-toolbar-emoji :deep(.emoji-panel) {
  right: 0;
  left: auto;
  transform: none;
}

.edit-aside {
  width: 230px;
  flex-shrink: 0;
  overflow-y: auto;
  padding: 14px 12px;
  border-left: 1px solid #e4e7ed;
  background: #fff;
}

.edit-aside .edit-form {
  gap: 16px;
}

.aside-mobile-head {
  display: none;
}

.cover-uploader,
.cover-uploader :deep(.el-upload) {
  width: 100%;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 78px;
  border: 1px dashed #d3d6db;
  border-radius: 6px;
  color: #909399;
  cursor: pointer;
}

.cover-placeholder:hover {
  border-color: #303133;
  color: #303133;
}

@media (max-width: 1024px) {
  .edit-body {
    flex-direction: column;
    overflow-y: auto;
  }

  .editor-panel {
    flex: 0 0 auto;
    height: 62vh;
    min-height: 460px;
  }

  .edit-aside {
    width: 100%;
    overflow: visible;
    border-top: 1px solid #e4e7ed;
    border-left: none;
  }
}

@media (max-width: 768px) {
  .edit-topbar {
    flex-wrap: wrap;
    padding: 8px 10px;
  }

  .edit-actions {
    width: 100%;
  }

  .edit-actions .el-button {
    margin-left: 0;
  }

  .save-state {
    display: none;
  }

  .title-row {
    padding: 10px 12px;
  }

  .edit-body {
    padding: 10px;
  }

  .editor-panel {
    height: 68vh;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
  }

  .editor-panel :deep(.md-editor-toolbar) {
    min-width: max-content;
  }

  .editor-panel :deep(.md-editor-toolbar-wrapper) {
    overflow-x: auto;
  }

  .edit-aside {
    margin-top: 12px;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
  }

  .aside-mobile-head {
    display: block;
    margin-bottom: 14px;
    font-weight: 600;
  }
}
</style>
