<script setup>
import { computed, onMounted, onUnmounted, ref, shallowRef } from 'vue'

const weekdayMap = [
  '星期日',
  '星期一',
  '星期二',
  '星期三',
  '星期四',
  '星期五',
  '星期六'
]
const now = ref(new Date())
const solarFactory = shallowRef(null)
let timer = null
let lunarLoaderId = null

const pad = (value) => String(value).padStart(2, '0')
const lunarDayMap = {
  1: '初一',
  2: '初二',
  3: '初三',
  4: '初四',
  5: '初五',
  6: '初六',
  7: '初七',
  8: '初八',
  9: '初九',
  10: '初十',
  11: '十一',
  12: '十二',
  13: '十三',
  14: '十四',
  15: '十五',
  16: '十六',
  17: '十七',
  18: '十八',
  19: '十九',
  20: '二十',
  21: '廿一',
  22: '廿二',
  23: '廿三',
  24: '廿四',
  25: '廿五',
  26: '廿六',
  27: '廿七',
  28: '廿八',
  29: '廿九',
  30: '三十'
}

const lunarFormatter = new Intl.DateTimeFormat('zh-CN-u-ca-chinese', {
  month: 'long',
  day: 'numeric'
})

const digitalTime = computed(() => {
  const time = now.value
  return `${pad(time.getHours())}:${pad(time.getMinutes())}:${pad(time.getSeconds())}`
})

const digitalDate = computed(() => {
  const time = now.value
  return `${time.getFullYear()}-${pad(time.getMonth() + 1)}-${pad(time.getDate())}`
})

const weekdayText = computed(() => weekdayMap[now.value.getDay()])

const currentLunar = computed(() => {
  if (!solarFactory.value) return null

  const time = now.value
  return solarFactory.value
    .fromYmd(time.getFullYear(), time.getMonth() + 1, time.getDate())
    .getLunar()
})

const cnDateText = computed(() => {
  if (currentLunar.value) {
    return `${currentLunar.value.getMonthInChinese()}月${currentLunar.value.getDayInChinese()}`
  }

  const parts = lunarFormatter.formatToParts(now.value)
  const monthText = parts.find((part) => part.type === 'month')?.value ?? ''
  const dayNumber = Number(
    parts.find((part) => part.type === 'day')?.value ?? ''
  )
  const dayText = lunarDayMap[dayNumber] ?? `${dayNumber}日`

  return `${monthText}${dayText}`
})

const almanacInfo = computed(() => {
  const lunar = currentLunar.value

  if (!lunar) {
    return {
      ganzhi: '--',
      yi: '加载中',
      ji: '加载中',
      xiu: '--',
      chongsha: '--'
    }
  }

  const yiList = lunar.getDayYi().slice(0, 4)
  const jiList = lunar.getDayJi().slice(0, 4)
  const xiuLuck = lunar.getXiuLuck()
  const chongDesc = lunar.getChongDesc?.() || lunar.getChong()

  return {
    ganzhi: lunar.getDayInGanZhi(),
    yi: yiList.length ? yiList.join(' / ') : '无特别宜事',
    ji: jiList.length ? jiList.join(' / ') : '无特别忌事',
    xiu: `${lunar.getXiu()}宿${xiuLuck ? ` · ${xiuLuck}` : ''}`,
    chongsha: `冲${chongDesc} 煞${lunar.getSha()}`
  }
})

const currentDayText = computed(() => pad(now.value.getDate()))
const secondRotation = computed(() => now.value.getSeconds() * 6)
const minuteRotation = computed(
  () => now.value.getMinutes() * 6 + now.value.getSeconds() * 0.1
)
const hourRotation = computed(() => {
  const hours = now.value.getHours() % 12
  return hours * 30 + now.value.getMinutes() * 0.5
})

const clockMarkers = Array.from({ length: 12 }, (_, index) => ({
  angle: index * 30,
  accent: index % 3 === 0
}))

const getRemainingRatio = (start, end, current) => {
  const total = end - start
  const remaining = end - current
  if (total <= 0) return 0
  return Math.min(Math.max(remaining / total, 0), 1)
}

