import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPage, pageNumber } from '../api.js'
import { articleView } from '../shared/presentation.js'
import { useResource } from './useResource.js'

/** 查询文章列表，页面补充各自的筛选条件，翻页保留搜索参数。 */
export function useArticleList(pageSize, loadFilters) {
  const route = useRoute()
  const router = useRouter()
  const searchKeyword = computed(() => typeof route.query.search === 'string' ? route.query.search.trim() : '')
  const listing = useResource(() => route.fullPath.split('#')[0], async (signal) => {
    const params = { page: pageNumber(route.query.page), pageSize }
    if (searchKeyword.value) {
      if ([...searchKeyword.value].length > 100) {
        throw new Error('搜索关键词最多 100 个字符')
      }
      params.keyword = searchKeyword.value
    }
    const filters = await loadFilters(signal)
    return getPage('/blog/articles', { ...params, ...filters }, signal)
  })
  const articles = computed(() => listing.data?.records.map(articleView) || [])

  function changePage(page) {
    router.push({ query: { ...route.query, page: String(page) } })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return { searchKeyword, listing, articles, changePage }
}
