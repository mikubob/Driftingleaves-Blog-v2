import assert from 'node:assert/strict'
import { test } from 'node:test'
import { articleView, discussionView } from '../src/api/presentation.js'
import { useDiscussionForm } from '../src/composables/useDiscussionForm.js'
import { ref } from 'vue'

test('V2 字段适配原文章卡片，保留字符串 ID、计数与安全正文', () => {
  const view = articleView({
    articleId: '9007199254740993', coverUrl: '/api/v1/blog/articles/1/files/2/content',
    category: { categoryName: '技术' }, tags: [{ tagName: 'Vue' }],
    htmlContent: '<p>服务端安全正文</p>', readingMinutes: 2, likeCount: '9223372036854775807',
  })
  assert.equal(view.id, '9007199254740993')
  assert.equal(view.likeCount, '9223372036854775807')
  assert.equal(view.coverImage, '/api/v1/blog/articles/1/files/2/content')
  assert.equal(view.categoryName, '技术')
  assert.deepEqual(view.tagNames, ['Vue'])
  assert.equal(view.contentHtml, '<p>服务端安全正文</p>')
  assert.equal(view.readingTime, 2)
})

test('删除占位映射不带原作者，留言身份只取服务端作者', () => {
  const deleted = discussionView({ commentId: '4', author: null, status: 3, content: '该评论已删除' })
  assert.equal(deleted.id, '4')
  assert.equal(deleted.nickname, '已删除的用户')
  assert.equal(deleted.avatar, undefined)
  const message = discussionView({ messageId: '5', author: { nickname: '读者', avatar: null }, status: 0 })
  assert.equal(message.nickname, '读者')
  assert.equal(message.isApproved, 0)
})

test('复用表单校验正文并按光标替换选区插入原 EmojiPicker 的表情', async () => {
  const selection = []
  const textarea = ref({ selectionStart: 1, selectionEnd: 2, focus() {}, setSelectionRange: (...range) => selection.push(range) })
  const form = useDiscussionForm(textarea)
  form.form.value.content = '   '
  assert.equal(form.validateForm(), false)
  form.form.value.content = '你好呀'
  await form.insertEmoji('🍂')
  assert.equal(form.form.value.content, '你🍂呀')
  assert.deepEqual(selection, [[3, 3]])
  assert.equal(form.validateForm(), true)
  form.resetForm()
  assert.equal(form.form.value.content, '')
  assert.equal(form.errors.value.content, '')
})
