<script setup>
import CvIcon from './CvIcon.vue'

/** 同一时间线用于教育、工作和项目；正文仅接收后端清理后的 htmlContent。 */
defineProps({
  id: { type: String, required: true },
  title: { type: String, required: true },
  icon: { type: String, required: true },
  items: { type: Array, required: true },
})

function formatDate(date) {
  if (date === null) {
    return '至今'
  }
  return date.slice(0, 7).replace('-', '.')
}
</script>

<template>
  <section :id="id" class="resume-section" :aria-labelledby="`${id}-title`">
    <h2 :id="`${id}-title`" class="section-title"><CvIcon :name="icon" />{{ title }}</h2>
    <ol class="timeline">
      <li v-for="item in items" :key="item.experienceId" class="timeline-item">
        <article class="experience-card">
          <div class="experience-heading">
            <img v-if="item.logoUrl" :src="item.logoUrl" alt="" class="experience-logo" loading="lazy" />
            <div class="experience-info">
              <h3>{{ item.title }}</h3>
              <p v-if="item.subtitle" class="subtitle">{{ item.subtitle }}</p>
            </div>
            <p class="date-range">
              <time :datetime="item.startDate">{{ formatDate(item.startDate) }}</time>
              <span aria-hidden="true"> — </span><span class="sr-only"> 至 </span>
              <time v-if="item.endDate" :datetime="item.endDate">{{ formatDate(item.endDate) }}</time>
              <span v-else>至今</span>
            </p>
          </div>
          <!-- htmlContent 的安全清理由后端完成，普通文本不走 HTML 渲染。 -->
          <div v-if="item.htmlContent" class="experience-content" v-html="item.htmlContent"></div>
        </article>
      </li>
    </ol>
  </section>
</template>
