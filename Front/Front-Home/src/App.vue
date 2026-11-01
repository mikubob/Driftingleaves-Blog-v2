<script setup>
import { createOnlineConnection } from '../../shared/online.mjs'
import { apiRoot, getOnlineTicket } from './api.js'
import RequestFailure from './components/RequestFailure.vue'
import { setRequestFailureHandler } from '../../shared/requestFailure.mjs'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watchEffect } from 'vue'
import { getProfile, getSocialLinks, getSiteConfig, recordPageView } from './api.js'
import { createPageView, getFingerprint } from './tracking.js'
import HomeIcon from './components/HomeIcon.vue'

const requestFailure = ref(false)
const connection = createOnlineConnection({
  apiRoot, requestTicket: getOnlineTicket,
  payload: () => ({ fingerprint: getFingerprint(window), siteCode: 'home', pagePath: window.location.pathname }),
})
onMounted(connection.start)
onBeforeUnmount(connection.stop)

const resources = reactive({
  profile: { data: null, loading: true, error: '' },
  socials: { data: [], loading: true, error: '' },
  config: { data: null, loading: true, error: '' },
})
const loaders = { profile: getProfile, socials: getSocialLinks, config: getSiteConfig }
const controller = new AbortController()
const failedAvatar = ref('')
const failedIcons = reactive(new Set())
const currentYear = new Date().getFullYear()

const profile = computed(() => resources.profile.data)
const site = computed(() => resources.config.data)
const displayName = computed(() => profile.value?.nickname || site.value?.title || 'DriftingLeaves')
const copyrightYears = computed(() => {
  const startYear = site.value?.startDate?.slice(0, 4)
  if (startYear && Number(startYear) < currentYear) {
    return `${startYear}–${currentYear}`
  }
  return String(currentYear)
})

watchEffect(() => {
  if (requestFailure.value) {
    return
  }
  document.title = `${site.value?.title || displayName.value} | 个人主页`
  document.querySelector('meta[name="description"]').content =
    site.value?.description || profile.value?.description || 'DriftingLeaves · 个人主页'
})

/** 独立加载和重试对应区域，失败不会把其他公开内容一同清空。 */
async function loadResource(name) {
  const resource = resources[name]
  resource.loading = true
  resource.error = ''

  try {
    resource.data = await loaders[name](controller.signal)
  } catch (error) {
    if (error.name !== 'AbortError') {
      resource.error = error.message
    }
  } finally {
    resource.loading = false
  }
}

onMounted(async () => {
  await Promise.all(Object.keys(loaders).map(loadResource))
  if (controller.signal.aborted) {
    return
  }

  // 采集失败不阻塞公开阅读；重试内容请求不会重复写入浏览记录。
  try {
    const payload = createPageView(
      getFingerprint(window),
      window.location.pathname,
      document.title,
      document.referrer,
    )
    await recordPageView(payload, controller.signal)
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.warn('主页浏览统计未完成：', error.message)
    }
  }
})

onBeforeUnmount(() => controller.abort())
setRequestFailureHandler(() => {
  if (requestFailure.value) {
    return
  }
  requestFailure.value = true
  document.title = '404 - 页面不存在'
})
onBeforeUnmount(() => setRequestFailureHandler())
</script>

