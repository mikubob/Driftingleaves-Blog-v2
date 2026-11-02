import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import * as vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import { request } from '../api.js'
import { prepareBatch } from '../batch.js'
import { nullable } from '../format.js'

const source = readFileSync(new URL('./Categories.vue', import.meta.url), 'utf8')
const compiled = compileScript(parse(source).descriptor, { id: 'category-status-check' })
const revision = `"${'a'.repeat(64)}"`

/** 编译真实组件和请求适配，仅替换列表、提示、权限和网络边界。 */
function createCategory(t, { permitted = true, status = 200 } = {}) {
  const calls = []
  const messages = []
  let loads = 0
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, ...options, body: options.body ? JSON.parse(options.body) : undefined })
    const reading = !options.method
    return new Response(JSON.stringify({
      code: reading || status === 200 ? 'OK' : 'REVISION_CONFLICT',
      msg: reading || status === 200 ? '成功' : '版本过期',
      data: reading ? { categoryId: '9007199254740993' } : { items: [] },
      errors: [],
    }), { status: reading ? 200 : status, headers: { 'X-Revision': revision } })
  })
  const noop = () => {}
  const modules = {
    vue: { ...vue, onMounted: noop },
    'element-plus': {
      ElMessage: { success: (value) => messages.push(value), error: (value) => messages.push(value) },
      ElMessageBox: { confirm: async () => {} },
    },
    '../api.js': { request },
    '../batch.js': { prepareBatch },
    '../format.js': { nullable },
    '../session.js': { can: () => permitted },
    '../composables/useList.js': { useList: () => ({ load: () => { loads += 1 } }) },
  }
  const bindings = Object.entries(compiled.imports).map(([name, binding]) => [
    name, modules[binding.source]?.[binding.imported] ?? {},
  ])
  const code = compiled.content.replace(/^import[^\n]*\n/gm, '').replace('export default', 'return')
  const component = new Function(...bindings.map(([name]) => name), code)(...bindings.map(([, value]) => value))
  return { state: component.setup({}, { expose: noop }), calls, messages, get loads() { return loads } }
}

test('单个分类展示与隐藏均提交一项到批量接口，ID不丢失精度', async (t) => {
  const check = createCategory(t)
  for (const status of [1, 0]) {
    await check.state.changeStatus({ categoryId: '9007199254740993', categoryName: '技术', status })
    const [read, write] = check.calls.slice(-2)
    assert.equal(read.url, '/api/v1/admin/categories/9007199254740993')
    assert.equal(write.url, '/api/v1/admin/categories/batch-status')
    assert.equal(write.method, 'PUT')
    assert.deepEqual(write.body, { items: [{ id: '9007199254740993', revision }], status: status === 1 ? 0 : 1 })
    assert.equal(write.headers.has('If-Revision'), false)
  }
  assert.equal(check.loads, 2)
  assert.equal(check.state.changingStatus.value, false)
})

test('批量展示和隐藏复用批量组件，缺少权限时不显示状态操作', (t) => {
  const allowed = createCategory(t)
  const actions = allowed.state.batchActions.value.filter((action) => action.path === 'batch-status')
  assert.deepEqual(actions.map((action) => [action.method, action.body.status]), [['PUT', 1], ['PUT', 0]])
  const denied = createCategory(t, { permitted: false })
  assert.equal(denied.state.batchActions.value.length, 0)
})

test('编辑资料不提交状态，新建保留初始展示状态', async (t) => {
  const check = createCategory(t)
  check.state.id.value = '7'
  check.state.revision.value = revision
  check.state.form.status = 0
  await check.state.save()
  assert.equal(check.calls[0].method, 'PUT')
  assert.equal(Object.hasOwn(check.calls[0].body, 'status'), false)
  check.state.id.value = ''
  await check.state.save()
  assert.equal(check.calls[1].method, 'POST')
  assert.equal(check.calls[1].body.status, 0)
})

test('版本冲突提示失败，释放忙状态且不显示操作成功', async (t) => {
  const check = createCategory(t, { status: 412 })
  await check.state.changeStatus({ categoryId: '9007199254740993', categoryName: '技术', status: 1 })
  assert.equal(check.state.changingStatus.value, false)
  assert.equal(check.loads, 0)
  assert.equal(check.messages.length, 1)
  assert.match(check.messages[0], /重新获取详情/)
})