const yearRemainingRatio = computed(() => {
  const current = now.value.getTime()
  const start = new Date(now.value.getFullYear(), 0, 1).getTime()
  const end = new Date(now.value.getFullYear() + 1, 0, 1).getTime()
  return getRemainingRatio(start, end, current)
})

const monthRemainingRatio = computed(() => {
  const current = now.value.getTime()
  const start = new Date(
    now.value.getFullYear(),
    now.value.getMonth(),
    1
  ).getTime()
  const end = new Date(
    now.value.getFullYear(),
    now.value.getMonth() + 1,
    1
  ).getTime()
  return getRemainingRatio(start, end, current)
})

const dayRemainingRatio = computed(() => {
  const current = now.value.getTime()
  const start = new Date(
    now.value.getFullYear(),
    now.value.getMonth(),
    now.value.getDate()
  ).getTime()
  const end = new Date(
    now.value.getFullYear(),
    now.value.getMonth(),
    now.value.getDate() + 1
  ).getTime()
  return getRemainingRatio(start, end, current)
})

const toPercent = (ratio) => `${Math.round(ratio * 100)}%`
const progressRings = computed(() => [
  {
    label: '今年剩余',
    percent: toPercent(yearRemainingRatio.value),
    ratio: yearRemainingRatio.value,
    color: '#31c48d'
  },
  {
    label: '本月剩余',
    percent: toPercent(monthRemainingRatio.value),
    ratio: monthRemainingRatio.value,
    color: '#ff5b73'
  },
  {
    label: '今日剩余',
    percent: toPercent(dayRemainingRatio.value),
    ratio: dayRemainingRatio.value,
    color: '#3f8cff'
  }
])

const circleRadius = 26
const circleCircumference = 2 * Math.PI * circleRadius
const getRingDashoffset = (ratio) => circleCircumference * (1 - ratio)

const loadLunarModule = async () => {
  if (solarFactory.value) return

  try {
    const { Solar } = await import('lunar-javascript')
    solarFactory.value = Solar
  } catch {
    solarFactory.value = null
  }
}

onMounted(() => {
  now.value = new Date()
  timer = window.setInterval(() => {
    now.value = new Date()
  }, 1000)

  if ('requestIdleCallback' in window) {
    lunarLoaderId = window.requestIdleCallback(() => {
      loadLunarModule()
    })
  } else {
    lunarLoaderId = window.setTimeout(() => {
      loadLunarModule()
    }, 200)
  }
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }

  if (lunarLoaderId) {
    if ('cancelIdleCallback' in window) {
      window.cancelIdleCallback(lunarLoaderId)
    } else {
      clearTimeout(lunarLoaderId)
    }
    lunarLoaderId = null
  }
})
</script>

