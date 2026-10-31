import { onScopeDispose, reactive, ref } from 'vue'
import { getPage } from '../api.js'

/** 管理列表共用分页和请求取消，业务筛选与操作留在各页面。 */
export function useList(path, initialFilters = {}, pageSize = 10) {
  const filters = reactive({ ...initialFilters })
  const rows = ref([])
  const page = ref(1)
  const total = ref('0')
  const totalPages = ref('0')
  const loading = ref(false)
  const error = ref('')
  let controller

  async function load(reset = false) {
    if (reset) {
      page.value = 1
    }
    controller?.abort()
    const current = new AbortController()
    controller = current
    loading.value = true
    error.value = ''
    try {
      const data = await getPage(path, { ...filters, page: page.value, pageSize }, current.signal)
      rows.value = data.records
      total.value = data.total
      totalPages.value = data.totalPages
    } catch (failure) {
      if (failure.name !== 'AbortError') {
        error.value = failure.message
      }
    } finally {
      if (controller === current) {
        loading.value = false
      }
    }
  }

  function changePage(value) {
    page.value = value
    return load()
  }

  function reset() {
    Object.assign(filters, initialFilters)
    return load(true)
  }

  onScopeDispose(() => controller?.abort())
  return { filters, rows, page, total, totalPages, loading, error, load, reset, changePage }
}
