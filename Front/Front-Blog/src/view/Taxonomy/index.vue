<script setup>
import { computed, inject } from 'vue'
import { useRoute } from 'vue-router'
import { request } from '@/api.js'
import { useArticleList } from '@/composables/useArticleList.js'
import ArticleCard from '@/components/ArticleCard.vue'
import SidebarCard from '@/components/SidebarCard.vue'
import ListPager from '@/components/ListPager.vue'
/** 选择分类或标签，页面按对应类型请求文章。 */
const props = defineProps({ kind: { type: String, required: true } })
// 分类和标签共用列表，接口路径与字段仍按各自类型读取。
const taxonomy = computed(() => props.kind === 'category'
  ? { path: 'categories', id: 'categoryId', name: 'categoryName' }
  : { path: 'tags', id: 'tagId', name: 'tagName' })
const route = useRoute()
const { articleTitle, articleMeta } = inject('setHero')
const { listing, articles, changePage } = useArticleList(10, async (signal) => {
  const { path, id, name } = taxonomy.value
  const { data } = await request(`/blog/${path}/${encodeURIComponent(route.params.slug)}`, { signal })
  articleTitle.value = data[name]
  articleMeta.value = (data.description || '') + ' · 共 ' + data.articleCount + ' 篇文章'

  return { [id]: data[id] }
})
</script>

<template>
  <div class="taxonomy-page">
    <div class="taxonomy-layout">
      <div class="taxonomy-main">
        <p v-if="listing.error" role="alert">
          {{ listing.error }}
          <button @click="listing.reload">重试</button>
        </p>
        <div v-if="listing.loading" class="loading-placeholder">
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
            <ListPager :data="listing.data" @change="changePage" />
          </div>
        </template>
        <div v-else-if="!listing.error" class="empty-card">
          <p class="empty">{{ kind === 'category' ? '该分类下暂无文章' : '该标签下暂无文章' }}</p>
        </div>
      </div>
      <SidebarCard />
    </div>
  </div>
</template>

<style scoped>
.taxonomy-page {
  width: 100%;
}
.taxonomy-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.taxonomy-main {
  flex: 1;
  min-width: 0;
}

/* 骨架屏 */
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
  color: var(--blog-text3);
  padding: 60px 0;
  font-size: 14px;
  margin: 0;
}

@media (max-width: 1220px) {
  .taxonomy-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(236px, 280px);
    align-items: start;
  }
}

@media (max-width: 960px) {
  .taxonomy-layout {
    flex-direction: column;
    display: flex;
  }
  .taxonomy-main {
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
