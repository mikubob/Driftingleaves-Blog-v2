import { onScopeDispose, ref } from 'vue'
import { getPage } from '../api.js'
import { useList } from './useList.js'

/** 评论和留言按层加载；筛选时平铺全部命中记录，避免回复因父节点不匹配而漏审。 */
export function useDiscussionTree(path, idKey, initialFilters, initialPageSize = 10) {
  const list = useList(path, { ...initialFilters, parentId: '0' }, initialPageSize)
  const table = ref(null)
  const tableKey = ref(0)
  const filtered = ref(false)
  const repliesLoading = ref(false)
  const branches = new Map()
  let generation = 0

  /** 刷新时清空展开缓存并取消旧回复请求，审核或删除后不保留过期分支。 */
  async function load(reset = false) {
    generation += 1
    for (const branch of branches.values()) {
      branch.controller?.abort()
    }
    branches.clear()
    repliesLoading.value = false
    filtered.value = Object.keys(initialFilters).some(key => {
      const value = list.filters[key]
      return value !== '' && value !== null && value !== undefined
    })
    list.filters.parentId = filtered.value ? '' : '0'
    tableKey.value += 1
    await list.load(reset)
  }

  /** 每层先取20条，剩余回复通过同层的加载按钮继续分页，失败保留原页以便重试。 */
  async function fetchReplies(row, resolve) {
    const id = row[idKey]
    let branch = branches.get(id)
    if (!branch) {
      branch = { records: [], page: 1, loading: false }
      branches.set(id, branch)
    }
    if (branch.loading) {
      return
    }
    branch.loading = true
    repliesLoading.value = true
    const currentGeneration = generation
    branch.controller = new AbortController()
    let failureMessage = ''
    let more = true
    try {
      const data = await getPage(path, { parentId: id, page: branch.page, pageSize: 20 }, branch.controller.signal)
      if (currentGeneration !== generation) {
        return
      }
      const knownIds = new Set(branch.records.map(record => record[idKey]))
      branch.records.push(...data.records.filter(record => !knownIds.has(record[idKey])))
      branch.page += 1
      more = BigInt(data.page) < BigInt(data.totalPages)
    } catch (failure) {
      if (failure.name === 'AbortError' || currentGeneration !== generation) {
        return
      }
      failureMessage = failure.message
    } finally {
      branch.loading = false
      if (currentGeneration === generation) {
        repliesLoading.value = [...branches.values()].some(item => item.loading)
      }
    }
    const children = [...branch.records]
    if (more) {
      children.push({
        [idKey]: `more:${id}`, replyPager: true, parent: row, error: failureMessage,
      })
    }
    if (resolve) {
      resolve(children)
    } else {
      table.value?.updateKeyChildren(id, children)
    }
  }

  function changePage(value) {
    list.page.value = value
    return load()
  }

  function changePageSize(value) {
    list.pageSize.value = value
    return load(true)
  }

  function reset() {
    Object.assign(list.filters, initialFilters)
    return load(true)
  }

  onScopeDispose(() => {
    generation += 1
    for (const branch of branches.values()) {
      branch.controller?.abort()
    }
  })

  return {
    ...list, table, tableKey, filtered, repliesLoading, load, reset, changePage, changePageSize,
    loadReplies: (row, treeNode, resolve) => fetchReplies(row, resolve),
    loadMore: row => fetchReplies(row.parent),
  }
}
