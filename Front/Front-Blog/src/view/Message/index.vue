<script setup>
import { inject, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { request } from '@/api.js'
import { session, can } from '@/session.js'
import { useDiscussionForm } from '@/composables/useDiscussionForm.js'
import SidebarCard from '@/components/SidebarCard.vue'
import CaptchaField from '../../../../shared/CaptchaField.vue'
import DiscussionList from '@/components/DiscussionList.vue'
import EmojiPicker from '@shared/EmojiPicker.vue'
const route = useRoute()
const { articleTitle, articleMeta } = inject('setHero')
articleTitle.value = '留言板'
articleMeta.value = '说点什么吧'
const captcha = ref(null)
const msgTextareaRef = ref(null)
const { form, errors, contentLength, validateField, validateForm, insertEmoji: insertMsgEmoji, resetForm: clearForm } = useDiscussionForm(msgTextareaRef)
const replyTarget = ref(null)
const editTarget = ref(null)
const submitting = ref(false)
const messageTotal = ref('0')
const refresh = ref(0)
function resetForm() {
  clearForm()
  replyTarget.value = null
  editTarget.value = null
}
function startReply(item) {
  editTarget.value = null
  replyTarget.value = item
  form.value.isSecret = item.isSecret === 1
  msgTextareaRef.value?.focus()
}
async function startEdit(item) {
  submitting.value = true
  try {
    const result = await request('/blog/me/messages/' + item.id)
    editTarget.value = { id: item.id, revision: result.revision }
    replyTarget.value = null
    form.value.content = result.data.content
    form.value.isMarkdown = result.data.contentFormat === 1
    form.value.isNotice = result.data.notifyReply === 1
    form.value.isSecret = result.data.isSecret === 1
    msgTextareaRef.value?.focus()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
async function handleSubmit() {
  if (submitting.value || !validateForm()) {
    return
  }
  submitting.value = true
  try {
    const body = { content: form.value.content.trim(), contentFormat: form.value.isMarkdown ? 1 : 0, notifyReply: form.value.isNotice ? 1 : 0 }
    let result
    if (editTarget.value) {
      result = await request('/blog/me/messages/' + editTarget.value.id, {
        method: 'PUT', revision: editTarget.value.revision, body, captcha: captcha.value.getValue(),
      })
    } else {
      body.parentId = replyTarget.value?.id || null
      body.isSecret = form.value.isSecret || replyTarget.value?.isSecret === 1 ? 1 : 0
      result = await request('/blog/messages', { method: 'POST', body, captcha: captcha.value.getValue() })
    }
    resetForm()
    refresh.value += 1
    ElMessage.success(result.data.status === 0 ? '留言已提交，正在等待审核' : '留言已发表')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    captcha.value?.refresh()
    submitting.value = false
  }
}
</script>

<template>
  <div class="message-page">
    <div class="message-layout">
      <div class="message-inner">
        <!-- 留言表单 -->
        <p v-if="!session.user">
          <router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">登录后留言 →</router-link>
        </p>
        <p v-else-if="!can('blog:message:create') && !editTarget">当前账号没有留言权限。</p>
        <fieldset v-else class="msg-form form-card" :disabled="submitting">
          <h3 class="form-title">
            <i class="iconfont icon-liuyan" />
            {{ editTarget ? '修改留言' : '写留言' }}
          </h3>
          <div v-if="replyTarget" class="reply-tip">
            回复
            <strong>{{ replyTarget.nickname }}</strong>
            <span class="cancel" @click="resetForm">&times;</span>
          </div>
          <div v-if="editTarget" class="reply-tip">
            修改留言
            <span class="cancel" @click="resetForm">&times;</span>
          </div>
          <div class="textarea-wrap">
            <textarea
              ref="msgTextareaRef"
              v-model="form.content"
              aria-label="留言正文"
              :class="[
                'form-textarea',
                { 'form-textarea-error': errors.content }
              ]"
              placeholder="写点什么..."
              rows="4"
              @blur="validateField('content')"
            />
            <p v-if="errors.content" class="error-text">{{ errors.content }}</p>
            <div class="char-counter-line" :class="{ 'over-limit': contentLength > 2000 }">
              {{ contentLength }}
              /2000
            </div>
          </div>
          <CaptchaField ref="captcha" :request="request" endpoint="/blog/auth/captcha" :disabled="submitting" />
          <div class="form-options">
            <label class="option-check">
              <input
                type="checkbox"
                v-model="form.isSecret"
                :disabled="!!editTarget || replyTarget?.isSecret === 1"
              />
              悄悄话
            </label>
            <label class="option-check">
              <input type="checkbox" v-model="form.isNotice" />
              邮件提醒
            </label>
            <label class="option-check">
              <input type="checkbox" v-model="form.isMarkdown" />
              Markdown
            </label>
            <div class="form-actions">
              <EmojiPicker @select="insertMsgEmoji" />
              <button class="btn-submit" :disabled="submitting || !captcha?.ready" @click="handleSubmit">
                {{ editTarget ? '修改' : '留言' }}
              </button>
            </div>
          </div>
        </fieldset>
        <!-- 留言数 -->
        <div class="msg-count">
          <span>共 {{ messageTotal }} 条主留言</span>
        </div>
        <!-- 留言列表 -->
        <DiscussionList
          :key="refresh"
          kind="messages"
          :allow-reply="!submitting"
          @reply="startReply"
          @edit="startEdit"
          @total="messageTotal = $event"
        />
      </div>
      <SidebarCard />
    </div>
  </div>
</template>

<style scoped>
.message-page {
  width: 100%;
}
.message-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.message-inner {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 表单卡片 */
.form-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
  padding: 20px 24px;
}
.form-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}
.form-title .iconfont {
  font-size: 17px;
}
.reply-tip {
  font-size: 13px;
  color: #606266;
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.cancel {
  cursor: pointer;
  font-size: 16px;
  margin-left: auto;
  color: #909399;
}
.form-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: flex-start;
}
.form-field {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.input-with-icon {
  position: relative;
  display: flex;
  align-items: center;
}
.input-icon {
  position: absolute;
  left: 10px;
  font-size: 14px;
  color: #c0c4cc;
  pointer-events: none;
  z-index: 1;
}
.input-with-icon .form-input {
  padding-left: 32px;
  width: 100%;
}
.captcha-wrap {
  min-width: 0;
}
.captcha-refresh {
  position: absolute;
  right: 8px;
  font-size: 16px;
  color: #909399;
  cursor: pointer;
  user-select: none;
  transition: color 0.15s;
}
.captcha-refresh:hover {
  color: #303133;
}
.captcha-tip {
  position: absolute;
  top: -28px;
  left: 32px;
  font-size: 12px;
  color: #303133;
  background: #fff;
  padding: 2px 10px;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  border: 1px solid #ebeef5;
  white-space: nowrap;
  z-index: 10;
  pointer-events: none;
  font-weight: 600;
}
.form-input {
  flex: 1;
  padding: 9px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  background: #fff;
  font-family: inherit;
  box-sizing: border-box;
}
.form-input:focus {
  border-color: #303133;
}
.textarea-wrap {
  margin-bottom: 8px;
}
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  font-size: 13px;
  outline: none;
  resize: vertical;
  font-family: inherit;
  background: #fff;
  box-sizing: border-box;
}
.form-textarea:focus {
  border-color: #303133;
}
.form-textarea-error,
.form-textarea-error:focus {
  border-color: #f56c6c;
}
.error-text {
  color: #f56c6c;
  font-size: 12px;
  margin: 4px 0 0;
  line-height: 1.4;
}
.char-counter-line {
  text-align: right;
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}
.char-counter-line.over-limit {
  color: #f56c6c;
}
.form-input-error,
.form-input-error:focus {
  border-color: #f56c6c;
}
.form-options {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.option-check {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
}
.option-check input[type='checkbox'] {
  accent-color: #303133;
}
.btn-submit {
  padding: 8px 24px;
  font-size: 13px;
  border: none;
  background: #303133;
  color: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
  margin-left: auto;
  transition: background 0.15s;
}
.btn-submit:hover {
  background: #000;
}
.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 留言数 */
.msg-count {
  font-size: 13px;
  color: #909399;
  padding: 0 4px;
}

/* 留言卡片 */
.msg-item-card {
  display: flex;
  gap: 12px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
  padding: 18px 22px;
  margin-bottom: 12px;
}

/* 头像 */
.msg-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
}
.msg-avatar-sm {
  width: 32px;
  height: 32px;
}
.msg-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #ebeef5;
}
.msg-avatar-letter {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #e4e7ed;
  color: #606266;
  font-size: 16px;
  font-weight: 700;
  font-family: var(--blog-serif);
  user-select: none;
}
.msg-avatar-sm .msg-avatar-letter {
  font-size: 13px;
}

