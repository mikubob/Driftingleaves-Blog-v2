<script setup>
import { createOnlineConnection } from './shared/online.mjs'
import { apiRoot, getOnlineTicket, getProfile, getSocialLinks, getExperiences, getSkills, recordPageView } from './api.js'
import { createPageView, getFingerprint } from './tracking.js'
import RequestFailure from './shared/RequestFailure.vue'
import { setRequestFailureHandler } from './shared/requestFailure.mjs'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import CvIcon from './components/CvIcon.vue'
import ExperienceSection from './components/ExperienceSection.vue'
import { vReveal } from './reveal.js'

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
const failedAvatar = ref('')
const failedSocialIcons = reactive(new Set())
const failedSkillIcons = reactive(new Set())
const isImageIcon = (icon) => /^https?:/i.test(icon || '')
const isMailto = (url) => /^mailto:/i.test(url || '')

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

onMounted(async () => {
  applyTheme(colorScheme.matches)
  colorScheme.addEventListener('change', followSystemTheme)
  await Promise.all(Object.keys(loaders).map(loadResource))
  if (controller.signal.aborted) {
    return
  }

  // 采集失败不阻塞简历阅读；手动重试区块加载不会重复写入浏览记录。
  try {
    const payload = createPageView(
      getFingerprint(),
      window.location.pathname,
      document.title,
      document.referrer,
    )
    await recordPageView(payload, controller.signal)
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.warn('简历浏览统计未完成：', error.message)
    }
  }
})

onBeforeUnmount(() => {
  controller.abort()
  colorScheme.removeEventListener('change', followSystemTheme)
})
const requestFailure = ref(null)
setRequestFailureHandler((error) => {
  requestFailure.value ??= error
  connection.stop()
})
onBeforeUnmount(() => setRequestFailureHandler())
</script>

