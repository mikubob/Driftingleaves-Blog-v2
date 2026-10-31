import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { effectScope } from 'vue'
import { useList } from '../src/composables/useList.js'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
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
