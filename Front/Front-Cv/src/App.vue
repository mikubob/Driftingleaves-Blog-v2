<script setup>
import { createOnlineConnection } from '../../shared/online.mjs'
import { apiRoot, getOnlineTicket } from './api.js'
import { getFingerprint } from './tracking.js'
import RequestFailure from './components/RequestFailure.vue'
import { setRequestFailureHandler } from '../../shared/requestFailure.mjs'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { getProfile, getSocialLinks, getExperiences, getSkills } from './api.js'
import CvIcon from './components/CvIcon.vue'
import ExperienceSection from './components/ExperienceSection.vue'

const connection = createOnlineConnection({
  apiRoot, requestTicket: getOnlineTicket,
  payload: () => ({ fingerprint: getFingerprint(), siteCode: 'cv', pagePath: window.location.pathname }),
})
onMounted(connection.start)
onBeforeUnmount(connection.stop)

const resources = reactive({
  profile: { data: null, loading: true, error: '' },
  socials: { data: [], loading: true, error: '' },
  experiences: { data: [], loading: true, error: '' },
  skills: { data: [], loading: true, error: '' },
})
const loaders = { profile: getProfile, socials: getSocialLinks, experiences: getExperiences, skills: getSkills }
const labels = { profile: '个人资料', socials: '社交链接', experiences: '简历经历', skills: '专业技能' }
const controller = new AbortController()
const theme = reactive({ dark: false })
const colorScheme = window.matchMedia('(prefers-color-scheme: dark)')

const experienceGroups = computed(() => [
  { id: 'education', title: '教育经历', icon: 'education', type: 0 },
  { id: 'work', title: '实习 & 工作经历', icon: 'work', type: 1 },
  { id: 'projects', title: '项目经历', icon: 'project', type: 2 },
].map((group) => ({
  ...group,
  items: resources.experiences.data.filter((item) => item.experienceType === group.type),
})).filter((group) => group.items.length > 0))

const hasContent = computed(() => resources.profile.data !== null
  || resources.experiences.data.length > 0 || resources.skills.data.length > 0)
const canPrint = computed(() => hasContent.value
  && Object.values(resources).every((resource) => !resource.loading && !resource.error))

/** 每个数据区独立加载与重试，部分失败不会隐藏其他已成功内容。 */
async function loadResource(name) {
  const resource = resources[name]
  resource.loading = true
  resource.error = ''

  try {
    resource.data = await loaders[name](controller.signal)
    if (name === 'profile' && resource.data && !requestFailure.value) {
      document.title = `${resource.data.nickname} · 在线简历`
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      resource.error = error.message
    }
  } finally {
    resource.loading = false
  }
}

