<script setup>
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElEmpty,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { request, queryString } from '../api.js'
import { session } from '../session.js'
import { formatCount, ratio } from '../format.js'
import RequestError from '../components/RequestError.vue'
import TrendChart from '../components/TrendChart.vue'
import ReportChart from '../components/ReportChart.vue'
import { makeArticleOption, makeProvinceOption, makeRefererOption } from '../chartOptions.js'
import AdminIcon from '../components/AdminIcon.vue'
import RollingNumber from '../components/RollingNumber.vue'

const today = new Date().toISOString().slice(0, 10)
const weekAgo = new Date(Date.now() - 6 * 86400000).toISOString().slice(0, 10)
const filters = reactive({ begin: weekAgo, end: today, siteCode: '' })
const overview = ref(null)
const views = ref([])
const visitors = ref([])
const provinces = ref([])
const articles = ref([])
const referers = ref([])
const articleOption = computed(() => makeArticleOption(articles.value))
const provinceOption = computed(() => makeProvinceOption(provinces.value))
const refererOption = computed(() => makeRefererOption(referers.value))
const loading = ref(false)
const error = ref('')
// 各图表卡片独立维护“图表 / 数据表”视图状态，保持卡片高度固定不互相拉伸。
const tableView = reactive({
  views: false,
  visitors: false,
  articles: false,
  provinces: false,
  referers: false,
})
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
    const refererQuery = queryString({ ...filters, limit: 10 })
    const results = await Promise.all([
      request(`/admin/statistics/overview${query}`, { signal: current.signal }),
      request(`/admin/statistics/page-views${query}`, { signal: current.signal }),
      request(`/admin/statistics/visitors${query}`, { signal: current.signal }),
      request(`/admin/statistics/provinces${query}`, { signal: current.signal }),
      request(`/admin/statistics/articles${articleQuery}`, { signal: current.signal }),
      request(`/admin/statistics/referers${refererQuery}`, { signal: current.signal }),
    ])
    if (!results[0].data || !results.slice(1).every((result) => Array.isArray(result.data))) {
      throw new Error('统计数据加载异常，请刷新页面后重试。')
    }
    overview.value = results[0].data
    views.value = results[1].data
    visitors.value = results[2].data
    provinces.value = results[3].data
    articles.value = results[4].data
    referers.value = results[5].data
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
/** 切换顶部站点分栏并刷新统计报表。 */
function switchSite(siteCode) {
  filters.siteCode = siteCode
  load()
}

/** 切换指定图表卡片的图表与数据表视图。 */
function toggleTableView(key) {
  tableView[key] = !tableView[key]
}

