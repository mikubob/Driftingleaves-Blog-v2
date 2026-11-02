import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import * as vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'
import { request } from './api.js'
import { prepareBatch } from './batch.js'
import * as format from './format.js'

const revision = `"${'a'.repeat(64)}"`

/** 编译真实页面或组件，保留真实请求及批量准备函数，只替换网络、消息和列表边界。 */
function setup(t, name, props = {}) {
  const calls = []
  const messages = []
  const events = []
  const noop = () => {}
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, ...options, body: options.body ? JSON.parse(options.body) : undefined })
    return new Response(JSON.stringify({ code: 'OK', msg: '成功', data: {}, errors: [] }), {
      headers: { 'X-Revision': revision },
    })
  })
  const modules = {
    vue: { ...vue, onMounted: noop, onBeforeUnmount: noop },
    'element-plus': {
      ElMessage: { success: (value) => messages.push(value), error: (value) => messages.push(value), warning: noop },
      ElMessageBox: { confirm: async () => {} },
    },
    './api.js': { request },
    './batch.js': { prepareBatch },
    './format.js': format,
    './session.js': { can: () => true },
    './composables/useList.js': { useList: () => ({ filters: {}, load: noop }) },
  }
  const source = readFileSync(new URL(name, import.meta.url), 'utf8')
  const compiled = compileScript(parse(source).descriptor, { id: name })
  const bindings = Object.entries(compiled.imports).map(([key, value]) => [
    key, modules[value.source.replace('../', './')]?.[value.imported] ?? {},
  ])
  const code = compiled.content.replace(/^import[^\n]*\n/gm, '').replace('export default', 'return')
  const component = new Function(...bindings.map(([key]) => key), code)(...bindings.map(([, value]) => value))
  const state = component.setup(props, { expose: noop, emit: (...args) => events.push(args) })
  return { state, calls, messages, events }
}

for (const name of ['Links', 'Music', 'Socials', 'Skills', 'Experiences', 'Roles', 'Permissions', 'Menus', 'Users']) {
  test(`${name} 编辑不传状态，新建保留初始状态`, async (t) => {
    const check = setup(t, `./views/${name}.vue`)
    check.state.id.value = '7'
    check.state.revision.value = revision
    check.state.form.status = 0
    if (name === 'Experiences') {
      check.state.form.content = '经历正文'
    }
    if (name === 'Menus') {
      check.state.form.permissionId = '9'
    }
    await check.state.save()
    const edited = check.calls.find((call) => call.method === 'PUT')
    assert.ok(edited, name)
    assert.equal(Object.hasOwn(edited.body, 'status'), false)
    check.state.id.value = ''
    if (name === 'Users') {
      check.state.form.password = 'Example-password-2026'
    }
    await check.state.save()
    const created = check.calls.find((call) => call.method === 'POST')
    assert.ok(created, name)
    assert.equal(created.body.status, 0)
    if (name !== 'Users') {
      const actions = check.state.batchActions.value.filter((action) => action.path === 'batch-status')
      assert.deepEqual(actions.map((action) => action.body.status), name === 'Links' ? [1, 2, 0] : [1, 0])
      assert.ok(actions.every((action) => action.method === 'PUT'))
    }
    if (name === 'Menus') {
      const remove = check.state.batchActions.value.find((action) => action.path === 'batch-delete')
      assert.equal(remove.accept({ children: [{}] }), false)
      assert.equal(remove.accept({ children: [] }), true)
    }
  })
}

test('行内指定目标复用真实批量执行，未多选也只发送一次批量状态请求', async (t) => {
  const check = setup(t, './components/BatchActions.vue', {
    resource: '/admin/music', idKey: 'musicId', rows: [], actions: [], disabled: false,
  })
  await check.state.execute({ label: '批量隐藏', path: 'batch-status', method: 'PUT', body: { status: 0 } }, [
    { musicId: '9007199254740993' },
  ])
  assert.equal(check.calls.length, 2)
  assert.equal(check.calls[0].url, '/api/v1/admin/music/9007199254740993')
  assert.equal(check.calls[1].url, '/api/v1/admin/music/batch-status')
  assert.deepEqual(check.calls[1].body, { items: [{ id: '9007199254740993', revision }], status: 0 })
  assert.deepEqual(check.events, [['completed']])
  assert.equal(check.state.busy.value, false)
})

test('批量限制访客仍通过extra提交时间，状态组件改造不改变原请求', async (t) => {
  const check = setup(t, './components/BatchActions.vue', {
    resource: '/admin/visitors', idKey: 'visitorId', rows: [], actions: [], disabled: false,
  })
  check.state.selected.value = [{ visitorId: '8' }]
  check.state.blockAction.value = { label: '批量限制', path: 'batch-block', method: 'PUT', block: true }
  check.state.permanent.value = true
  await check.state.block()
  assert.deepEqual(check.calls[1].body, { items: [{ id: '8', revision }], blockedUntil: null })
})
