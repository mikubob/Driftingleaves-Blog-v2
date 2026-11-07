import assert from 'node:assert/strict'
import test from 'node:test'
import { adminArticleUrl, editorImageUrl } from '../src/editorPreview.mjs'

test('编辑器通过原文件接口预览图片，保存后的预览仍校验文章引用', () => {
  const url = '/api/v1/blog/articles/1/files/9007199254740993/content'
  const attachment = '/api/v1/admin/articles/1/files/9007199254740993/content'

  assert.equal(editorImageUrl(url, '1'), '/api/v1/admin/files/9007199254740993/content')
  assert.equal(editorImageUrl(attachment, '1'), '/api/v1/admin/files/9007199254740993/content')
  assert.equal(editorImageUrl(url, '2'), attachment)
  assert.equal(adminArticleUrl(url), attachment)
  assert.equal(editorImageUrl('https://example.com/photo.png', '1'), 'https://example.com/photo.png')
})
