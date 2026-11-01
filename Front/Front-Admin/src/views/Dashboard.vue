<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { request, queryString } from '../api.js'
import { session } from '../session.js'
import { formatCount, ratio } from '../format.js'
import RequestError from '../components/RequestError.vue'
import TrendChart from '../components/TrendChart.vue'
import ReportChart from '../components/ReportChart.vue'
import { makeArticleOption, makeProvinceOption } from '../chartOptions.js'
import AdminIcon from '../components/AdminIcon.vue'

const today = new Date().toISOString().slice(0, 10)
const weekAgo = new Date(Date.now() - 6 * 86400000).toISOString().slice(0, 10)
const filters = reactive({ begin: weekAgo, end: today, siteCode: '' })
const overview = ref(null)
const views = ref([])
const visitors = ref([])
const provinces = ref([])
const articles = ref([])
const articleOption = computed(() => makeArticleOption(articles.value))
const provinceOption = computed(() => makeProvinceOption(provinces.value))
const loading = ref(false)
const error = ref('')
let controller
const cards = [
  { key: 'pageViewCount', label: '区间浏览量', icon: 'chart', tone: 'blue' },
  { key: 'visitorCount', label: '区间访客数', icon: 'user', tone: 'teal' },
  { key: 'newVisitorCount', label: '区间新访客', icon: 'user', tone: 'green' },
  { key: 'articleCount', label: '文章总数', icon: 'document', tone: 'purple' },
  { key: 'publishedArticleCount', label: '公开文章', icon: 'document', tone: 'green' },
  { key: 'commentCount', label: '评论总数', icon: 'chat', tone: 'blue' },
  { key: 'messageCount', label: '留言总数', icon: 'mail', tone: 'teal' },
  { key: 'pendingCommentCount', label: '待审评论', icon: 'chat', tone: 'orange' },
  { key: 'pendingMessageCount', label: '待审留言', icon: 'mail', tone: 'orange' },
]
const provinceMax = computed(() =>
  provinces.value.reduce(
    (maximum, item) => (BigInt(item.visitorCount) > BigInt(maximum) ? item.visitorCount : maximum),
    '0',
  ),
)