<template>
  <RequestFailure v-if="requestFailure" :error="requestFailure" />
  <div v-else>
    <a class="skip-link no-print" href="#resume">跳至简历内容</a>
    <div class="toolbar no-print" role="toolbar" aria-label="简历工具">
      <button type="button" class="icon-button" :aria-label="theme.dark ? '切换浅色主题' : '切换深色主题'"
        :title="theme.dark ? '切换浅色主题' : '切换深色主题'" @click="applyTheme(!theme.dark)">
        <CvIcon :name="theme.dark ? 'sun' : 'moon'" />
      </button>
      <button type="button" class="icon-button" aria-label="打印 / 导出 PDF" title="打印 / 导出 PDF"
        :disabled="!canPrint" @click="printResume"><CvIcon name="print" /></button>
    </div>

    <main id="resume" class="cv-page" tabindex="-1">
      <header class="hero">
        <p class="eyebrow">CURRICULUM VITAE</p>
        <template v-if="resources.profile.data">
          <img
            v-if="resources.profile.data.avatar && failedAvatar !== resources.profile.data.avatar"
            :src="resources.profile.data.avatar"
            alt="个人头像"
            class="avatar"
            referrerpolicy="no-referrer"
            @error="failedAvatar = resources.profile.data.avatar"
          />
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
        <!-- 个人资料加载中显示骨架屏，避免内容突然出现。 -->
        <div v-else-if="resources.profile.loading" class="hero-skeleton no-print" aria-hidden="true">
          <span class="skeleton skeleton-avatar"></span>
          <span class="skeleton skeleton-title"></span>
          <span class="skeleton skeleton-text"></span>
          <span class="skeleton skeleton-text short"></span>
        </div>
        <template v-else>
          <h1>在线简历</h1>
          <p v-if="!resources.profile.error" class="muted">个人资料尚未公开</p>
        </template>
        <ul v-if="resources.socials.data.length" class="contact-list social-list" aria-label="社交链接">
          <li v-for="(social, index) in resources.socials.data" :key="social.socialId"
            :style="{ '--reveal-index': Math.min(index, 10) }">
            <a
              :href="social.url"
              :target="isMailto(social.url) ? undefined : '_blank'"
              :rel="isMailto(social.url) ? undefined : 'noopener noreferrer'"
            >
              <img
                v-if="isImageIcon(social.icon) && !failedSocialIcons.has(social.icon)"
                :src="social.icon"
                alt=""
                referrerpolicy="no-referrer"
                @error="failedSocialIcons.add(social.icon)"
              />
              <CvIcon v-else :name="isImageIcon(social.icon) ? 'link' : (social.icon || 'link')" />{{ social.name }}
            </a>
          </li>
        </ul>
        <div v-else-if="resources.socials.loading" class="contact-skeleton no-print" aria-hidden="true">
          <span v-for="index in 4" :key="index" class="skeleton skeleton-pill"></span>
        </div>
      </header>

      <nav v-if="experienceGroups.length || resources.skills.data.length" class="section-nav no-print" aria-label="简历章节">
        <a v-for="group in experienceGroups" :key="group.id" :href="`#${group.id}`">{{ group.title }}</a>
        <a v-if="resources.skills.data.length" href="#skills">专业技能</a>
      </nav>

      <div class="resource-status" aria-live="polite">
        <template v-for="(resource, name) in resources" :key="name">
          <!-- 加载进度由骨架屏展示，这里只给读屏软件播报。 -->
          <p v-if="resource.loading" class="sr-only" role="status">正在加载{{ labels[name] }}…</p>
          <div v-else-if="resource.error" class="error-message" role="alert">
            <p>{{ labels[name] }}加载失败：{{ resource.error }}</p>
            <button type="button" @click="loadResource(name)">重新加载{{ labels[name] }}</button>
          </div>
        </template>
      </div>

      <div v-if="resources.experiences.loading && !resources.experiences.data.length"
        class="resume-section skeleton-group no-print" aria-hidden="true">
        <span class="skeleton skeleton-heading"></span>
        <ol class="timeline">
          <li v-for="index in 2" :key="index" class="timeline-item">
            <div class="experience-card skeleton-card">
              <span class="skeleton skeleton-logo"></span>
              <div class="skeleton-lines">
                <span class="skeleton skeleton-line"></span>
                <span class="skeleton skeleton-line short"></span>
              </div>
            </div>
          </li>
        </ol>
      </div>

      <ExperienceSection v-for="group in experienceGroups" :id="group.id" :key="group.id"
        :title="group.title" :icon="group.icon" :items="group.items" />
      <p v-if="!resources.experiences.loading && !resources.experiences.error && !resources.experiences.data.length"
        v-reveal class="empty-message">暂无公开的教育、工作或项目经历。</p>

      <section id="skills" class="resume-section" aria-labelledby="skills-title">
        <h2 id="skills-title" v-reveal class="section-title"><CvIcon name="code" />专业技能</h2>
        <ul v-if="resources.skills.data.length" class="skill-list">
          <li v-for="(skill, index) in resources.skills.data" :key="skill.skillId" v-reveal class="skill-row"
            :style="{ '--reveal-index': Math.min(index, 6) }">
            <img
              v-if="isImageIcon(skill.icon) && !failedSkillIcons.has(skill.icon)"
              :src="skill.icon"
              alt=""
              class="skill-logo"
              loading="lazy"
              referrerpolicy="no-referrer"
              @error="failedSkillIcons.add(skill.icon)"
            />
            <div v-else-if="skill.icon && !isImageIcon(skill.icon)" class="skill-logo skill-initial"><CvIcon :name="skill.icon" /></div>
            <div v-else class="skill-logo skill-initial" aria-hidden="true">{{ Array.from(skill.name)[0] }}</div>
            <div class="skill-info"><h3>{{ skill.name }}</h3><p v-if="skill.description">{{ skill.description }}</p></div>
          </li>
        </ul>
        <ul v-else-if="resources.skills.loading" class="skill-list skeleton-group no-print" aria-hidden="true">
          <li v-for="index in 3" :key="index" class="skill-row skeleton-card">
            <span class="skeleton skeleton-logo"></span>
            <div class="skeleton-lines">
              <span class="skeleton skeleton-line"></span>
              <span class="skeleton skeleton-line short"></span>
            </div>
          </li>
        </ul>
        <p v-else-if="!resources.skills.error" v-reveal class="empty-message">暂无公开的技能。</p>
      </section>

      <footer v-reveal class="page-footer"><span>Drifting Leaves</span><span>在线简历</span></footer>
    </main>
  </div>
</template>
