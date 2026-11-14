<script setup>
import { computed, defineAsyncComponent, inject, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isId, request } from '@/api.js'
import { useArticleList } from '@/composables/useArticleList.js'
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
const { searchKeyword, listing, articles, changePage } = useArticleList(6, async (signal) => {
  const filters = {}
  if (route.params.userId) {
    if (!isId(route.params.userId)) {
      throw new Error('作者地址无效')
    }
    const { data } = await request('/blog/authors/' + route.params.userId, { signal })
    filters.authorId = data.userId
    articleTitle.value = data.nickname + '的文章'
    articleMeta.value = data.intro || ''
  }
  if (route.params.month) {
    if (!/^[1-9]\d{3}-(0[1-9]|1[0-2])$/.test(route.params.month)) {
      throw new Error('归档月份无效')
    }
    filters.month = route.params.month
    articleTitle.value = filters.month + ' 的文章'
    articleMeta.value = '按 UTC 月份归档'
  }

  return filters
})
const total = computed(() => listing.data?.total || '0')
const keyword = ref(searchKeyword.value)

watch(searchKeyword, (search) => {
  keyword.value = search
})

function submitSearch() {
  const search = keyword.value.trim()
  keyword.value = search
  router.push({ path: '/', query: search ? { search } : {} })
}

function clearSearch() {
  keyword.value = ''
  router.push('/')
}
</script>

<template>
  <div class="home-page">
    <div class="home-content">
      <div v-if="showTimeWidgets" class="left-widget-col">
        <HomeTimeWidgets />
      </div>
      <div class="article-col">
        <header v-if="route.name === 'home'" class="article-toolbar">
          <h2>{{ searchKeyword ? '搜索结果' : '最新文章' }}</h2>
          <form class="article-search" role="search" @submit.prevent="submitSearch">
            <input
              v-model="keyword"
              type="search"
              placeholder="搜索文章…"
              aria-label="搜索文章"
              maxlength="100"
            />
            <button type="submit">
              <i aria-hidden="true" class="iconfont icon-sousuo" />
              搜索
            </button>
          </form>
        </header>
        <div v-if="searchKeyword" class="search-result-tip">
          <span>
            搜索:
            <strong>{{ searchKeyword }}</strong>
          </span>
          <span class="search-count">{{ total }} 篇结果</span>
          <button type="button" class="clear-search" @click="clearSearch">清除搜索</button>
        </div>
        <p v-if="listing.error" role="alert">
          {{ listing.error }}
          <button @click="listing.reload">重试</button>
        </p>
        <div v-if="listing.loading" class="loading-placeholder">
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
            <ListPager :data="listing.data" @change="changePage" />
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

.article-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 16px;
  border: 1px solid var(--blog-border);
  border-radius: 8px;
  background: var(--blog-card);
}

.article-toolbar h2 {
  flex-shrink: 0;
  margin: 0;
  font-family: var(--blog-serif);
  font-size: 17px;
}

.article-search {
  display: flex;
  flex: 0 1 296px;
  min-width: 0;
  min-height: 36px;
  border: 1px solid var(--blog-border);
  border-radius: 6px;
  background: var(--blog-hover);
  font-size: 14px;
  transition: border-color 0.2s;
}

.article-search:focus-within {
  border-color: var(--blog-text3);
}

.article-search input {
  flex: 1;
  min-width: 0;
  padding: 0 10px;
  border: 0;
  border-radius: 5px 0 0 5px;
  background: transparent;
  color: var(--blog-text);
  font: inherit;
  outline: none;
}

.article-search button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 10px;
  border: 0;
  border-left: 1px solid var(--blog-border);
  border-radius: 0 5px 5px 0;
  background: var(--blog-card);
  color: var(--blog-text);
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.2s;
}

.article-search button:hover {
  background: var(--blog-hover);
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
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.search-result-tip > span:first-child {
  flex: 1 1 180px;
  min-width: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.search-count {
  color: var(--blog-text3);
  font-size: 13px;
}

.clear-search {
  flex-shrink: 0;
  padding: 4px 10px;
  border: 1px solid var(--blog-border);
  border-radius: 4px;
  background: var(--blog-card);
  font: inherit;
  white-space: nowrap;
  color: var(--blog-text);
  cursor: pointer;
  font-weight: 600;
  margin-left: auto;
}

.skeleton-card {
  display: flex;
  gap: 0;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
}

.skeleton-cover {
  width: 230px;
  min-height: 170px;
  background: #ebeef5;
  flex-shrink: 0;
}

.skeleton-body {
  flex: 1;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  justify-content: center;
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
  color: var(--blog-text3);
  font-size: 14px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.article-toolbar:has(+ .empty-tip) {
  margin-bottom: 0;
  border-radius: 8px 8px 0 0;
}

.article-toolbar + .empty-tip {
  border-color: var(--blog-border);
  border-top: 0;
  border-radius: 0 0 8px 8px;
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
  .article-toolbar {
    gap: 8px;
    padding: 8px 12px;
  }

  .article-search {
    min-height: 40px;
  }

  .article-search input {
    font-size: 16px;
  }

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
