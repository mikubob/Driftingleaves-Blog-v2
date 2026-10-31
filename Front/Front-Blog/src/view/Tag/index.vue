<script setup>
import { computed, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPage, isId, pageNumber, request } from '@/api.js'
import { articleView } from '@/api/presentation.js'
import { useResource } from '@/composables/useResource.js'
import ArticleCard from '@/components/ArticleCard.vue'
import SidebarCard from '@/components/SidebarCard.vue'
import ListPager from '@/components/ListPager.vue'
const route = useRoute()
const router = useRouter()
const { articleTitle, articleMeta } = inject('setHero')
const searchKeyword = computed(() => typeof route.query.search === 'string' ? route.query.search.trim() : '')
const page = computed(() => pageNumber(route.query.page))
const pageSize = 10
const listing = useResource(() => route.fullPath.split('#')[0], async (signal) => {
  const params = { page: page.value, pageSize }
  if (searchKeyword.value) {
    if ([...searchKeyword.value].length > 100) {
      throw new Error('搜索关键词最多 100 个字符')
    }
    params.keyword = searchKeyword.value
  }
  
  const { data } = await request('/blog/tags/' + encodeURIComponent(route.params.slug), { signal })
  params.tagId = data.tagId
  articleTitle.value = data.tagName
  articleMeta.value = (data.description || '') + ' · 共 ' + data.articleCount + ' 篇文章'
  
  return getPage('/blog/articles', params, signal)
})
const articles = computed(() => listing.data?.records.map(articleView) || [])
const total = computed(() => listing.data?.total || '0')
const loading = computed(() => listing.loading)
function handlePageChange(page) {
  router.push({ query: { ...route.query, page: String(page) } })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
const handlePage = handlePageChange
</script>

<template>
  <div class="tag-page">
    <div class="tag-layout">
      <div class="tag-main">
        <p v-if="listing.error" role="alert">
          {{ listing.error }}
          <button @click="listing.reload">重试</button>
        </p>
        <div v-if="loading" class="loading-placeholder">
          <div v-for="i in 3" :key="i" class="skeleton-card">
            <div class="skeleton-cover" />
            <div class="skeleton-body">
              <div class="skeleton-line w60" />
              <div class="skeleton-line w90" />
              <div class="skeleton-line w40" />
            </div>
          </div>
        </div>
        <template v-else-if="articles.length">
          <ArticleCard v-for="a in articles" :key="a.id" :article="a" />
          <div v-if="listing.data && BigInt(listing.data.totalPages) > 1n" class="pager">
            <ListPager v-if="listing.data" :data="listing.data" @change="handlePageChange" />
          </div>
        </template>
        <div v-else-if="!listing.error" class="empty-card">
          <p class="empty">该标签下暂无文章</p>
        </div>
      </div>
      <SidebarCard />
    </div>
  </div>
</template>

<style scoped>
.tag-page {
  width: 100%;
}
.tag-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.tag-main {
  flex: 1;
  min-width: 0;
}

/* 骨架屏 */
.skeleton-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
}
.skeleton-cover {
  width: 200px;
  height: 130px;
  background: #ebeef5;
  border-radius: 6px;
  flex-shrink: 0;
}
.skeleton-body {
  flex: 1;
}
.skeleton-line {
  height: 14px;
  background: #ebeef5;
  border-radius: 4px;
  margin-bottom: 10px;
}
.w60 {
  width: 60%;
}
.w90 {
  width: 90%;
}
.w40 {
  width: 40%;
}

.pager {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.empty-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}
.empty {
  text-align: center;
  color: #909399;
  padding: 60px 0;
  font-size: 14px;
  margin: 0;
}

@media (max-width: 1220px) {
  .tag-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(236px, 280px);
    align-items: start;
  }
}

@media (max-width: 960px) {
  .tag-layout {
    flex-direction: column;
    display: flex;
  }
  .tag-main {
    width: 100%;
  }
}
@media (max-width: 600px) {
  .skeleton-card {
    flex-direction: column;
  }
  .skeleton-cover {
    width: 100%;
    height: 160px;
  }
  .empty-card {
    padding: 16px;
  }
}
</style>