<template>
  <RequestFailure v-if="requestFailure" />
  <div v-else>
    <a class="skip-link" href="#home">跳至主页内容</a>
    <div class="home-container">
      <main id="home" class="card" tabindex="-1" aria-label="个人主页">
        <section class="profile" aria-label="个人介绍" :aria-busy="resources.profile.loading">
          <div class="avatar-container" :class="{ skeleton: resources.profile.loading }">
            <img v-if="profile?.avatar && failedAvatar !== profile.avatar"
              :src="profile.avatar" :alt="`${profile.nickname}的头像`" referrerpolicy="no-referrer"
              @error="failedAvatar = profile.avatar" />
            <span v-else-if="!resources.profile.loading" class="avatar-placeholder" aria-hidden="true">
              {{ Array.from(displayName)[0] }}
            </span>
          </div>

          <template v-if="resources.profile.loading">
            <h1 class="loading-title">正在加载主页</h1>
            <p class="tagline" role="status">很快就好，请稍候…</p>
          </template>
          <div v-else-if="resources.profile.error" class="feedback" role="status">
            <h1>{{ displayName }}</h1>
            <p>个人资料加载失败：{{ resources.profile.error }}</p>
            <button type="button" @click="loadResource('profile')">重新加载个人资料</button>
          </div>
          <template v-else-if="profile">
            <h1>{{ profile.nickname }}</h1>
            <p class="tagline">
              <span v-if="profile.tagline">{{ profile.tagline }}<span class="tagline-divider"> · </span></span>
              <span>欢迎来到我的主页</span>
            </p>
            <p v-if="profile.description" class="description">{{ profile.description }}</p>
            <p v-if="profile.location" class="location">
              <HomeIcon name="location" />{{ profile.location }}
            </p>
            <div v-if="profile.website || profile.githubUrl || profile.publicEmail" class="contacts" aria-label="公开联系方式">
              <a v-if="profile.website" :href="profile.website" target="_blank" rel="noopener noreferrer">
                <HomeIcon name="globe" />个人网站
              </a>
              <a v-if="profile.githubUrl" :href="profile.githubUrl" target="_blank" rel="noopener noreferrer">
                <HomeIcon name="github" />GitHub
              </a>
              <a v-if="profile.publicEmail" :href="`mailto:${encodeURIComponent(profile.publicEmail)}`">
                <HomeIcon name="mail" />{{ profile.publicEmail }}
              </a>
            </div>
          </template>
          <template v-else>
            <h1>{{ displayName }}</h1>
            <p class="tagline">欢迎来到我的主页</p>
            <p class="empty-state" role="status">主页资料暂未配置。</p>
          </template>
        </section>

        <section class="social-section" aria-label="社交链接" :aria-busy="resources.socials.loading">
          <p v-if="resources.socials.loading" class="empty-state" role="status">正在加载社交链接…</p>
          <div v-else-if="resources.socials.error" class="feedback" role="status">
            <p>社交链接加载失败：{{ resources.socials.error }}</p>
            <button type="button" @click="loadResource('socials')">重新加载社交链接</button>
          </div>
          <nav v-else-if="resources.socials.data.length" class="links" aria-label="站点与社交导航">
            <a v-for="(item, index) in resources.socials.data" :key="item.socialId"
              class="link" :href="item.url" target="_blank" rel="noopener noreferrer"
              :style="{ '--link-index': Math.min(index, 10) }">
              <img v-if="/^https?:\/\//i.test(item.icon || '') && !failedIcons.has(item.icon)"
                class="social-icon" :src="item.icon" alt="" loading="lazy" referrerpolicy="no-referrer"
                @error="failedIcons.add(item.icon)" />
              <HomeIcon v-else :name="item.icon || 'link'" />
              <span>{{ item.name }}</span>
            </a>
          </nav>
          <p v-else class="empty-state" role="status">暂无公开社交链接。</p>
        </section>

        <footer :aria-busy="resources.config.loading">
          <p v-if="resources.config.loading" class="config-status" role="status">正在加载站点信息…</p>
          <div v-else-if="resources.config.error" class="feedback config-status" role="status">
            <p>站点信息加载失败：{{ resources.config.error }}</p>
            <button type="button" @click="loadResource('config')">重新加载站点信息</button>
          </div>
          <div class="beian-info">
            <span v-if="site?.publicSecurityRecord">{{ site.publicSecurityRecord }}</span>
            <a v-if="site?.icp" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
              {{ site.icp }}
            </a>
            <span>© {{ copyrightYears }} {{ displayName }}.</span>
          </div>
        </footer>
      </main>
    </div>
  </div>
</template>
