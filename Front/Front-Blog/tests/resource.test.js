import assert from 'node:assert/strict'
import { test } from 'node:test'
import { effectScope, nextTick, ref } from 'vue'
import { useResource } from '../src/composables/useResource.js'

test('快速切换筛选时取消旧请求，迟到响应不能覆盖新内容', async () => {
  const scope = effectScope()
  const filter = ref('old')
  const pending = []
  let resource
  scope.run(() => {
    resource = useResource(() => filter.value, (signal) => new Promise((resolve) => {
      pending.push({ resolve, signal })
    }))
  })
  filter.value = 'new'
  await nextTick()
  assert.equal(pending[0].signal.aborted, true)
  pending[1].resolve('新筛选结果')
  await nextTick()
  pending[0].resolve('迟到的旧结果')
  await nextTick()
  assert.equal(resource.data, '新筛选结果')
  assert.equal(resource.loading, false)
  scope.stop()
  assert.equal(pending[1].signal.aborted, true)
})

test('读取失败显示错误，显式重试成功后才显示空列表', async () => {
  const scope = effectScope()
  let fail = true
  let resource
  scope.run(() => {
    resource = useResource(() => true, async () => {
      if (fail) {
        throw new Error('服务不可用')
      }
      return []
    })
  })
  await nextTick()
  assert.equal(resource.error, '服务不可用')
  assert.equal(resource.data, null)
  fail = false
  await resource.reload()
  assert.equal(resource.error, '')
  assert.deepEqual(resource.data, [])
  scope.stop()
})
