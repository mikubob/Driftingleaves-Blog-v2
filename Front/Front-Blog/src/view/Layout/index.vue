<script setup>
import { computed, onMounted, onUnmounted, provide, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BlogHeader from '@/components/BlogHeader.vue'
import BlogFooter from '@/components/BlogFooter.vue'
import HeroBanner from '@/components/HeroBanner.vue'
import { useBlogStore, useThemeStore } from '@/stores'
import { createOnlineConnection } from '../../../../shared/online.mjs'
import { apiRoot, getOnlineTicket } from '@/api.js'
import { getFingerprint } from '@/tracking.js'
import { clearSession, onSessionChange } from '@/session.js'
import { recordPage } from '@/tracking.js'
const route = useRoute()
const online = ref({ status: 'connecting' })
provide('online', online)
const connection = createOnlineConnection({
  apiRoot, requestTicket: getOnlineTicket,
  payload: () => ({ fingerprint: getFingerprint(), siteCode: 'blog',
    pagePath: route.meta.sensitive ? new URL(import.meta.env.BASE_URL, window.location.href).pathname : window.location.pathname }),
  onState: (state) => { online.value = state }, onSessionInvalid: clearSession,
})
const unsubscribe = onSessionChange(connection.restart)
onMounted(connection.start)
onUnmounted(() => {
  unsubscribe()
  connection.stop()
})
const blogStore = useBlogStore()
const themeStore = useThemeStore()
const articleCover = ref('')
const articleTitle = ref('')
const articleMeta = ref('')
const showBackTop = ref(false)
provide('setHero', { articleCover, articleTitle, articleMeta })
const pageTitle = computed(() => articleTitle.value || route.meta.title || '博客')
watch(() => route.path, () => {
  articleCover.value = ''
  articleTitle.value = ''
  articleMeta.value = ''
  if (!route.meta.sensitive && route.name !== 'article') {
    recordPage(route.path, route.meta.title || '博客')
  }
}, { immediate: true })
watch([pageTitle, () => blogStore.site.title], () => {
  document.title = pageTitle.value + ' - ' + (blogStore.site.title || 'DriftingLeaves')
}, { immediate: true })
const onScroll = () => {
  showBackTop.value = window.scrollY > 400
}
const scrollToTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })
onMounted(() => {
  themeStore.applyTheme()
  blogStore.init()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <div class="blog-layout">
    <BlogHeader />
    <HeroBanner :cover-image="articleCover" :title="articleTitle" :meta="articleMeta" />
    <div v-if="blogStore.loadError" role="alert">
      {{ blogStore.loadError }}
      <button type="button" @click="blogStore.init">重试</button>
    </div>
    <main class="blog-main">
      <div class="main-inner">
        <router-view v-slot="{ Component, route: viewRoute }">
          <transition name="page-fade" mode="out-in" appear>
            <div :key="viewRoute.path" :class="{ 'v2-page': viewRoute.meta.account }">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </div>
    </main>
    <BlogFooter />
    <!-- 回到顶部 -->
    <transition name="backtop-fade">
      <button v-show="showBackTop" class="back-to-top" title="回到顶部" @click="scrollToTop">
        <svg
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <polyline points="18 15 12 9 6 15" />
        </svg>
      </button>
    </transition>
  </div>
</template>

<style scoped>
.blog-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.blog-main {
  flex: 1;
  width: 100%;
  background: var(--blog-bg);
}
.main-inner {
  max-width: 1500px;
  margin: 0 auto;
  padding: 32px clamp(16px, 2.4vw, 32px);
}

/* 页面切换过渡动画 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition:
    opacity 0.3s ease,
    transform 0.3s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 回到顶部 */
.back-to-top {
  position: fixed;
  right: 28px;
  bottom: 36px;
  z-index: 99;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid var(--blog-border);
  background: var(--blog-card);
  color: var(--blog-text2);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    color 0.2s,
    box-shadow 0.2s,
    border-color 0.2s;
}
.back-to-top:hover {
  color: var(--blog-text);
  border-color: var(--blog-text3);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}
.backtop-fade-enter-active,
.backtop-fade-leave-active {
  transition:
    opacity 0.25s,
    transform 0.25s;
}
.backtop-fade-enter-from,
.backtop-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

@media (max-width: 768px) {
  .main-inner {
    padding: 20px 16px;
  }
  .back-to-top {
    right: 16px;
    bottom: 20px;
    width: 38px;
    height: 38px;
  }
}
</style>