onMounted(load)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <h1>
          你好，{{ session.user?.nickname }}
          <span class="greeting-dot">.</span>
        </h1>
      </div>
      <span class="date-caption">站点数据概览</span>
    </div>
    <div class="page-tabs">
      <button
        v-for="tab in [
          { value: '', label: '全部站点' },
          { value: 'home', label: '主页' },
          { value: 'blog', label: '博客' },
          { value: 'cv', label: '简历' },
        ]"
        :key="tab.value"
        :class="{ active: filters.siteCode === tab.value }"
        @click="switchSite(tab.value)"
      >
        {{ tab.label }}
      </button>
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
            <strong class="stat-value"><RollingNumber :value="overview?.[card.key]" /></strong>
            <p class="stat-label">{{ card.label }}</p>
          </div>
        </article>
      </div>
      <div class="charts-grid">
        <el-card shadow="never">
          <div class="card-heading">
            <div>
              <h2>浏览趋势</h2>
            </div>
            <div class="card-heading-actions">
              <span class="chart-legend">
                <i></i>
                浏览量
              </span>
              <button
                v-if="views.length"
                type="button"
                class="chart-view-toggle"
                :aria-pressed="tableView.views"
                @click="toggleTableView('views')"
              >
                {{ tableView.views ? '返回图表' : '查看数据' }}
              </button>
            </div>
          </div>
          <TrendChart
            :rows="views"
            value-key="pageViewCount"
            label="浏览量"
            color="#2563eb"
            :show-table="tableView.views"
          />
        </el-card>
        <el-card shadow="never">
          <div class="card-heading">
            <div>
              <h2>新访客趋势</h2>
            </div>
            <div class="card-heading-actions">
              <span class="chart-legend is-visitors">
                <i></i>
                新访客
              </span>
              <button
                v-if="visitors.length"
                type="button"
                class="chart-view-toggle"
                :aria-pressed="tableView.visitors"
                @click="toggleTableView('visitors')"
              >
                {{ tableView.visitors ? '返回图表' : '查看数据' }}
              </button>
            </div>
          </div>
          <TrendChart
            :rows="visitors"
            value-key="newVisitorCount"
            label="新访客数"
            color="#258552"
            :show-table="tableView.visitors"
          />
        </el-card>
      </div>
      <div class="charts-grid">
        <el-card shadow="never">
          <div class="card-heading">
            <h2>文章访问排行</h2>
            <div class="card-heading-actions">
              <span class="muted-text">区间内 · 全部博客文章</span>
              <button
                v-if="articles.length"
                type="button"
                class="chart-view-toggle"
                :aria-pressed="tableView.articles"
                @click="toggleTableView('articles')"
              >
                {{ tableView.articles ? '返回图表' : '查看数据' }}
              </button>
            </div>
          </div>
          <template v-if="articles.length">
            <Transition name="chart-swap" mode="out-in">
              <ReportChart
                v-if="!tableView.articles"
                :option="articleOption"
                label="文章访问排行柱状图"
              />
              <div v-else class="chart-table-pane">
                <el-table :data="articles">
                  <el-table-column type="index" label="#" width="50" />
                  <el-table-column
                    prop="title"
                    label="文章标题"
                    min-width="180"
                    show-overflow-tooltip
                  />
                  <el-table-column label="浏览量" width="110" align="center">
                    <template #default="{ row }">{{ formatCount(row.pageViewCount) }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </Transition>
          </template>
          <el-empty v-else description="暂无文章访问数据" :image-size="70" />
        </el-card>
        <el-card shadow="never">
          <div class="card-heading">
            <h2>访客省份分布</h2>
            <div v-if="provinces.length" class="card-heading-actions">
              <button
                type="button"
                class="chart-view-toggle"
                :aria-pressed="tableView.provinces"
                @click="toggleTableView('provinces')"
              >
                {{ tableView.provinces ? '返回图表' : '查看数据' }}
              </button>
            </div>
          </div>
          <template v-if="provinces.length">
            <Transition name="chart-swap" mode="out-in">
              <ReportChart
                v-if="!tableView.provinces"
                :option="provinceOption"
                label="访客省份分布环形图"
              />
              <div v-else class="chart-table-pane">
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
              </div>
            </Transition>
          </template>
          <el-empty v-else description="暂无地域数据" :image-size="70" />
        </el-card>
      </div>
      <div class="charts-grid">
        <el-card shadow="never">
          <div class="card-heading">
            <h2>访问来源排行</h2>
            <div v-if="referers.length" class="card-heading-actions">
              <button
                type="button"
                class="chart-view-toggle"
                :aria-pressed="tableView.referers"
                @click="toggleTableView('referers')"
              >
                {{ tableView.referers ? '返回图表' : '查看数据' }}
              </button>
            </div>
          </div>
          <template v-if="referers.length">
            <Transition name="chart-swap" mode="out-in">
              <ReportChart
                v-if="!tableView.referers"
                :option="refererOption"
                label="访问来源排行柱状图"
              />
              <div v-else class="chart-table-pane">
                <el-table :data="referers">
                  <el-table-column type="index" label="#" width="50" />
                  <el-table-column label="来源域名" min-width="180" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.source || '直接访问' }}</template>
                  </el-table-column>
                  <el-table-column label="浏览量" width="100" align="center">
                    <template #default="{ row }">{{ formatCount(row.pageViewCount) }}</template>
                  </el-table-column>
                  <el-table-column label="访客数" width="100" align="center">
                    <template #default="{ row }">{{ formatCount(row.visitorCount) }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </Transition>
          </template>
          <el-empty v-else description="暂无来源数据" :image-size="70" />
        </el-card>
      </div>
    </div>
  </section>
</template>
