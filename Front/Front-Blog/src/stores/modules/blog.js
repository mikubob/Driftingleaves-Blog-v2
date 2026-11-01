import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getPage, request } from '../../api.js'

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
  const initialized = new Set()
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

  /** 沿用老站共享加载入口，失败模块可重试，成功数据不重复请求。 */
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
      ['categories', async () => {
        categories.value = (await allPages('/blog/categories')).map((item) => ({
          ...item, id: item.categoryId, name: item.categoryName,
        }))
      }],
      ['tags', async () => {
        tags.value = (await allPages('/blog/tags')).map((item) => ({
          ...item, id: item.tagId, name: item.tagName,
        }))
      }],
      ['music', async () => {
        musics.value = (await allPages('/blog/music')).map((item) => ({
          ...item, id: item.musicId, musicUrl: item.audioUrl, coverImage: item.coverUrl,
        }))
      }],
      ['social', async () => {
        socialLinks.value = (await request('/blog/social-links')).data
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

  return { personalInfo, report, site, categories, tags, musics, socialLinks, loaded, loadError, init }
})
