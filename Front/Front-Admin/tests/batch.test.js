import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { prepareBatch } from '../src/batch.js'

const originalFetch = globalThis.fetch
const revision = '"' + 'a'.repeat(64) + '"'
afterEach(() => { globalThis.fetch = originalFetch })
function response(version = revision) {
  return new Response(JSON.stringify({ code: 'OK', msg: '成功', data: {}, errors: [] }), {
    headers: { 'Content-Type': 'application/json', 'X-Revision': version },
  })
}

test('批量选择保留大整数ID和原始顺序，读取每项版本后不自行执行写入', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push(url)
    assert.equal(options.method, undefined)
    return response()
  }
  const ids = ['9223372036854775807', '2']
  assert.deepEqual(await prepareBatch('/admin/articles', 'articleId', ids.map(articleId => ({ articleId }))), {
    items: ids.map(id => ({ id, revision })),
  })
  assert.deepEqual(calls, ids.map(id => '/api/v1/admin/articles/' + id))
})

test('无版本批量动作直接使用选中ID，不请求不存在的浏览记录详情', async () => {
  globalThis.fetch = async () => { throw new Error('不应获取详情') }
  assert.deepEqual(await prepareBatch('/admin/page-views', 'viewId', [{ viewId: '1' }, { viewId: '2' }], false), {
    ids: ['1', '2'],
  })
})

test('空选择、重复ID、超过100项和数字ID都在发送请求前拒绝', async () => {
  globalThis.fetch = async () => { throw new Error('不应发送请求') }
  for (const rows of [[], [{ id: '1' }, { id: '1' }], [{ id: 1 }],
    Array.from({ length: 101 }, (_, index) => ({ id: String(index + 1) }))]) {
    await assert.rejects(prepareBatch('/admin/articles', 'id', rows), /1～100/)
  }
})

test('任一详情或版本读取失败时停止，不用新版本覆盖冲突，也不退回逐条写入', async () => {
  const calls = []
  globalThis.fetch = async (url) => {
    calls.push(url)
    return url.endsWith('/1') ? response() : response('"invalid"')
  }
  await assert.rejects(prepareBatch('/admin/articles', 'id', [{ id: '1' }, { id: '2' }, { id: '3' }]), /有效版本/)
  assert.equal(calls.length, 2)
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'FORBIDDEN', msg: '无权查询', data: null, errors: [] }), {
    status: 403, headers: { 'Content-Type': 'application/json' },
  })
  await assert.rejects(prepareBatch('/admin/articles', 'id', [{ id: '1' }]), /无权查询/)
})
