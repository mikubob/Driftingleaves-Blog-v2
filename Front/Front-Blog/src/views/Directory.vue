<script setup>
import { useRoute, useRouter } from 'vue-router'
import { getPage, pageNumber } from '../api.js'
import { useResource } from '../composables/useResource.js'
import { formatDate } from '../format.js'
import ResourceState from '../components/ResourceState.vue'
import ListPager from '../components/ListPager.vue'
import BlogIcon from '../components/BlogIcon.vue'
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
    <p class="eyebrow">EXPLORE THE JOURNAL</p>
    <h2>{{ route.meta.title }}</h2>
    <p class="muted">{{ type === 'links' ? '互联网很大，去朋友的世界里走走。' : '沿着不同的线索，翻阅过去的记录。' }}</p>
    <ResourceState :resource="listing" />
    <template v-if="listing.data">
      <div v-if="type === 'archives'" class="archive-list">
        <router-link v-for="item in listing.data.records" :key="item.month" :to="`/archive/${item.month}`">
          <BlogIcon name="calendar" />
          <strong>{{ item.month }}</strong>
          <span>{{ item.articleCount }} 篇记录</span>
          <BlogIcon name="arrow" />
        </router-link>
      </div>
      <div v-else-if="type === 'links'" class="directory-grid">
        <a
          v-for="item in listing.data.records"
          :key="item.linkId"
          :href="item.url"
          target="_blank"
          rel="noopener noreferrer"
          class="friend-card"
        >
          <img v-if="item.avatar" :src="item.avatar" alt="" loading="lazy" />
          <span v-else class="friend-avatar">
            <BlogIcon name="link" />
          </span>
          <div>
            <h3>{{ item.linkName }} ↗</h3>
            <p>{{ item.description || '去看看这个小站。' }}</p>
          </div>
        </a>
      </div>
      <div v-else-if="type === 'authors'" class="directory-grid">
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
          <span class="muted">{{ type === 'categories' ? 'COLLECTION' : 'TAG' }}</span>
          <h3>
            {{ type === 'categories' ? item.categoryName : `# ${item.tagName}` }}
            <span>↗</span>
          </h3>
          <p v-if="item.description" class="muted">{{ item.description }}</p>
        </router-link>
      </div>
      <p v-if="!listing.data.records.length" class="empty-state">这里还没有内容，过些日子再来看看。</p>
      <ListPager :data="listing.data" @change="changePage" />
    </template>
  </section>
</template>
