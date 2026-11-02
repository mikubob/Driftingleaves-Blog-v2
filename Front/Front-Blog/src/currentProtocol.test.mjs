import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { createMemoryHistory, createRouter } from 'vue-router'
import { request } from './api.js'

function respond(t, data) {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    code: 'OK', msg: '成功', data, errors: [],
  })))
}

const article = {
  articleId: '1', title: '文章', slug: 'current-article', summary: '',
  author: { userId: '2', nickname: '作者', avatar: null }, category: null, tags: [],
  coverUrl: null, articleType: 1, isTop: 0, allowComment: 1,
  viewCount: '1', likeCount: '0', commentCount: '0',
  publishTime: '2026-11-03T00:00:00.000Z', updateTime: '2026-11-03T00:00:00.000Z',
  markdownContent: '# 当前正文', wordCount: '4', readingMinutes: 1,
  prevArticle: null, nextArticle: null, relatedArticles: [],
}

test('当前文章详情只用Markdown，不要求HTML正文', async (t) => {
  respond(t, article)
  const result = await request('/blog/articles/current-article')
  assert.equal(result.data.markdownContent, '# 当前正文')
  assert.equal(Object.hasOwn(result.data, 'htmlContent'), false)
})

test('文章详情缺少Markdown时拒绝响应，不回退HTML', async (t) => {
  const { markdownContent, ...missing } = article
  respond(t, { ...missing, htmlContent: '<h1>正文</h1>' })
  await assert.rejects(request('/blog/articles/current-article'), /格式/)
})

test('留言响应不接受删除占位状态', async (t) => {
  respond(t, {
    page: 1, pageSize: 10, total: '1', totalPages: '1', records: [{
      messageId: '1', content: '正文', htmlContent: '<p>正文</p>', contentFormat: 0,
      author: { userId: '2', nickname: '作者', avatar: null },
      location: null, userAgentOs: null, userAgentBrowser: null,
      isSecret: 0, isOwn: false, status: 3, createTime: '2026-11-03T00:00:00.000Z',
    }],
  })
  await assert.rejects(request('/blog/messages'), /格式/)
})

test('真实路由只识别现行文章和搜索入口', () => {
  const source = readFileSync(new URL('./router.js', import.meta.url), 'utf8')
    .replace(/^import[^\n]*\n/gm, '')
    .replace('export const router', 'const router')
  const router = new Function('createRouter', 'createWebHistory', 'apiRoot', 'reportNotFound', `${source}\nreturn router`)(
    createRouter, createMemoryHistory, '/api/v1', () => {},
  )
  assert.equal(router.resolve('/articles/current-article').name, 'article')
  assert.equal(router.resolve('/?search=keyword').name, 'home')
  assert.equal(router.resolve('/article/current-article').name, 'not-found')
  assert.equal(router.resolve('/search?keyword=test').name, 'not-found')
})
