import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { effectScope } from 'vue'
import { useList } from '../src/composables/useList.js'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})

test('切换每页条数回到第一页，后续翻页和筛选继续使用所选条数', async () => {
  const scope = effectScope()
  const list = scope.run(() => useList('/admin/tags', { keyword: '' }))
  const calls = []
  globalThis.fetch = async (url) => {
    const query = new URL(url, 'https://example.test').searchParams
    calls.push(Object.fromEntries(query))
    const pageSize = Number(query.get('pageSize'))
    return new Response(JSON.stringify({
      code: 'OK',
      data: {
        page: Number(query.get('page')), pageSize, total: '100',
        totalPages: String(Math.ceil(100 / pageSize)), records: [],
      },
      errors: [],
    }))
  }

  try {
    await list.changePage(5)
    list.filters.keyword = '分页测试'
    await list.changePageSize(20)
    assert.equal(list.page.value, 1)
    assert.equal(list.pageSize.value, 20)
    assert.equal(list.totalPages.value, '5')
    assert.deepEqual(calls.at(-1), { keyword: '分页测试', page: '1', pageSize: '20' })

    await list.changePage(3)
    assert.deepEqual(calls.at(-1), { keyword: '分页测试', page: '3', pageSize: '20' })
    await list.reset()
    assert.deepEqual(calls.at(-1), { page: '1', pageSize: '20' })
  } finally {
    scope.stop()
  }
})

test('列表新筛选取消旧请求，错误状态不伪装成空列表成功', async () => {
  const scope = effectScope()
  const list = scope.run(() => useList('/admin/tags', { keyword: '', status: 0 }))
  let firstSignal
  let call = 0
  globalThis.fetch = async (_url, options) => {
    call += 1
    if (call === 1) {
      firstSignal = options.signal
      return new Promise((_resolve, reject) => {
        options.signal.addEventListener(
          'abort',
          () => reject(new DOMException('取消', 'AbortError')),
          { once: true },
        )
      })
    }
    return new Response(
      JSON.stringify({
        code: 'OK',
        data: {
          page: 1,
          pageSize: 10,
          total: '1',
          totalPages: '1',
          records: [{ tagId: '1', tagName: '最新结果' }],
        },
        errors: [],
      }),
    )
  }
  const first = list.load()
  list.filters.keyword = '最新'
  await list.load(true)
  await first
  assert.equal(firstSignal.aborted, true)
  assert.equal(list.rows.value[0].tagName, '最新结果')
  assert.equal(list.loading.value, false)
  globalThis.fetch = async () => {
    throw new TypeError('网络断开')
  }
  await list.load()
  assert.match(list.error.value, /无法连接/)
  assert.equal(list.rows.value[0].tagName, '最新结果')
  scope.stop()
})
