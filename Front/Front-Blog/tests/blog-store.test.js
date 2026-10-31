import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createPinia, setActivePinia } from 'pinia'
import { useBlogStore } from '../src/stores/modules/blog.js'

test('原侧栏加载完整字典，失败重试只读取未成功模块，统计字符串保持精度', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  let failTags = true
  globalThis.fetch = async (url) => {
    const target = new URL(url, 'http://example.test')
    const path = target.pathname.slice('/api/v1'.length)
    calls.push(path)
    let data
    let status = 200
    if (path === '/home/profile') {
      data = null
    } else if (path === '/blog/statistics') {
      data = Object.fromEntries(['articleCount', 'categoryCount', 'tagCount', 'pageViewCount', 'visitorCount', 'todayPageViewCount'].map((key) => [key, '9007199254740993']))
    } else if (path === '/blog/configs' || path === '/home/social-links') {
      data = []
    } else {
      const page = Number(target.searchParams.get('page'))
      let records = []
      if (path === '/blog/categories') {
        records = [{ categoryId: String(page), categoryName: `分类${page}`, slug: `category-${page}` }]
      } else if (path === '/blog/tags' && failTags) {
        status = 503
        failTags = false
      }
      data = { page, pageSize: 100, total: path === '/blog/categories' ? '2' : '0', totalPages: path === '/blog/categories' ? '2' : '0', records }
    }
    return new Response(JSON.stringify({ code: status === 200 ? 'OK' : 'SERVICE_UNAVAILABLE', msg: '测试服务不可用', data, errors: [] }), { status })
  }
  try {
    setActivePinia(createPinia())
    const store = useBlogStore()
    await Promise.all([store.init(), store.init()])
    assert.equal(store.loaded, false)
    assert.equal(store.loadError, '测试服务不可用')
    assert.deepEqual(store.categories.map((item) => item.id), ['1', '2'])
    assert.equal(store.report.viewTotalCount, '9007199254740993')
    await store.init()
    assert.equal(store.loaded, true)
    assert.equal(store.loadError, '')
    assert.equal(calls.filter((path) => path === '/blog/categories').length, 2)
    assert.equal(calls.filter((path) => path === '/blog/tags').length, 2)
    assert.equal(calls.filter((path) => path === '/home/profile').length, 1)
  } finally {
    globalThis.fetch = originalFetch
  }
})
