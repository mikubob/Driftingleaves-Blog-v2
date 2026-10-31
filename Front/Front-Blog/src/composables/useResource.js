import { onScopeDispose, reactive, ref, shallowRef, watch } from 'vue'

/** 页面筛选改变时取消旧读取，保留加载、失败和空结果的区别。 */
export function useResource(source, load) {
  const data = shallowRef(null)
  const error = ref('')
  const loading = ref(false)
  let controller

  async function reload() {
    controller?.abort()
    const current = new AbortController()
    controller = current
    loading.value = true
    error.value = ''
    data.value = null
    try {
      const result = await load(current.signal)
      if (!current.signal.aborted) {
        data.value = result
      }
    } catch (failure) {
      if (!current.signal.aborted) {
        error.value = failure.message
      }
    } finally {
      if (!current.signal.aborted) {
        loading.value = false
      }
    }
  }

  watch(source, reload, { immediate: true })
  onScopeDispose(() => controller?.abort())
  return reactive({ data, error, loading, reload })
}
