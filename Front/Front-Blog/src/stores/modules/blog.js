import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import { getPage, request } from '../../api.js'

/** 管理博客的公开配置和首页数据，供页面共用。 */
export const useBlogStore = defineStore('blog', () => {
  const personalInfo = ref({})
  const report = ref({})
  const site = ref({})
  const categories = ref([])
  const tags = ref([])
  const musics = ref([])
  const socialLinks = ref([])
  const loaded = ref(false)
  const loadError = ref('')
  const optionalState = reactive({
    categories: { loading: false, error: '' },
    tags: { loading: false, error: '' },
    social: { loading: false, error: '' },
  })
  const initialized = new Set()
  const inflight = new Map()
  let pending

  async function allPages(path) {
    const records = []
    let page = 1
    while (true) {
      const result = await getPage(path, { page, pageSize: 100 })
      records.push(...result.records)
      if (BigInt(page) >= BigInt(result.totalPages)) {
        return records
      }
      page += 1
    }
  }

  /** 按需加载非首屏公开数据，同一模块并发请求自动复用，并维护加载与错误态。 */
  function loadOptional(key, loader) {
    if (initialized.has(key)) {
      return Promise.resolve()
    }
    if (inflight.has(key)) {
      return inflight.get(key)
    }
    const state = optionalState[key]
    state.loading = true
    state.error = ''
    const task = loader()
      .then(() => {
        initialized.add(key)
      })
      .catch((error) => {
        state.error = error.message || '加载失败'
        throw error
      })
      .finally(() => {
        state.loading = false
        inflight.delete(key)
      })
    inflight.set(key, task)
    return task
  }

  /** 按需加载分类列表（仅打开侧栏分类弹窗时请求）。 */
  function ensureCategories() {
    return loadOptional('categories', async () => {
      categories.value = (await allPages('/blog/categories')).map((item) => ({
        ...item, id: item.categoryId, name: item.categoryName,
      }))
    })
  }

  /** 按需加载标签列表（仅打开侧栏标签弹窗时请求）。 */
  function ensureTags() {
    return loadOptional('tags', async () => {
      tags.value = (await allPages('/blog/tags')).map((item) => ({
        ...item, id: item.tagId, name: item.tagName,
      }))
    })
  }

  /** 按需加载社交外链（仅进入关于页时请求）。 */
  function ensureSocialLinks() {
    return loadOptional('social', async () => {
      socialLinks.value = (await request('/blog/social-links')).data
    })
  }

  /** 加载博客首屏必需数据（资料、统计、配置、音乐），重试失败模块并复用已加载数据。 */
  function init() {
    if (pending) {
      return pending
    }
    const sources = [
      ['profile', async () => {
        const { data } = await request('/blog/profile')
        personalInfo.value = {
          ...data,
          nickname: data?.nickname || '', tag: data?.tagline || '',
          github: data?.githubUrl, email: data?.publicEmail,
        }
      }],
      ['report', async () => {
        const { data } = await request('/blog/statistics')
        report.value = {
          articleTotalCount: data.articleCount, categoryTotalCount: data.categoryCount,
          tagTotalCount: data.tagCount, viewTodayCount: data.todayPageViewCount,
          viewTotalCount: data.pageViewCount, visitorTotalCount: data.visitorCount,
        }
      }],
      ['site', async () => {
        const { data } = await request('/blog/configs')
        site.value = data.find((item) => item.configKey === 'site')?.configValue || {}
      }],
      ['music', async () => {
        musics.value = (await allPages('/blog/music')).map((item) => ({
          ...item, id: item.musicId, musicUrl: item.audioUrl, coverImage: item.coverUrl,
        }))
      }],
    ]
    pending = Promise.allSettled(sources.map(async ([key, load]) => {
      if (!initialized.has(key)) {
        await load()
        initialized.add(key)
      }
    })).then((results) => {
      const failure = results.find((result) => result.status === 'rejected')
      loadError.value = failure ? failure.reason.message : ''
      loaded.value = !failure
    }).finally(() => {
      pending = null
    })
    return pending
  }

  return {
    personalInfo, report, site, categories, tags, musics, socialLinks,
    loaded, loadError, optionalState, init, ensureCategories, ensureTags, ensureSocialLinks,
  }
})