function applyTheme(dark) {
  theme.dark = dark
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

function followSystemTheme(event) {
  applyTheme(event.matches)
}

function printResume() {
  window.print()
}

onMounted(() => {
  applyTheme(colorScheme.matches)
  colorScheme.addEventListener('change', followSystemTheme)
  for (const name of Object.keys(loaders)) {
    loadResource(name)
  }
})

onBeforeUnmount(() => {
  controller.abort()
  colorScheme.removeEventListener('change', followSystemTheme)
})
const requestFailure = ref('')
setRequestFailureHandler((error) => {
  if (requestFailure.value) {
    return
  }
  requestFailure.value = error.message
  document.title = '404 - 页面不存在'
})
onBeforeUnmount(() => setRequestFailureHandler())
</script>

<template>
  <RequestFailure v-if="requestFailure" :message="requestFailure" />
  <div v-else>
    <a class="skip-link no-print" href="#resume">跳至简历内容</a>
    <div class="toolbar no-print" aria-label="简历工具">
      <button type="button" class="icon-button" :aria-label="theme.dark ? '切换浅色主题' : '切换深色主题'"
        :title="theme.dark ? '切换浅色主题' : '切换深色主题'" @click="applyTheme(!theme.dark)">
        <CvIcon :name="theme.dark ? 'sun' : 'moon'" />
      </button>
      <button type="button" class="icon-button" aria-label="打印 / 导出 PDF" title="打印 / 导出 PDF"
        :disabled="!canPrint" @click="printResume"><CvIcon name="print" /></button>
    </div>

    <main id="resume" class="cv-page">
      <header class="hero">
        <p class="eyebrow">CURRICULUM VITAE</p>
        <template v-if="resources.profile.data">
          <img v-if="resources.profile.data.avatar" :src="resources.profile.data.avatar" alt="个人头像" class="avatar" />
          <h1>{{ resources.profile.data.nickname }}</h1>
          <p v-if="resources.profile.data.tagline" class="tagline">{{ resources.profile.data.tagline }}</p>
          <p v-if="resources.profile.data.description" class="description">{{ resources.profile.data.description }}</p>
          <ul class="contact-list">
            <li v-if="resources.profile.data.location"><CvIcon name="location" />{{ resources.profile.data.location }}</li>
            <li v-if="resources.profile.data.publicEmail">
              <a :href="`mailto:${encodeURIComponent(resources.profile.data.publicEmail)}`"><CvIcon name="mail" />{{ resources.profile.data.publicEmail }}</a>
            </li>
            <li v-if="resources.profile.data.githubUrl">
              <a :href="resources.profile.data.githubUrl" target="_blank" rel="noopener noreferrer"><CvIcon name="github" />GitHub</a>
            </li>
            <li v-if="resources.profile.data.website">
              <a :href="resources.profile.data.website" target="_blank" rel="noopener noreferrer"><CvIcon name="globe" />个人网站</a>
            </li>
          </ul>
        </template>
        <template v-else>
          <h1>在线简历</h1>
          <p v-if="!resources.profile.loading && !resources.profile.error" class="muted">个人资料尚未公开</p>
        </template>
        <ul v-if="resources.socials.data.length" class="contact-list social-list" aria-label="社交链接">
          <li v-for="social in resources.socials.data" :key="social.socialId">
            <a :href="social.url" target="_blank" rel="noopener noreferrer">
              <img v-if="/^https?:/i.test(social.icon || '')" :src="social.icon" alt="" />
              <CvIcon v-else :name="social.icon || 'link'" />{{ social.name }}
            </a>
          </li>
        </ul>
      </header>

      <nav v-if="experienceGroups.length || resources.skills.data.length" class="section-nav no-print" aria-label="简历章节">
        <a v-for="group in experienceGroups" :key="group.id" :href="`#${group.id}`">{{ group.title }}</a>
        <a v-if="resources.skills.data.length" href="#skills">专业技能</a>
      </nav>

      <div class="resource-status" aria-live="polite">
        <template v-for="(resource, name) in resources" :key="name">
          <p v-if="resource.loading" class="loading-message" role="status"><span class="spinner" aria-hidden="true"></span>正在加载{{ labels[name] }}…</p>
          <div v-else-if="resource.error" class="error-message" role="alert">
            <p>{{ labels[name] }}加载失败：{{ resource.error }}</p>
            <button type="button" @click="loadResource(name)">重新加载{{ labels[name] }}</button>
          </div>
        </template>
      </div>

      <ExperienceSection v-for="group in experienceGroups" :id="group.id" :key="group.id"
        :title="group.title" :icon="group.icon" :items="group.items" />
      <p v-if="!resources.experiences.loading && !resources.experiences.error && !resources.experiences.data.length"
        class="empty-message">暂无公开的教育、工作或项目经历。</p>

      <section id="skills" class="resume-section" aria-labelledby="skills-title">
        <h2 id="skills-title" class="section-title"><CvIcon name="code" />专业技能</h2>
        <ul v-if="resources.skills.data.length" class="skill-list">
          <li v-for="skill in resources.skills.data" :key="skill.skillId" class="skill-row">
            <img v-if="/^https?:/i.test(skill.icon || '')" :src="skill.icon" alt="" class="skill-logo" loading="lazy" />
            <div v-else-if="skill.icon" class="skill-logo skill-initial"><CvIcon :name="skill.icon" /></div>
            <div v-else class="skill-logo skill-initial" aria-hidden="true">{{ Array.from(skill.name)[0] }}</div>
            <div class="skill-info"><h3>{{ skill.name }}</h3><p v-if="skill.description">{{ skill.description }}</p></div>
          </li>
        </ul>
        <p v-else-if="!resources.skills.loading && !resources.skills.error" class="empty-message">暂无公开的技能。</p>
      </section>

      <footer class="page-footer"><span>Drifting Leaves</span><span>在线简历</span></footer>
    </main>
  </div>
</template>
