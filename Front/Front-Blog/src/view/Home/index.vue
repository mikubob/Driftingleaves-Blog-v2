<script setup>
import { computed, defineAsyncComponent, inject, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPage, isId, pageNumber, request } from '@/api.js'
import { articleView } from '@/api/presentation.js'
import { useResource } from '@/composables/useResource.js'
import ArticleCard from '@/components/ArticleCard.vue'
import SidebarCard from '@/components/SidebarCard.vue'
import ListPager from '@/components/ListPager.vue'
const HomeTimeWidgets = defineAsyncComponent(() => import('@/components/HomeTimeWidgets.vue'))
const route = useRoute()
const router = useRouter()
const { articleTitle, articleMeta } = inject('setHero')
const showTimeWidgets = ref(false)
const widgetMediaQuery = window.matchMedia('(min-width: 1221px)')
const syncWidgets = (event) => {
  showTimeWidgets.value = event.matches
}
onMounted(() => {
  showTimeWidgets.value = widgetMediaQuery.matches
  widgetMediaQuery.addEventListener('change', syncWidgets)
})
onUnmounted(() => widgetMediaQuery.removeEventListener('change', syncWidgets))
const searchKeyword = computed(() => typeof route.query.search === 'string' ? route.query.search.trim() : '')
const page = computed(() => pageNumber(route.query.page))
const pageSize = 6
const listing = useResource(() => route.fullPath.split('#')[0], async (signal) => {
  const params = { page: page.value, pageSize }
  if (searchKeyword.value) {
    if ([...searchKeyword.value].length > 100) {
      throw new Error('搜索关键词最多 100 个字符')
    }
    params.keyword = searchKeyword.value
  }
  
  if (route.params.userId) {
    if (!isId(route.params.userId)) {
      throw new Error('作者地址无效')
    }
    const { data } = await request('/blog/authors/' + route.params.userId, { signal })
    params.authorId = data.userId
    articleTitle.value = data.nickname + '的文章'
    articleMeta.value = data.intro || ''
  }
  if (route.params.month) {
    if (!/^[1-9]\d{3}-(0[1-9]|1[0-2])$/.test(route.params.month)) {
      throw new Error('归档月份无效')
    }
    params.month = route.params.month
    articleTitle.value = params.month + ' 的文章'
    articleMeta.value = '按 UTC 月份归档'
  }
  
  return getPage('/blog/articles', params, signal)
})
const articles = computed(() => listing.data?.records.map(articleView) || [])
const total = computed(() => listing.data?.total || '0')
const loading = computed(() => listing.loading)
function handlePageChange(page) {
  router.push({ query: { ...route.query, page: String(page) } })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <div class="home-page">
    <div class="home-content">
      <div v-if="showTimeWidgets" class="left-widget-col">
        <HomeTimeWidgets />
      </div>
      <div class="article-col">
        <div v-if="searchKeyword" class="search-result-tip">
          <span>
            搜索:
            <strong>{{ searchKeyword }}</strong>
          </span>
          <span class="search-count">{{ total }} 篇结果</span>
          <a class="clear-search" @click="router.push('/')">&times; 清除</a>
        </div>
        <p v-if="listing.error" role="alert">
          {{ listing.error }}
          <button @click="listing.reload">重试</button>
        </p>
        <div v-if="loading" class="loading-placeholder">
          <div v-for="i in 4" :key="i" class="skeleton-card">
            <div class="skeleton-cover" />
            <div class="skeleton-body">
              <div class="skeleton-line w60" />
              <div class="skeleton-line w90" />
              <div class="skeleton-line w40" />
            </div>
          </div>
        </div>
        <template v-else-if="articles.length">
          <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
          <div v-if="listing.data && BigInt(listing.data.totalPages) > 1n" class="pagination-wrap">
            <ListPager v-if="listing.data" :data="listing.data" @change="handlePageChange" />
          </div>
        </template>
        <div v-else-if="!listing.error" class="empty-tip">暂无文章</div>
      </div>
      <SidebarCard />
    </div>
  </div>
</template>

<style scoped>
.home-page {
  width: 100%;
}

.home-content {
  display: grid;
  grid-template-columns: minmax(220px, 240px) minmax(0, 1fr) minmax(
      248px,
      280px
    );
  gap: 28px;
  align-items: flex-start;
}

.left-widget-col {
  min-width: 0;
  position: sticky;
  top: 74px;
  align-self: flex-start;
}

.article-col {
  min-width: 0;
}

.search-result-tip {
  padding: 12px 16px;
  font-size: 14px;
  color: #606266;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-count {
  color: #909399;
  font-size: 13px;
}

.clear-search {
  color: #303133;
  cursor: pointer;
  font-weight: 600;
  margin-left: auto;
}

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

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.empty-tip {
  padding: 60px 0;
  text-align: center;
  color: #909399;
  font-size: 14px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

@media (max-width: 1440px) {
  .home-content {
    grid-template-columns: minmax(208px, 224px) minmax(0, 1fr) minmax(
        236px,
        264px
      );
    gap: 22px;
  }
}

@media (max-width: 1220px) {
  .home-content {
    grid-template-columns: minmax(0, 1fr) minmax(236px, 280px);
    grid-template-areas:
      'main right'
      'left right';
    align-items: start;
  }

  .article-col {
    grid-area: main;
  }

  .left-widget-col {
    grid-area: left;
    position: static;
  }

  :deep(.sidebar) {
    grid-area: right;
  }
}

@media (max-width: 960px) {
  .home-content {
    grid-template-columns: 1fr;
    grid-template-areas:
      'main'
      'left'
      'right';
  }
}

@media (max-width: 600px) {
  .search-result-tip {
    font-size: 13px;
    padding: 10px 12px;
  }

  .skeleton-card {
    flex-direction: column;
  }

  .skeleton-cover {
    width: 100%;
    height: 160px;
  }
}
</style>