.msg-main {
  flex: 1;
  min-width: 0;
}
.msg-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.msg-nick {
  font-size: 14px;
  font-weight: 700;
  color: #303133;
}
.badge-admin {
  font-size: 10px;
  padding: 1px 6px;
  background: #303133;
  color: #fff;
  border-radius: 3px;
  font-weight: 600;
}
.reply-to {
  font-size: 12px;
  color: #909399;
}
.msg-pending {
  font-size: 10px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
}
.reply-icon {
  display: inline-block;
  transform: scaleX(-1);
  font-size: 12px;
}

/* 元信息 — 与昵称同行 */
.msg-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #b0b4bb;
}
.msg-meta-icon {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}
.msg-date {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
  flex-shrink: 0;
}

.msg-body {
  font-size: 14px;
  color: #444;
  line-height: 1.7;
}
.msg-body :deep(p) {
  margin: 4px 0;
}
.msg-body :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.5;
}
.msg-body :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
  font-size: inherit;
}
.msg-body :deep(code) {
  background: #f5f7fa;
  padding: 2px 5px;
  border-radius: 3px;
  font-size: 13px;
  color: #476582;
}
.msg-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid #dcdfe6;
  background: #f5f7fa;
  color: #606266;
}
.msg-body :deep(ul),
.msg-body :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}
.msg-body :deep(a) {
  color: #409eff;
  text-decoration: none;
}
.msg-body :deep(a:hover) {
  text-decoration: underline;
}
.msg-body :deep(h1),
.msg-body :deep(h2),
.msg-body :deep(h3),
.msg-body :deep(h4) {
  margin: 8px 0 4px;
  font-weight: 600;
  color: #303133;
}
.msg-body :deep(h1) {
  font-size: 18px;
}
.msg-body :deep(h2) {
  font-size: 16px;
}
.msg-body :deep(h3) {
  font-size: 15px;
}
.msg-body :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}

