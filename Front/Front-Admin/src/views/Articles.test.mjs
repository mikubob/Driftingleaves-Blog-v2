import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import * as vue from 'vue'
import { compileScript, parse } from 'vue/compiler-sfc'

const source = readFileSync(new URL('./Articles.vue', import.meta.url), 'utf8')
const compiled = compileScript(parse(source).descriptor, { id: 'article-detail-check' })

/** 编译真实组件，只替换请求、路由及无关列表边界，不复制详情逻辑。 */
function createDetail() {
  const pending = []
  const noop = () => {}
  const modules = {
    vue: { ...vue, onActivated: noop, onMounted: noop, onBeforeUnmount: noop, onDeactivated: noop },
    'vue-router': { useRoute: () => ({ meta: {} }), useRouter: () => ({}) },
    '../api.js': {
      request: (path, options) => new Promise((resolve, reject) => {
        pending.push({ path, ...options, resolve, reject })
      }),
    },
    '../composables/useList.js': { useList: () => ({ filters: { trash: 0 } }) },
  }
  const bindings = Object.entries(compiled.imports).map(([name, binding]) => [
    name, modules[binding.source]?.[binding.imported] ?? {},
  ])
  const code = compiled.content.replace(/^import[^\n]*\n/gm, '').replace('export default', 'return')
  const component = new Function(...bindings.map(([name]) => name), code)(...bindings.map(([, value]) => value))
  return { state: component.setup({}, { expose: noop }), pending }
}

test('立即打开详情并加载完整资料，ID 保持字符串', async () => {
  const { state, pending } = createDetail()
  const article = { articleId: '9223372036854775807', markdownContent: '# 正文', tags: [] }
  const task = state.view(article)
  assert.equal(state.detailVisible.value, true)
  assert.equal(state.detailLoading.value, true)
  assert.equal(state.detail.value, null)
  assert.equal(pending[0].path, '/admin/articles/9223372036854775807')
  pending[0].resolve({ data: article })
  await task
  assert.deepEqual(state.detail.value, article)
  assert.equal(state.detailLoading.value, false)
  assert.equal(state.detailError.value, '')
})

test('失败后清空旧详情并允许重新查询同一篇文章', async () => {
  const { state, pending } = createDetail()
  state.detail.value = { title: '旧正文' }
  const task = state.view({ articleId: '7' })
  pending[0].reject(new Error('文章不存在'))
  await task
  assert.equal(state.detail.value, null)
  assert.equal(state.detailError.value, '文章不存在')
  assert.equal(state.detailLoading.value, false)
  const retry = state.view(state.selectedArticle.value)
  assert.equal(state.detailError.value, '')
  assert.equal(pending[1].path, '/admin/articles/7')
  pending[1].resolve({ data: { articleId: '7', title: '重试成功' } })
  await retry
  assert.equal(state.detail.value.title, '重试成功')
})

test('切换文章不接收旧响应，关闭时取消查询且不回填详情', async () => {
  const { state, pending } = createDetail()
  const first = state.view({ articleId: '1' })
  const second = state.view({ articleId: '2' })
  assert.equal(pending[0].signal.aborted, true)
  pending[0].resolve({ data: { articleId: '1' } })
  await first
  assert.equal(state.detail.value, null)
  assert.equal(state.detailLoading.value, true)
  pending[1].resolve({ data: { articleId: '2' } })
  await second
  assert.equal(state.detail.value.articleId, '2')
  const third = state.view({ articleId: '3' })
  state.closeDetail()
  assert.equal(pending[2].signal.aborted, true)
  assert.equal(state.detailVisible.value, false)
  pending[2].reject(new Error('请求取消'))
  await third
  assert.equal(state.detail.value, null)
  assert.equal(state.detailError.value, '')
})
