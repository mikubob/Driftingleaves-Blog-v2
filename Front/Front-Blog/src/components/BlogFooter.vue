<script setup>
import { computed } from 'vue'
import { useBlogStore } from '@/stores'
import { apiRoot } from '@/api.js'
const blogStore = useBlogStore()
const icpBeian = computed(() => blogStore.site.icp || '')
const gonganBeian = computed(() => blogStore.site.publicSecurityRecord || '')
const currentYear = new Date().getFullYear()
const startYear = computed(() => {
  const year = blogStore.site.startDate?.slice(0, 4)
  return year && Number(year) < currentYear ? year : ''
})
const sitemapUrl = apiRoot + '/blog/sitemap.xml'
const rssFeedUrl = apiRoot + '/blog/rss.xml'
</script>

<template>
  <footer class="site-footer">
    <div class="footer-inner">
      <div v-if="gonganBeian || icpBeian" class="footer-beian">
        <span v-if="gonganBeian">{{ gonganBeian }}</span>
        <span v-if="gonganBeian && icpBeian" class="divider">|</span>
        <a
          v-if="icpBeian"
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          >{{ icpBeian }}</a
        >
      </div>
      <div class="footer-copy">
        &copy; {{ startYear ? `${startYear}-` : '' }}{{ currentYear }} {{ blogStore.personalInfo.nickname || blogStore.site.title || 'DriftingLeaves' }}. All
        rights reserved.
      </div>
      <div class="footer-links">
        <a :href="sitemapUrl" target="_blank" rel="noopener">Sitemap</a>
        <span class="divider">|</span>
        <a :href="rssFeedUrl" target="_blank" rel="noopener">RSS</a>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  background: var(--blog-card, #fff);
  border-top: 1px solid var(--blog-border-light, #ebeef5);
  color: var(--blog-text3, #909399);
  margin-top: auto;
}
.footer-inner {
  text-align: center;
  padding: 14px 24px;
}
.footer-beian {
  font-size: 12px;
  color: var(--blog-text3, #b0b0b0);
  margin-bottom: 4px;
}
.footer-beian a {
  color: var(--blog-text3, #b0b0b0);
  text-decoration: none;
  border-bottom: 1px dotted var(--blog-border, #dcdfe6);
  transition: color 0.2s;
}
.footer-beian a:hover {
  color: var(--blog-text2, #606266);
}
.divider {
  margin: 0 8px;
  opacity: 0.4;
}
.footer-copy {
  font-size: 12px;
  color: var(--blog-text3, #c0c4cc);
}
.footer-links {
  font-size: 12px;
  margin-top: 4px;
  color: var(--blog-text3, #c0c4cc);
}
.footer-links a {
  color: var(--blog-text3, #b0b0b0);
  text-decoration: none;
  border-bottom: 1px dotted var(--blog-border, #dcdfe6);
  transition: color 0.2s;
}
.footer-links a:hover {
  color: var(--blog-text2, #606266);
}
</style>