/* 底部：hover操作在右下角 */
.msg-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
.msg-actions {
  display: flex;
  gap: 12px;
  opacity: 0;
  transition: opacity 0.15s;
}
.msg-item-card:hover .msg-actions,
.msg-child:hover > .msg-main > .msg-footer > .msg-actions {
  opacity: 1;
}
.act {
  font-size: 12px;
  color: #909399;
  cursor: pointer;
  transition: color 0.15s;
}
.act:hover {
  color: #303133;
}
.act.del:hover {
  color: #c00;
}

/* 子留言 */
.msg-children {
  margin-top: 8px;
  padding-left: 4px;
  border-left: 2px solid #ebeef5;
}
.msg-child {
  display: flex;
  gap: 10px;
  padding: 10px 0;
}
.msg-child + .msg-child {
  border-top: 1px dashed #ebeef5;
}

.placeholder {
  padding: 20px 0;
}
.sk-line {
  height: 14px;
  background: #ebeef5;
  border-radius: 4px;
  margin-bottom: 12px;
  width: 60%;
}
.empty {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}

@media (max-width: 1220px) {
  .message-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(236px, 280px);
    align-items: start;
  }
}

@media (max-width: 960px) {
  .message-layout {
    flex-direction: column;
    display: flex;
  }
  .message-inner {
    width: 100%;
  }
}
@media (max-width: 600px) {
  .form-card,
  .msg-item-card {
    padding: 14px 16px;
  }
  .form-row {
    flex-direction: column;
    align-items: stretch;
  }
  .msg-meta-item {
    display: none;
  }
  .msg-child {
    gap: 8px;
  }
}
</style>