<template>
  <div class="home-time-widgets">
    <div class="clock-card">
      <h4 class="card-title">
        <svg
          viewBox="0 0 24 24"
          width="15"
          height="15"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 2" />
        </svg>
        实时时钟
      </h4>
      <div class="analog-clock" aria-label="实时时钟">
        <span
          v-for="marker in clockMarkers"
          :key="marker.angle"
          class="clock-marker"
          :class="{ 'clock-marker--accent': marker.accent }"
          :style="{ transform: `translateX(-50%) rotate(${marker.angle}deg)` }"
        />
        <span
          class="clock-hand clock-hand--hour"
          :style="{ transform: `translateX(-50%) rotate(${hourRotation}deg)` }"
        >
          <span class="clock-hand-shape" />
        </span>
        <span
          class="clock-hand clock-hand--minute"
          :style="{
            transform: `translateX(-50%) rotate(${minuteRotation}deg)`
          }"
        >
          <span class="clock-hand-shape" />
        </span>
        <span
          class="clock-hand clock-hand--second"
          :style="{
            transform: `translateX(-50%) rotate(${secondRotation}deg)`
          }"
        >
          <span class="clock-hand-shape" />
        </span>
        <span class="clock-center" />
      </div>
      <div class="clock-digital">{{ digitalTime }}</div>
      <div class="clock-date">{{ digitalDate }} · {{ weekdayText }}</div>
      <div class="clock-ganzhi">{{ almanacInfo.ganzhi }}日</div>
    </div>

    <div
      class="time-progress-card"
      tabindex="0"
      aria-label="日期与黄历信息卡片"
    >
      <div class="time-progress-panel time-progress-panel--front">
        <div class="time-progress-head">
          <div class="time-progress-day">{{ currentDayText }}</div>
          <div class="time-progress-meta">
            <p class="time-progress-date">{{ cnDateText }}</p>
            <p class="time-progress-weekday">{{ weekdayText }}</p>
          </div>
        </div>
        <div class="time-progress-divider" />
        <div class="time-progress-rings">
          <div
            v-for="item in progressRings"
            :key="item.label"
            class="time-ring-item"
          >
            <div class="time-ring-wrap">
              <svg class="time-ring-svg" viewBox="0 0 64 64" aria-hidden="true">
                <circle
                  class="time-ring-track"
                  cx="32"
                  cy="32"
                  :r="circleRadius"
                />
                <circle
                  class="time-ring-progress"
                  cx="32"
                  cy="32"
                  :r="circleRadius"
                  :stroke="item.color"
                  :stroke-dasharray="circleCircumference"
                  :stroke-dashoffset="getRingDashoffset(item.ratio)"
                />
              </svg>
              <span class="time-ring-percent" :style="{ color: item.color }">
                {{ item.percent }}
              </span>
            </div>
            <span class="time-ring-label">{{ item.label }}</span>
          </div>
        </div>
      </div>

      <div class="time-progress-panel time-progress-panel--back">
        <div class="almanac-grid">
          <div class="almanac-item">
            <span class="almanac-label almanac-label--yi">宜：</span>
            <span class="almanac-value">{{ almanacInfo.yi }}</span>
          </div>
          <div class="almanac-item">
            <span class="almanac-label almanac-label--ji">忌：</span>
            <span class="almanac-value">{{ almanacInfo.ji }}</span>
          </div>
          <div class="almanac-item">
            <span class="almanac-label almanac-label--xiu">星宿：</span>
            <span class="almanac-value">{{ almanacInfo.xiu }}</span>
          </div>
          <div class="almanac-item">
            <span class="almanac-label almanac-label--chongsha">冲煞：</span>
            <span class="almanac-value">{{ almanacInfo.chongsha }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="countdown-card">
      <div class="countdown-icon" aria-hidden="true">
        <svg viewBox="0 0 64 64" width="44" height="44" fill="none">
          <g filter="url(#countdownHourglassShadow)">
            <rect
              x="12"
              y="8"
              width="40"
              height="7"
              rx="3.5"
              fill="var(--hourglass-frame)"
            />
            <rect
              x="12"
              y="49"
              width="40"
              height="7"
              rx="3.5"
              fill="var(--hourglass-frame)"
            />
            <path
              d="M20 15.5h24c0 7.5-4.9 11-9.1 14.3-1.2 1-1.8 1.5-1.8 2.2s.6 1.2 1.8 2.2C39.1 37.5 44 41 44 48.5H20c0-7.5 4.9-11 9.1-14.3 1.2-1 1.8-1.5 1.8-2.2s-.6-1.2-1.8-2.2C24.9 26.5 20 23 20 15.5z"
              fill="var(--hourglass-shell)"
            />
            <path
              d="M23.5 18.5h17c-.3 4.1-3.4 6.3-6.4 8.4-.8.6-1.6 1.1-2.1 1.7-.6-.6-1.3-1.1-2.1-1.7-3-2.1-6.1-4.3-6.4-8.4z"
              fill="var(--hourglass-sand)"
            />
            <path
              d="M23.5 45.5h17c-.3-4.1-3.4-6.3-6.4-8.4-.8-.6-1.6-1.1-2.1-1.7-.6.6-1.3 1.1-2.1 1.7-3 2.1-6.1 4.3-6.4 8.4z"
              fill="var(--hourglass-sand)"
            />
            <circle cx="32" cy="32" r="1.8" fill="var(--hourglass-sand-glow)" />
          </g>
          <defs>
            <filter
              id="countdownHourglassShadow"
              x="7"
              y="5"
              width="50"
              height="54"
              filterUnits="userSpaceOnUse"
              color-interpolation-filters="sRGB"
            >
              <feDropShadow
                dx="0"
                dy="2"
                stdDeviation="1.8"
                flood-color="rgba(0,0,0,0.14)"
              />
            </filter>
          </defs>
        </svg>
      </div>
      <div class="countdown-main">
        <p class="countdown-text">今天还剩：{{ progressRings[2].percent }}</p>
        <div class="countdown-bar">
          <span
            class="countdown-bar-fill"
            :style="{ width: progressRings[2].percent }"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-time-widgets {
  width: 100%;
  max-width: 240px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-title {
  margin: 0 0 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #303133;
  font-size: 14px;
  font-weight: 700;
}

.clock-card,
.time-progress-card,
.countdown-card {
  border: 1px solid var(--blog-border-light);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
}

.clock-card {
  position: relative;
  padding: 16px 16px 18px;
  border-radius: 24px;
  background: var(--blog-card);
  text-align: center;
  --clock-hour-fill: #4d5160;
  --clock-hour-stroke: rgba(0, 0, 0, 0.18);
  --clock-time-color: #2f3542;
  --clock-date-color: #5f6778;
}

.clock-ganzhi {
  margin-top: 8px;
  color: #121212;
  font-size: 16px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 1px;
}

.analog-clock {
  position: relative;
  width: 172px;
  height: 172px;
  margin: 0 auto 10px;
  border-radius: 50%;
  background: #fff;
}

.clock-marker {
  position: absolute;
  left: 50%;
  top: 14px;
  width: 10px;
  height: 24px;
  transform-origin: 50% 72px;
  border-radius: 999px;
  background: #dedfe4;
}

.clock-marker--accent {
  background: #4d5160;
}

.clock-hand {
  position: absolute;
  left: 50%;
  bottom: 50%;
  width: 0;
  height: 0;
  transform-origin: center bottom;
}

.clock-hand--hour {
  z-index: 3;
}

.clock-hand--minute {
  z-index: 1;
}

.clock-hand--second {
  z-index: 2;
}

.clock-hand-shape {
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  transform-origin: center bottom;
  filter: drop-shadow(0 0 0 #ffffff) drop-shadow(0 0 0 #ffffff);
}

.clock-hand--hour .clock-hand-shape {
  width: 14px;
  height: 30px;
  border: 1.5px solid var(--clock-hour-stroke);
  border-radius: 7px;
  background: var(--clock-hour-fill);
  box-shadow: 0 0 0 3px #fff;
}

.clock-hand--minute .clock-hand-shape {
  width: 18px;
  height: 46px;
  background: #3b94f6;
  clip-path: polygon(50% 0, 100% 18%, 60% 100%, 40% 100%, 0 18%);
  box-shadow: 0 0 0 3px #fff;
}

.clock-hand--second .clock-hand-shape {
  width: 16px;
  height: 48px;
  background: #ef4762;
  clip-path: polygon(50% 0, 100% 18%, 60% 100%, 40% 100%, 0 18%);
  box-shadow: 0 0 0 3px #fff;
}

.clock-center {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 4;
  width: 28px;
  height: 28px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: #4d5160;
}

.clock-digital {
  color: var(--clock-time-color);
  font-family: var(--blog-serif);
  font-size: 22px;
  line-height: 1.2;
  letter-spacing: 1px;
}

.clock-date {
  margin-top: 6px;
  color: var(--clock-date-color);
  font-size: 13px;
}

.time-progress-card {
  position: relative;
  min-height: 204px;
  overflow: hidden;
  padding: 14px 18px 16px;
  border-radius: 28px;
  background:
    radial-gradient(
      circle at top left,
      rgba(109, 194, 255, 0.14),
      transparent 34%
    ),
    linear-gradient(180deg, #fffefa 0%, #ffffff 100%);
  isolation: isolate;
}

.time-progress-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(
      circle at 80% 20%,
      rgba(73, 166, 255, 0.12),
      transparent 26%
    ),
    linear-gradient(
      140deg,
      rgba(255, 255, 255, 0) 0%,
      rgba(73, 166, 255, 0.08) 100%
    );
  opacity: 0;
  transition: opacity 0.45s ease;
  pointer-events: none;
}

.time-progress-card:hover::after,
.time-progress-card:focus-visible::after,
.time-progress-card:focus-within::after {
  opacity: 1;
}

.time-progress-card:focus-visible {
  outline: 2px solid rgba(73, 166, 255, 0.28);
  outline-offset: 3px;
}

.time-progress-panel {
  position: absolute;
  inset: 18px;
  display: flex;
  flex-direction: column;
  transition:
    opacity 0.42s ease,
    transform 0.42s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.42s ease;
}

.time-progress-panel--front {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
}

.time-progress-panel--back {
  justify-content: center;
  opacity: 0;
  transform: translateY(16px) scale(0.96);
  filter: blur(6px);
  pointer-events: none;
}

.time-progress-card:hover .time-progress-panel--front,
.time-progress-card:focus-visible .time-progress-panel--front,
.time-progress-card:focus-within .time-progress-panel--front {
  opacity: 0;
  transform: translateY(-14px) scale(0.97);
  filter: blur(8px);
}

.time-progress-card:hover .time-progress-panel--back,
.time-progress-card:focus-visible .time-progress-panel--back,
.time-progress-card:focus-within .time-progress-panel--back {
  opacity: 1;
  transform: translateY(0) scale(1);
  filter: blur(0);
  pointer-events: auto;
}

.time-progress-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.time-progress-day {
  color: #121212;
  font-family: 'Trebuchet MS', 'Segoe Print', 'Comic Sans MS', cursive;
  font-size: 64px;
  font-weight: 800;
  line-height: 0.9;
  letter-spacing: 1px;
}

.time-progress-meta {
  flex: 1;
  padding-top: 2px;
  text-align: right;
}

.time-progress-date,
.time-progress-weekday {
  margin: 0;
  color: #5aa8ea;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 2px;
}

.time-progress-weekday {
  margin-top: 4px;
}

.time-progress-divider {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  align-self: center;
  width: 78%;
  height: 1px;
  margin: 14px auto 18px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(148, 163, 184, 0.18) 12%,
    rgba(148, 163, 184, 0.42) 50%,
    rgba(148, 163, 184, 0.18) 88%,
    transparent
  );
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.55);
}

.time-progress-rings {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.time-ring-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.time-ring-wrap {
  position: relative;
  width: 58px;
  height: 58px;
}

.time-ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.time-ring-track,
.time-ring-progress {
  fill: none;
  stroke-width: 8;
  stroke-linecap: round;
}

.time-ring-track {
  stroke: #ececec;
}

.time-ring-progress {
  transition: stroke-dashoffset 0.4s ease;
}

.time-ring-percent {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--blog-serif);
  font-size: 14px;
  font-weight: 700;
}

.time-ring-label {
  color: #2b2b2b;
  font-size: 12px;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.almanac-grid {
  display: grid;
  gap: 4px;
}

.almanac-item {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  align-items: start;
  gap: 8px;
  padding: 8px 0 9px;
  border-bottom: 1px solid rgba(100, 116, 139, 0.14);
}

.almanac-label {
  color: #607086;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.almanac-label--yi {
  color: #2f9e74;
}

.almanac-label--ji {
  color: #e45874;
}

.almanac-label--xiu {
  color: #4b8ef7;
}

.almanac-label--chongsha {
  color: #9b6bdb;
}

.almanac-value {
  color: #223044;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}

.almanac-item:first-child {
  padding-top: 0;
}

.almanac-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.countdown-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 16px;
  border: none;
  border-radius: 30px;
  background: linear-gradient(135deg, #4f77e3 0%, #5279e1 100%);
  box-shadow:
    0 20px 34px rgba(83, 121, 225, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  --hourglass-frame: #2d3140;
  --hourglass-shell: #fff8f8;
  --hourglass-sand: #ff6079;
  --hourglass-sand-glow: #ff8ea1;
  --countdown-text-color: #f9fbff;
  --countdown-track: rgba(255, 255, 255, 0.18);
  --countdown-fill: linear-gradient(90deg, #f5f7ff 0%, #eef2ff 100%);
  --countdown-fill-shadow: rgba(30, 53, 120, 0.12);
}

.countdown-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
}

.countdown-icon svg {
  width: 54px;
  height: 54px;
  overflow: visible;
}

.countdown-main {
  flex: 1;
  min-width: 0;
}

.countdown-text {
  margin: 0 0 12px;
  color: var(--countdown-text-color);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.12);
}

.countdown-bar {
  position: relative;
  width: 100%;
  height: 18px;
  margin-left: -6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--countdown-track);
  box-shadow:
    inset 0 1px 2px rgba(18, 30, 62, 0.18),
    0 1px 0 rgba(255, 255, 255, 0.06);
}

.countdown-bar-fill {
  display: block;
  min-width: 18px;
  height: 100%;
  border-radius: inherit;
  background: var(--countdown-fill);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.1),
    0 4px 10px var(--countdown-fill-shadow);
  transition: width 0.4s ease;
}

@media (max-width: 1440px) {
  .home-time-widgets {
    max-width: 224px;
  }
}

@media (max-width: 1220px) {
  .home-time-widgets {
    max-width: none;
  }
}

:global(html.dark .clock-card) {
  --clock-hour-fill: #626777;
  --clock-hour-stroke: rgba(0, 0, 0, 0.28);
  --clock-time-color: #ffffff;
  --clock-date-color: rgba(255, 255, 255, 0.82);
}

:global(html.dark .clock-digital) {
  color: #ffffff !important;
}

:global(html.dark .clock-date) {
  color: rgba(255, 255, 255, 0.82) !important;
}

:global(html.dark .clock-ganzhi) {
  color: #ffffff;
}

:global(html.dark .time-progress-card) {
  background:
    radial-gradient(
      circle at top left,
      rgba(109, 194, 255, 0.08),
      transparent 34%
    ),
    linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.045) 0%,
      rgba(255, 255, 255, 0.02) 100%
    ),
    var(--blog-card);
  border-color: var(--blog-border);
  box-shadow:
    0 10px 30px rgba(15, 23, 42, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

:global(html.dark .time-progress-day) {
  color: #ffffff;
}

:global(html.dark .time-progress-date),
:global(html.dark .time-progress-weekday) {
  color: #6caef8;
}

:global(html.dark .time-progress-divider) {
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.1) 12%,
    rgba(255, 255, 255, 0.22) 50%,
    rgba(255, 255, 255, 0.1) 88%,
    transparent
  );
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.04);
}

