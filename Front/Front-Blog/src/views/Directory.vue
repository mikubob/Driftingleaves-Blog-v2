<script setup>
import { useRoute, useRouter } from 'vue-router'
import { getPage, pageNumber } from '../api.js'
import { useResource } from '../composables/useResource.js'
import { formatDate } from '../format.js'
import ResourceState from '../components/ResourceState.vue'
import ListPager from '../components/ListPager.vue'
const route = useRoute()
const router = useRouter()
const type = route.meta.directory
const listing = useResource(() => route.query.page, (signal) => getPage(`/blog/${type}`, {
  page: pageNumber(route.query.page), pageSize: 20,
}, signal))

function changePage(page) {
  router.push({ query: { page: String(page) } })
}
</script>

<template>
  <section class="panel directory-panel">
    <h2>{{ route.meta.title }}</h2>
    <ResourceState :resource="listing" />
    <template v-if="listing.data">
      <div v-if="type === 'authors'" class="directory-grid">
        <router-link
          v-for="item in listing.data.records"
          :key="item.userId"
          :to="`/author/${item.userId}`"
          class="friend-card"
        >
          <img v-if="item.avatar" :src="item.avatar" alt="" loading="lazy" />
          <span v-else class="friend-avatar">{{ item.nickname?.slice(0, 1) }}</span>
          <div>
            <h3>{{ item.nickname }}</h3>
            <p>{{ item.articleCount }} 篇文章 · {{ formatDate(item.lastPublishTime) }}</p>
            <p>{{ item.intro }}</p>
          </div>
        </router-link>
      </div>
      <div v-else class="directory-grid">
        <router-link
          v-for="item in listing.data.records"
          :key="item.slug"
          :to="`/${type === 'categories' ? 'category' : 'tag'}/${item.slug}`"
          class="taxonomy-card"
        >
          <h3>
            {{ type === 'categories' ? item.categoryName : `# ${item.tagName}` }}
            <span>↗</span>
          </h3>
          <p v-if="item.description" class="muted">{{ item.description }}</p>
        </router-link>
      </div>
      <p v-if="!listing.data.records.length" class="empty-state">暂无{{ route.meta.title }}</p>
      <ListPager :data="listing.data" @change="changePage" />
    </template>
  </section>
</template>
