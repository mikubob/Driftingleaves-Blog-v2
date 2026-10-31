<script setup>
import { formatDateTime } from '@/format.js'
defineProps({
  article: { type: Object, required: true }
})
const fmtDate = formatDateTime
</script>

<template>
  <router-link :to="`/articles/${article.slug}`" class="article-card">
    <div v-if="article.coverImage" class="card-cover">
      <img :src="article.coverImage" :alt="article.title" loading="lazy" />
    </div>
    <div class="card-body">
      <span v-if="article.isTop" class="card-pin">
        <i class="iconfont icon-zhiding" />
      </span>
      <div class="card-top">
        <span v-if="article.categoryName" class="card-category">
          <i class="iconfont icon-folder" /> {{ article.categoryName }}
        </span>
        <span class="card-date"
          ><i class="iconfont icon-time" />
          {{ fmtDate(article.publishTime) }}</span
        >
      </div>
      <h3 class="card-title">{{ article.title }}</h3>
      <p v-if="article.summary" class="card-summary">{{ article.summary }}</p>
      <div class="card-tags" v-if="article.tagNames?.length">
        <span v-for="t in article.tagNames" :key="t" class="card-tag"
          ># {{ t }}</span
        >
      </div>
      <div class="card-meta">
        <span
          ><i class="iconfont icon-eye" /> {{ article.viewCount ?? 0 }}</span
        >
        <span
          ><i class="iconfont icon-pinglun" />
          {{ article.commentCount ?? 0 }}</span
        >
        <span class="card-like">
          <svg
            viewBox="0 0 24 24"
            class="card-like-icon"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M17.5,1.917a6.4,6.4,0,0,0-5.5,3.3,6.4,6.4,0,0,0-5.5-3.3A6.8,6.8,0,0,0,0,8.967c0,4.547,4.786,9.513,8.8,12.88a4.974,4.974,0,0,0,6.4,0C19.214,18.48,24,13.514,24,8.967A6.8,6.8,0,0,0,17.5,1.917Zm-3.585,18.4a2.973,2.973,0,0,1-3.83,0C4.947,16.006,2,11.87,2,8.967a4.8,4.8,0,0,1,4.5-5.05A4.8,4.8,0,0,1,11,8.967a1,1,0,0,0,2,0,4.8,4.8,0,0,1,4.5-5.05A4.8,4.8,0,0,1,22,8.967C22,11.87,19.053,16.006,13.915,20.313Z"
            ></path>
          </svg>
          {{ article.likeCount ?? 0 }}
        </span>
        <span v-if="article.wordCount"
          ><i class="iconfont icon-guidang" /> {{ article.wordCount }} 字</span
        >
        <span v-if="article.readingTime"
          ><i class="iconfont icon-time" /> {{ article.readingTime }} 分钟</span
        >
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.article-card {
  display: flex;
  gap: 0;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  text-decoration: none;
  color: inherit;
  transition:
    box-shadow 0.2s,
    transform 0.2s;
  margin-bottom: 16px;
}
.article-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}
.card-cover {
  flex-shrink: 0;
  width: 230px;
  min-height: 170px;
  overflow: hidden;
}
.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s;
}
.article-card:hover .card-cover img {
  transform: scale(1.05);
}

.card-body {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
}
.card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #909399;
}
.card-top .iconfont {
  font-size: 12px;
  margin-right: 2px;
}
.card-category {
  color: #606266;
  font-weight: 500;
}
.card-pin {
  position: absolute;
  top: 10px;
  right: 12px;
  font-weight: 600;
  font-size: 25px;
  display: flex;
  align-items: center;
  gap: 3px;
}
.card-pin .iconfont {
  font-size: 25px;
}
.card-title {
  font-family: var(--blog-serif);
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 6px;
  line-height: 1.4;
  color: #303133;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-summary {
  font-size: 13.5px;
  color: #606266;
  margin: 0 0 8px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.card-tag {
  font-size: 11px;
  color: #909399;
  padding: 1px 6px;
  background: #f5f7fa;
  border-radius: 3px;
}
.card-meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: #909399;
  margin-top: auto;
}
.card-meta .iconfont {
  font-size: 12px;
  margin-right: 3px;
}
.card-like {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.card-like-icon {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: currentColor;
  stroke-width: 0.85px;
  flex-shrink: 0;
}

@media (max-width: 600px) {
  .article-card {
    flex-direction: column;
  }
  .card-cover {
    width: 100%;
    min-height: 180px;
  }
}
</style>