:global(html.dark .time-ring-track) {
  stroke: rgba(255, 255, 255, 0.08);
}

:global(html.dark .time-ring-percent) {
  opacity: 0.94;
}

:global(html.dark .time-ring-label) {
  color: rgba(255, 255, 255, 0.92);
}

:global(html.dark .almanac-item) {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

:global(html.dark .almanac-label) {
  color: rgba(255, 255, 255, 0.58);
}

:global(html.dark .almanac-label--yi) {
  color: #7ed7b0;
}

:global(html.dark .almanac-label--ji) {
  color: #ff8da1;
}

:global(html.dark .almanac-label--xiu) {
  color: #84b5ff;
}

:global(html.dark .almanac-label--chongsha) {
  color: #b89af0;
}

:global(html.dark .almanac-value) {
  color: rgba(255, 255, 255, 0.92);
}

:global(html.dark .countdown-card) {
  border: 1px solid var(--blog-border);
  background:
    radial-gradient(
      circle at top left,
      rgba(109, 194, 255, 0.08),
      transparent 34%
    ),
    linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.045) 0%,
      rgba(255, 255, 255, 0.02) 100%
    ),
    var(--blog-card);
  box-shadow:
    0 10px 30px rgba(15, 23, 42, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.03);
  --hourglass-frame: #384554;
  --hourglass-shell: #fff7f7;
  --hourglass-sand: #ff5f79;
  --hourglass-sand-glow: #ff91a3;
  --countdown-text-color: #e6bd68;
  --countdown-track: rgba(255, 255, 255, 0.1);
  --countdown-fill: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.92) 0%,
    rgba(247, 247, 247, 0.88) 100%
  );
  --countdown-fill-shadow: rgba(0, 0, 0, 0.12);
}

:global(html.dark .countdown-text) {
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.24);
}

:global(html.dark .countdown-bar) {
  box-shadow:
    inset 0 1px 2px rgba(0, 0, 0, 0.28),
    0 1px 0 rgba(255, 255, 255, 0.03);
}
</style>