async function load() {
  error.value = ''
  const span = (new Date(filters.end).getTime() - new Date(filters.begin).getTime()) / 86400000
  if (!filters.begin || !filters.end || span < 0 || span > 366) {
    error.value = '请选择有效的开始和结束日期，区间最多 367 天。'
    return
  }
  controller?.abort()
  const current = new AbortController()
  controller = current
  loading.value = true
  try {
    const query = queryString(filters)
    // 文章排行没有 siteCode 参数，不能透传总览的全部筛选。
    const articleQuery = queryString({ begin: filters.begin, end: filters.end, limit: 10 })
    const results = await Promise.all([
      request(`/admin/statistics/overview${query}`, { signal: current.signal }),
      request(`/admin/statistics/page-views${query}`, { signal: current.signal }),
      request(`/admin/statistics/visitors${query}`, { signal: current.signal }),
      request(`/admin/statistics/provinces${query}`, { signal: current.signal }),
      request(`/admin/statistics/articles${articleQuery}`, { signal: current.signal }),
    ])
    if (!results[0].data || !results.slice(1).every((result) => Array.isArray(result.data))) {
      throw new Error('统计响应格式不正确。')
    }
    overview.value = results[0].data
    views.value = results[1].data
    visitors.value = results[2].data
    provinces.value = results[3].data
    articles.value = results[4].data
  } catch (failure) {
    if (failure.name !== 'AbortError') {
      error.value = failure.message
    }
  } finally {
    if (controller === current) {
      loading.value = false
    }
  }
}
onMounted(load)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">OVERVIEW</p>
        <h1>
          你好，{{ session.user?.nickname }}
          <span class="greeting-dot">.</span>
        </h1>
        <p class="muted-text">每一篇内容，每一次访问，都在这里留下记录。</p>
      </div>
      <span class="date-caption">站点数据概览</span>
    </div>
    <el-card class="filter-card" shadow="never">
      <form class="filter-form" @submit.prevent="load">
        <span class="filter-label">统计区间</span>
        <el-date-picker
          v-model="filters.begin"
          type="date"
          aria-label="开始日期 UTC"
          placeholder="开始日期"
          value-format="YYYY-MM-DD"
        />
        <span class="muted-text">至</span>
        <el-date-picker
          v-model="filters.end"
          type="date"
          aria-label="结束日期 UTC"
          placeholder="结束日期"
          value-format="YYYY-MM-DD"
        />
        <el-select v-model="filters.siteCode" aria-label="站点" placeholder="全部站点" clearable>
          <el-option label="主页" value="home" />
          <el-option label="博客" value="blog" />
          <el-option label="简历" value="cv" />
        </el-select>
        <el-button type="primary" native-type="submit" :loading="loading">更新报表</el-button>
        <span class="muted-text">UTC 自然日</span>
      </form>
    </el-card>
    <RequestError :message="error" @retry="load" />
    <div v-if="!error" v-loading="loading">
      <div class="stats-grid">
        <article v-for="card in cards" :key="card.key" :class="['stat-card', `tone-${card.tone}`]">
          <span class="stat-icon"><AdminIcon :name="card.icon" /></span>
          <div class="stat-info">
            <strong class="stat-value">{{ formatCount(overview?.[card.key]) }}</strong>
            <p class="stat-label">{{ card.label }}</p>
            <span class="stat-card-caption">
              {{
                card.key.includes('Visitor') || ['visitorCount', 'pageViewCount'].includes(card.key)
                  ? '所选日期区间'
                  : '当前内容状态'
              }}
            </span>
          </div>
        </article>
      </div>
      <div class="charts-grid">
        <el-card shadow="never">
          <div class="card-heading">
            <div>
              <h2>浏览趋势</h2>
              <p class="muted-text">保留的每日访问明细</p>
            </div>
            <span class="chart-legend">
              <i></i>
              浏览量
            </span>
          </div>
          <TrendChart :rows="views" value-key="pageViewCount" label="浏览量" color="#2563eb" />
        </el-card>
        <el-card shadow="never">
          <div class="card-heading">
            <div>
              <h2>新访客趋势</h2>
              <p class="muted-text">区间内首次访问的读者</p>
            </div>
            <span class="chart-legend is-visitors">
              <i></i>
              新访客
            </span>
          </div>
          <TrendChart :rows="visitors" value-key="newVisitorCount" label="新访客数" color="#258552" />
        </el-card>
      </div>
      <div class="charts-grid">
        <el-card shadow="never">
          <div class="card-heading">
            <h2>文章访问排行</h2>
            <span class="muted-text">区间内 · 全部博客文章</span>
          </div>
          <ReportChart v-if="articles.length" :option="articleOption" label="文章访问排行柱状图" />
          <details v-if="articles.length" class="chart-details">
            <summary>查看文章访问明细</summary>
            <el-table :data="articles">
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="title" label="文章标题" min-width="180" show-overflow-tooltip />
              <el-table-column label="浏览量" width="110" align="right">
                <template #default="{ row }">{{ formatCount(row.pageViewCount) }}</template>
              </el-table-column>
            </el-table>
          </details>
          <el-empty v-else description="暂无文章访问数据" :image-size="70" />
        </el-card>
        <el-card shadow="never">
          <div class="card-heading">
            <h2>访客省份分布</h2>
            <span class="muted-text">按当前地域归类</span>
          </div>
          <ReportChart v-if="provinces.length" :option="provinceOption" label="访客省份分布环形图" />
          <details v-if="provinces.length" class="chart-details">
            <summary>查看省份数据</summary>
            <div class="province-list">
              <div
                v-for="province in provinces"
                :key="province.province || 'unknown'"
                class="province-row"
              >
                <span>{{ province.province || '未知' }}</span>
                <span class="province-track">
                  <span :style="{ width: `${ratio(province.visitorCount, provinceMax)}%` }"></span>
                </span>
                <strong>{{ formatCount(province.visitorCount) }}</strong>
              </div>
            </div>
          </details>
          <el-empty v-else description="暂无地域数据" :image-size="70" />
        </el-card>
      </div>
    </div>
  </section>
</template>
