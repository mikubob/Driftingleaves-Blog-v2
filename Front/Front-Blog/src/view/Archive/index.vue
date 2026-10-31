<script setup>
import { computed, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPage, pageNumber } from '@/api.js'
import { useResource } from '@/composables/useResource.js'
import SidebarCard from '@/components/SidebarCard.vue'
import ListPager from '@/components/ListPager.vue'
const route = useRoute()
const router = useRouter()
const { articleTitle, articleMeta } = inject('setHero')
articleTitle.value = '归档'
articleMeta.value = '时光轴上的足迹'
const listing = useResource(() => route.query.page, (signal) => getPage('/blog/archives', { page: pageNumber(route.query.page), pageSize: 12 }, signal))
const loading = computed(() => listing.loading)
const totalCount = computed(() => (listing.data?.records || []).reduce((total, item) => total + BigInt(item.articleCount), 0n).toString())
const yearGroups = computed(() => {
  const groups = new Map()
  for (const item of listing.data?.records || []) {
    const year = item.month.slice(0, 4)
    if (!groups.has(year)) {
      groups.set(year, [])
    }
    groups.get(year).push(item)
  }
  return [...groups].map(([year, items]) => ({ year, items }))
})
const goMonth = (month) => router.push('/archive/' + month)
const changePage = (page) => router.push({ query: { page: String(page) } })
</script>

<template>
  <div class="archive-page">
    <div class="archive-layout">
      <div class="archive-main">
        <div class="content-card">
          <div class="card-header">
            <i class="iconfont icon-guidang" />
            <span>本页归档共 {{ totalCount }} 篇文章</span>
          </div>
          <p v-if="listing.error" role="alert">
            {{ listing.error }}
            <button @click="listing.reload">重试</button>
          </p>
          <div v-if="loading" class="placeholder">
            <div v-for="i in 4" :key="i" class="sk-line" />
          </div>
          <div v-else class="timeline">
            <div v-for="g in yearGroups" :key="g.year" class="year-group">
              <h2 class="year-label">
                <span class="year-dot" />
                {{ g.year }}
              </h2>
              <ul class="year-list">
                <li
                  v-for="a in g.items"
                  :key="a.month"
                  class="archive-item"
                  @click="goMonth(a.month)"
                  role="link"
                  tabindex="0"
                  @keydown.enter="goMonth(a.month)"
                >
                  <span class="item-dot" />
                  <span class="item-date">{{ a.month.slice(5) }} 月</span>
                  <span class="item-title">{{ a.articleCount }} 篇文章</span>
                </li>
              </ul>
            </div>
          </div>
          <p v-if="!loading && !listing.error && !yearGroups.length" class="empty">暂无归档</p>
          <ListPager v-if="listing.data" :data="listing.data" @change="changePage" />
        </div>
      </div>
      <SidebarCard />
    </div>
  </div>
</template>

<style scoped>
.archive-page {
  width: 100%;
}
.archive-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.archive-main {
  flex: 1;
  min-width: 0;
}

.content-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid #ebeef5;
  padding: 24px 28px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #909399;
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid #ebeef5;
}
.card-header .iconfont {
  font-size: 16px;
  color: #606266;
}

.placeholder {
  padding: 20px 0;
}
.sk-line {
  height: 14px;
  background: #ebeef5;
  border-radius: 4px;
  margin-bottom: 12px;
  width: 60%;
}

.timeline {
  position: relative;
  padding-left: 20px;
  border-left: 2px solid #e4e7ed;
}
.year-group {
  margin-bottom: 24px;
}
.year-group:last-child {
  margin-bottom: 0;
}
.year-label {
  font-family: var(--blog-serif);
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
}
.year-dot {
  position: absolute;
  left: -27px;
  width: 12px;
  height: 12px;
  background: #303133;
  border-radius: 50%;
  border: 2px solid #fff;
}
.year-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.archive-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  cursor: pointer;
  position: relative;
  transition: background 0.15s;
  border-radius: 6px;
  padding-left: 4px;
}
.archive-item:hover {
  background: #f5f7fa;
}
.archive-item:hover .item-title {
  color: #303133;
}
.item-dot {
  position: absolute;
  left: -24px;
  width: 6px;
  height: 6px;
  background: #c0c4cc;
  border-radius: 50%;
}
.archive-item:hover .item-dot {
  background: #303133;
}
.item-date {
  flex-shrink: 0;
  font-size: 13px;
  color: #909399;
  font-family: var(--blog-serif);
  font-variant-numeric: tabular-nums;
  min-width: 48px;
}
.item-title {
  font-size: 14px;
  color: #606266;
  transition: color 0.15s;
}

.empty {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}

@media (max-width: 1220px) {
  .archive-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(236px, 280px);
    align-items: start;
  }
}

@media (max-width: 960px) {
  .archive-layout {
    flex-direction: column;
    display: flex;
  }
  .archive-main {
    width: 100%;
  }
}
@media (max-width: 600px) {
  .content-card {
    padding: 16px;
  }
}
</style>
