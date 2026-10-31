<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { session } from '@/session.js'
import { useBlogStore, useThemeStore } from '@/stores'

const router = useRouter()
const blogStore = useBlogStore()
const themeStore = useThemeStore()

const isDark = computed(() => themeStore.isDark)

/* 滚动检测 */
const scrolled = ref(false)
const handleScroll = () => {
  scrolled.value = window.scrollY > 60
}
onMounted(() =>
  window.addEventListener('scroll', handleScroll, { passive: true })
)

/* 搜索 */
const searchVisible = ref(false)
const keyword = ref('')
const searchInputRef = ref(null)
const mobileNavVisible = ref(false)
const compactNav = ref(false)
const headerInnerRef = ref(null)
const headerLeftRef = ref(null)
const headerRightRef = ref(null)
const siteTitleRef = ref(null)
const navDesktopRef = ref(null)
const searchBoxRef = ref(null)
const miniPlayerWrapRef = ref(null)
const themeSwitchRef = ref(null)
const searchAreaRef = ref(null)

/* 音乐播放 */
const isPlaying = ref(false)
const audioRef = ref(null)
const musicIndex = ref(0)
const musicListVisible = ref(false)

const currentTrack = computed(() => blogStore.musics[musicIndex.value] || null)
const DESKTOP_SEARCH_EXPANDED_WIDTH = 180
const HEADER_LAYOUT_BUFFER = 12
let headerResizeObserver = null

const syncHeaderLayout = () => {
  nextTick(() => {
    const headerInnerEl = headerInnerRef.value
    const headerLeftEl = headerLeftRef.value
    const siteTitleEl = siteTitleRef.value?.$el ?? siteTitleRef.value
    const navDesktopEl = navDesktopRef.value
    const searchBoxEl = searchBoxRef.value
    const miniPlayerWrapEl = miniPlayerWrapRef.value
    const themeSwitchEl = themeSwitchRef.value
    const searchAreaEl = searchAreaRef.value

    if (!headerInnerEl || !headerLeftEl || !navDesktopEl) {
      return
    }

    if (!siteTitleEl) return

    const leftGap = parseFloat(getComputedStyle(headerLeftEl).gap || '0')
    const rightGap = parseFloat(
      getComputedStyle(headerRightRef.value).gap || '0'
    )
    const titleWidth = siteTitleEl.getBoundingClientRect().width
    const navWidth = navDesktopEl.scrollWidth
    const leftWidth = titleWidth + leftGap + navWidth
    const currentSearchWidth = searchBoxEl?.getBoundingClientRect().width ?? 0
    const expandedSearchDelta = Math.max(
      0,
      DESKTOP_SEARCH_EXPANDED_WIDTH - currentSearchWidth
    )
    const rightItems = []

    if (currentTrack.value && miniPlayerWrapEl) {
      rightItems.push(miniPlayerWrapEl.getBoundingClientRect().width)
    }

    if (themeSwitchEl) {
      rightItems.push(themeSwitchEl.getBoundingClientRect().width)
    }

    if (searchAreaEl) {
      rightItems.push(
        searchAreaEl.getBoundingClientRect().width + expandedSearchDelta
      )
    }

    const rightWidth =
      rightItems.reduce((sum, width) => sum + width, 0) +
      Math.max(0, rightItems.length - 1) * rightGap
    const requiredWidth = leftWidth + rightWidth + HEADER_LAYOUT_BUFFER
    const shouldCompact = requiredWidth > headerInnerEl.clientWidth

    compactNav.value = shouldCompact

    if (!shouldCompact) {
      mobileNavVisible.value = false
    }
  })
}

const togglePlay = async () => {
  if (!audioRef.value || !currentTrack.value) return
  if (isPlaying.value) {
    audioRef.value.pause()
  } else {
    try {
      await audioRef.value.play()
    } catch {
      ElMessage.error('音频暂时无法播放，请重试或切换曲目')
      return
    }
  }
  isPlaying.value = !isPlaying.value
}

const playTrack = (index) => {
  musicIndex.value = index
  isPlaying.value = false
  nextTick(() => {
    if (audioRef.value) {
      audioRef.value.load()
      audioRef.value
        .play()
        .then(() => {
          isPlaying.value = true
        })
        .catch(() => ElMessage.error('音频暂时无法播放，请切换曲目'))
    }
  })
}

const nextTrack = () => {
  if (!blogStore.musics.length) return
  playTrack((musicIndex.value + 1) % blogStore.musics.length)
}

const toggleMusicList = () => {
  musicListVisible.value = !musicListVisible.value
}

const navItems = computed(() => [
  {
    label: '主页',
    icon: 'icon-zhuye',
    href: blogStore.personalInfo.website,
    external: true
  },
  { label: '博客', icon: 'icon-boke', to: '/' },
  { label: '归档', icon: 'icon-guidang', to: '/archive' },
  { label: '热门', icon: 'icon-fire', to: '/hot', isSvg: true },
  { label: '友链', icon: 'icon-lianjie', to: '/links' },
  { label: '留言', icon: 'icon-liuyan', to: '/message' },
  { label: '关于', icon: 'icon-guanyu', to: '/about' },
  { label: session.user?.nickname || '登录', icon: 'icon-user', to: session.user ? '/account' : '/login' },
  {
    label: '开往',
    icon: 'icon-subway',
    href: 'https://www.travellings.cn/go.html',
    external: true
  }
].filter((item) => !item.external || item.href))

const doSearch = () => {
  const kw = keyword.value.trim()
  if (!kw) return
  searchVisible.value = false
  keyword.value = ''
  router.push({ path: '/', query: { search: kw } })
}

const toggleSearch = () => {
  mobileNavVisible.value = false
  searchVisible.value = !searchVisible.value
  if (!searchVisible.value) {
    keyword.value = ''
  } else {
    nextTick(() => searchInputRef.value?.focus())
  }
}

const toggleMobileNav = () => {
  mobileNavVisible.value = !mobileNavVisible.value
}

const navTo = (item) => {
  mobileNavVisible.value = false
  if (item.external) {
    window.open(item.href, '_blank', 'noopener,noreferrer')
  } else {
    router.push(item.to)
  }
}

watch(
  () => blogStore.musics.length,
  () => syncHeaderLayout()
)

watch(currentTrack, () => syncHeaderLayout())

const handleWindowResize = () => {
  syncHeaderLayout()
}

onMounted(() => {
  window.addEventListener('resize', handleWindowResize, { passive: true })
  syncHeaderLayout()

  if (typeof ResizeObserver !== 'undefined') {
    headerResizeObserver = new ResizeObserver(() => {
      syncHeaderLayout()
    })
    headerInnerRef.value && headerResizeObserver.observe(headerInnerRef.value)
    headerLeftRef.value && headerResizeObserver.observe(headerLeftRef.value)
    headerRightRef.value && headerResizeObserver.observe(headerRightRef.value)
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleWindowResize)
  headerResizeObserver?.disconnect()
  headerResizeObserver = null
})
</script>

<template>
  <header
    class="site-header"
    :class="{ scrolled, dark: isDark, compact: compactNav }"
  >
    <div ref="headerInnerRef" class="header-inner">
      <div ref="headerLeftRef" class="header-left">
        <router-link ref="siteTitleRef" to="/" class="site-title"
          >{{ blogStore.site.title || 'DriftingLeaves Blog' }}</router-link
        >
        <nav ref="navDesktopRef" class="nav-desktop">
          <template v-for="item in navItems" :key="item.label">
            <a
              v-if="item.external"
              :href="item.href"
              target="_blank"
              rel="noopener"
              class="nav-link"
            >
              <svg
                v-if="item.isSvg"
                class="nav-svg-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"
                />
              </svg>
              <i v-else :class="['iconfont', item.icon]" /> {{ item.label }}
            </a>
            <router-link v-else :to="item.to" class="nav-link">
              <svg
                v-if="item.isSvg"
                class="nav-svg-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path
                  d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"
                />
              </svg>
              <i v-else :class="['iconfont', item.icon]" /> {{ item.label }}
            </router-link>
          </template>
        </nav>
      </div>

      <div ref="headerRightRef" class="header-right">
        <div
          v-if="currentTrack"
          ref="miniPlayerWrapRef"
          class="mini-player-wrap"
        >
          <div class="mini-player" @click="toggleMusicList">
            <img
              v-if="currentTrack.coverImage"
              :src="currentTrack.coverImage"
              class="player-cover"
              :class="{ spinning: isPlaying }"
            />
            <span class="player-title">{{ currentTrack.title }}</span>
          </div>
          <button class="player-btn" @click.stop="togglePlay" title="播放/暂停">
            <i
              class="iconfont"
              :class="isPlaying ? 'icon-zanting' : 'icon-play-full'"
            />
          </button>
          <button class="player-btn" @click.stop="nextTrack" title="下一首">
            <i class="iconfont icon-next" />
          </button>
          <audio
            ref="audioRef"
            :src="currentTrack.musicUrl"
            preload="none"
            @ended="nextTrack"
          />
          <transition name="fade">
            <div v-show="musicListVisible" class="music-panel">
              <div class="music-panel-header">
                <span><i class="iconfont icon-yinle" /> 播放列表</span>
                <span class="music-panel-count">
                  {{ blogStore.musics.length }} 首
                </span>
              </div>
              <ul class="music-panel-list">
                <li
                  v-for="(m, idx) in blogStore.musics"
                  :key="m.id"
                  class="music-panel-item"
                  :class="{ active: idx === musicIndex }"
                  @click="playTrack(idx)"
                >
                  <img
                    v-if="m.coverImage"
                    :src="m.coverImage"
                    class="music-panel-cover"
                  />
                  <div class="music-panel-info">
                    <span class="music-panel-name">{{ m.title }}</span>
                    <span class="music-panel-artist">{{ m.artist }}</span>
                  </div>
                  <i
                    v-if="idx === musicIndex && isPlaying"
                    class="iconfont icon-yinle playing-icon"
                  />
                </li>
              </ul>
            </div>
          </transition>
        </div>
        <!-- 明暗模式切换 -->
        <label
          ref="themeSwitchRef"
          class="theme-switch"
          :title="isDark ? '切换到浅色模式' : '切换到暗色模式'"
        >
          <input
            class="theme-switch__checkbox"
            type="checkbox"
            :checked="isDark"
            :aria-label="isDark ? '切换到浅色模式' : '切换到暗色模式'"
            @change="themeStore.toggle"
          />
          <div class="theme-switch__container">
            <div class="theme-switch__clouds"></div>
            <div class="theme-switch__stars-container">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 144 55"
                fill="none"
              >
                <path
                  fill-rule="evenodd"
                  clip-rule="evenodd"
                  d="M135.831 3.00688C135.055 3.85027 134.111 4.29946 133 4.35447C134.111 4.40947 135.055 4.85867 135.831 5.71123C136.607 6.55462 136.996 7.56303 136.996 8.72727C136.996 7.95722 137.172 7.25134 137.525 6.59129C137.886 5.93124 138.372 5.39954 138.98 5.00535C139.598 4.60199 140.268 4.39114 141 4.35447C139.88 4.2903 138.936 3.85027 138.16 3.00688C137.384 2.16348 136.996 1.16425 136.996 0C136.996 1.16425 136.607 2.16348 135.831 3.00688ZM31 23.3545C32.1114 23.2995 33.0551 22.8503 33.8313 22.0069C34.6075 21.1635 34.9956 20.1642 34.9956 19C34.9956 20.1642 35.3837 21.1635 36.1599 22.0069C36.9361 22.8503 37.8798 23.2903 39 23.3545C38.2679 23.3911 37.5976 23.602 36.9802 24.0053C36.3716 24.3995 35.8864 24.9312 35.5248 25.5913C35.172 26.2513 34.9956 26.9572 34.9956 27.7273C34.9956 26.563 34.6075 25.5546 33.8313 24.7112C33.0551 23.8587 32.1114 23.4095 31 23.3545ZM0 36.3545C1.11136 36.2995 2.05513 35.8503 2.83131 35.0069C3.6075 34.1635 3.99559 33.1642 3.99559 32C3.99559 33.1642 4.38368 34.1635 5.15987 35.0069C5.93605 35.8503 6.87982 36.2903 8 36.3545C7.26792 36.3911 6.59757 36.602 5.98015 37.0053C5.37155 37.3995 4.88644 37.9312 4.52481 38.5913C4.172 39.2513 3.99559 39.9572 3.99559 40.7273C3.99559 39.563 3.6075 38.5546 2.83131 37.7112C2.05513 36.8587 1.11136 36.4095 0 36.3545ZM56.8313 24.0069C56.0551 24.8503 55.1114 25.2995 54 25.3545C55.1114 25.4095 56.0551 25.8587 56.8313 26.7112C57.6075 27.5546 57.9956 28.563 57.9956 29.7273C57.9956 28.9572 58.172 28.2513 58.5248 27.5913C58.8864 26.9312 59.3716 26.3995 59.9802 26.0053C60.5976 25.602 61.2679 25.3911 62 25.3545C60.8798 25.2903 59.9361 24.8503 59.1599 24.0069C58.3837 23.1635 57.9956 22.1642 57.9956 21C57.9956 22.1642 57.6075 23.1635 56.8313 24.0069ZM81 25.3545C82.1114 25.2995 83.0551 24.8503 83.8313 24.0069C84.6075 23.1635 84.9956 22.1642 84.9956 21C84.9956 22.1642 85.3837 23.1635 86.1599 24.0069C86.9361 24.8503 87.8798 25.2903 89 25.3545C88.2679 25.3911 87.5976 25.602 86.9802 26.0053C86.3716 26.3995 85.8864 26.9312 85.5248 27.5913C85.172 28.2513 84.9956 28.9572 84.9956 29.7273C84.9956 28.563 84.6075 27.5546 83.8313 26.7112C83.0551 25.8587 82.1114 25.4095 81 25.3545ZM136 36.3545C137.111 36.2995 138.055 35.8503 138.831 35.0069C139.607 34.1635 139.996 33.1642 139.996 32C139.996 33.1642 140.384 34.1635 141.16 35.0069C141.936 35.8503 142.88 36.2903 144 36.3545C143.268 36.3911 142.598 36.602 141.98 37.0053C141.372 37.3995 140.886 37.9312 140.525 38.5913C140.172 39.2513 139.996 39.9572 139.996 40.7273C139.996 39.563 139.607 38.5546 138.831 37.7112C138.055 36.8587 137.111 36.4095 136 36.3545ZM101.831 49.0069C101.055 49.8503 100.111 50.2995 99 50.3545C100.111 50.4095 101.055 50.8587 101.831 51.7112C102.607 52.5546 102.996 53.563 102.996 54.7273C102.996 53.9572 103.172 53.2513 103.525 52.5913C103.886 51.9312 104.372 51.3995 104.98 51.0053C105.598 50.602 106.268 50.3911 107 50.3545C105.88 50.2903 104.936 49.8503 104.16 49.0069C103.384 48.1635 102.996 47.1642 102.996 46C102.996 47.1642 102.607 48.1635 101.831 49.0069Z"
                  fill="currentColor"
                />
              </svg>
            </div>
            <div class="theme-switch__circle-container">
              <div class="theme-switch__sun-moon-container">
                <div class="theme-switch__moon">
                  <div class="theme-switch__spot"></div>
                  <div class="theme-switch__spot"></div>
                  <div class="theme-switch__spot"></div>
                </div>
              </div>
            </div>
          </div>
        </label>

        <div ref="searchAreaRef" class="search-area">
          <div
            ref="searchBoxRef"
            class="search-box"
            :class="{ expanded: searchVisible }"
          >
            <input
              ref="searchInputRef"
              v-show="searchVisible"
              v-model="keyword"
              type="text"
              placeholder="搜索文章..."
              aria-label="搜索文章"
              maxlength="100"
              class="search-input"
              @keyup.enter="doSearch"
              @blur="searchVisible = false"
            />
          </div>
          <button class="search-toggle" @click="toggleSearch" title="搜索">
            <i class="iconfont icon-sousuo" />
          </button>
        </div>

        <button class="mobile-menu-btn" aria-label="展开导航" :aria-expanded="mobileNavVisible" @click="toggleMobileNav">
          <span :class="['bar', { open: mobileNavVisible }]" />
        </button>
      </div>
    </div>

    <nav v-show="mobileNavVisible" class="nav-mobile">
      <a
        v-for="item in navItems"
        :key="item.label"
        :href="item.external ? item.href : item.to"
        class="nav-mobile-link"
        @click.prevent="navTo(item)"
      >
        <svg
          v-if="item.isSvg"
          class="nav-mobile-svg-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"
          />
        </svg>
        <i v-else :class="['iconfont', item.icon]" /> {{ item.label }}
      </a>
      <a class="nav-mobile-link" @click="toggleSearch">
        <i class="iconfont icon-sousuo" /> 搜索
      </a>
      <a class="nav-mobile-link" @click="themeStore.toggle">
        <template v-if="isDark">
          <svg
            viewBox="0 0 24 24"
            width="15"
            height="15"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            style="vertical-align: -2px; margin-right: 4px"
          >
            <circle cx="12" cy="12" r="5" />
            <line x1="12" y1="1" x2="12" y2="3" />
            <line x1="12" y1="21" x2="12" y2="23" />
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
            <line x1="1" y1="12" x2="3" y2="12" />
            <line x1="21" y1="12" x2="23" y2="12" />
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
          </svg>
          浅色模式
        </template>
        <template v-else>
          <svg
            viewBox="0 0 24 24"
            width="15"
            height="15"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            style="vertical-align: -2px; margin-right: 4px"
          >
            <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
          </svg>
          深色模式
        </template>
      </a>
    </nav>
  </header>
</template>

<style scoped>
.site-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: transparent;
  border-bottom: none;
  transition:
    background 0.3s,
    box-shadow 0.3s;
}
.site-header.scrolled {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
}
.site-header.dark.scrolled {
  background: rgba(35, 35, 35, 0.95);
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.3);
}
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 28px;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 28px;
}
.site-title {
  font-family: var(--blog-serif);
  font-size: 18px;
  font-weight: 800;
  color: #fff;
  text-decoration: none;
  letter-spacing: 0.5px;
  white-space: nowrap;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  transition:
    color 0.3s,
    text-shadow 0.3s;
}
.scrolled .site-title {
  color: #303133;
  text-shadow: none;
}
.dark.scrolled .site-title {
  color: #e5e5e5;
}
.nav-desktop {
  display: flex;
  align-items: center;
  gap: 2px;
}
.nav-link {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
  text-decoration: none;
  padding: 6px 10px;
  border-radius: 4px;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  transition:
    color 0.2s,
    background 0.2s,
    text-shadow 0.3s;
}
.nav-link .iconfont {
  font-size: 14px;
}
.nav-link .nav-svg-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}
.nav-link:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.15);
}
.scrolled .nav-link {
  color: #606266;
  text-shadow: none;
}
.scrolled .nav-link:hover {
  color: #303133;
  background: #f5f7fa;
}
.dark.scrolled .nav-link {
  color: #b0b0b0;
}
.dark.scrolled .nav-link:hover {
  color: #e5e5e5;
  background: #333;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 杩蜂綘鎾斁鍣?*/
.mini-player-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
  position: relative;
}
.mini-player {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.25);
  cursor: pointer;
  backdrop-filter: blur(4px);
  transition:
    background 0.2s,
    border-color 0.2s;
}
.scrolled .mini-player {
  background: #f5f7fa;
  border-color: #e4e7ed;
}
.dark.scrolled .mini-player {
  background: #333;
  border-color: #444;
}
.mini-player:hover {
  border-color: rgba(255, 255, 255, 0.5);
}
.scrolled .mini-player:hover {
  border-color: #909399;
}
.dark.scrolled .mini-player:hover {
  border-color: #666;
}
.player-cover {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid rgba(255, 255, 255, 0.3);
  flex-shrink: 0;
}
.scrolled .player-cover {
  border-color: #e4e7ed;
}
.dark.scrolled .player-cover {
  border-color: #444;
}
.player-cover.spinning {
  animation: spin 8s linear infinite;
}
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
.player-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s;
}
.scrolled .player-title {
  color: #606266;
}
.dark.scrolled .player-title {
  color: #b0b0b0;
}
.player-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 15px;
  transition: color 0.2s;
  display: flex;
  align-items: center;
}
.player-btn:hover {
  color: #fff;
}
.scrolled .player-btn {
  color: #606266;
}
.scrolled .player-btn:hover {
  color: #000;
}
.dark.scrolled .player-btn {
  color: #b0b0b0;
}
.dark.scrolled .player-btn:hover {
  color: #fff;
}

/* 闊充箰鍒楄〃 */
.music-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 280px;
  max-height: 360px;
  background: var(--blog-card);
  border: 1px solid var(--blog-border);
  border-radius: 8px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.1);
  z-index: 200;
  overflow: hidden;
}
.music-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.dark .music-panel-header {
  border-bottom-color: #333;
  color: #e5e5e5;
}
.music-panel-header .iconfont {
  font-size: 14px;
  margin-right: 4px;
}
.music-panel-count {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}
.music-panel-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  max-height: 300px;
  overflow-y: auto;
}
.music-panel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  cursor: pointer;
  transition: background 0.12s;
}
.music-panel-item:hover {
  background: #f5f7fa;
}
.dark .music-panel-item:hover {
  background: #333;
}
.music-panel-item.active {
  background: #f5f7fa;
}
.dark .music-panel-item.active {
  background: #333;
}
.music-panel-item.active .music-panel-name {
  color: #000;
  font-weight: 600;
}
.dark .music-panel-item.active .music-panel-name {
  color: #fff;
}
.music-panel-cover {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid #ebeef5;
}
.dark .music-panel-cover {
  border-color: #444;
}
.music-panel-info {
  flex: 1;
  min-width: 0;
}
.music-panel-name {
  display: block;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dark .music-panel-name {
  color: #e5e5e5;
}
.music-panel-artist {
  display: block;
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.playing-icon {
  font-size: 14px;
  color: #000;
  flex-shrink: 0;
}
.dark .playing-icon {
  color: #fff;
}
.fade-enter-active,
.fade-leave-active {
  transition:
    opacity 0.15s,
    transform 0.15s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 明暗模式切换 */
.theme-switch {
  --toggle-size: 9px;
  --container-width: 5.625em;
  --container-height: 2.5em;
  --container-radius: 6.25em;
  --container-light-bg: #3d7eae;
  --container-night-bg: #1d1f2c;
  --circle-container-diameter: 3.375em;
  --sun-moon-diameter: 2.125em;
  --sun-bg: #ecca2f;
  --moon-bg: #c4c9d1;
  --spot-color: #959db1;
  --circle-container-offset: calc(
    (var(--circle-container-diameter) - var(--container-height)) / 2 * -1
  );
  --stars-color: #fff;
  --clouds-color: #f3fdff;
  --back-clouds-color: #aacadf;
  --transition: 0.5s cubic-bezier(0, -0.02, 0.4, 1.25);
  --circle-transition: 0.3s cubic-bezier(0, -0.02, 0.35, 1.17);
  display: inline-flex;
  align-items: center;
  line-height: 1;
  flex-shrink: 0;
}
.theme-switch,
.theme-switch *,
.theme-switch *::before,
.theme-switch *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
  font-size: var(--toggle-size);
}
.theme-switch__container {
  width: var(--container-width);
  height: var(--container-height);
  background-color: var(--container-light-bg);
  border-radius: var(--container-radius);
  overflow: hidden;
  cursor: pointer;
  box-shadow:
    0 -0.062em 0.062em rgba(0, 0, 0, 0.25),
    0 0.062em 0.125em rgba(255, 255, 255, 0.94);
  transition: var(--transition);
  position: relative;
}
.theme-switch__container::before {
  content: '';
  position: absolute;
  z-index: 1;
  inset: 0;
  box-shadow:
    0 0.05em 0.187em rgba(0, 0, 0, 0.25) inset,
    0 0.05em 0.187em rgba(0, 0, 0, 0.25) inset;
  border-radius: var(--container-radius);
}
.theme-switch__checkbox {
  display: none;
}
.theme-switch__circle-container {
  width: var(--circle-container-diameter);
  height: var(--circle-container-diameter);
  background-color: rgba(255, 255, 255, 0.1);
  position: absolute;
  left: var(--circle-container-offset);
  top: var(--circle-container-offset);
  border-radius: var(--container-radius);
  box-shadow:
    inset 0 0 0 3.375em rgba(255, 255, 255, 0.1),
    inset 0 0 0 3.375em rgba(255, 255, 255, 0.1),
    0 0 0 0.625em rgba(255, 255, 255, 0.1),
    0 0 0 1.25em rgba(255, 255, 255, 0.1);
  display: flex;
  transition: var(--circle-transition);
  pointer-events: none;
}
.theme-switch__sun-moon-container {
  pointer-events: auto;
  position: relative;
  z-index: 2;
  width: var(--sun-moon-diameter);
  height: var(--sun-moon-diameter);
  margin: auto;
  border-radius: var(--container-radius);
  background-color: var(--sun-bg);
  box-shadow:
    0.062em 0.062em 0.062em 0 rgba(254, 255, 239, 0.61) inset,
    0 -0.062em 0.062em 0 #a1872a inset;
  filter: drop-shadow(0.062em 0.125em 0.125em rgba(0, 0, 0, 0.25))
    drop-shadow(0 0.062em 0.125em rgba(0, 0, 0, 0.25));
  overflow: hidden;
  transition: var(--transition);
}
.theme-switch__moon {
  transform: translateX(100%);
  width: 100%;
  height: 100%;
  background-color: var(--moon-bg);
  border-radius: inherit;
  box-shadow:
    0.062em 0.062em 0.062em 0 rgba(254, 255, 239, 0.61) inset,
    0 -0.062em 0.062em 0 #969696 inset;
  transition: var(--transition);
  position: relative;
}
.theme-switch__spot {
  position: absolute;
  top: 0.75em;
  left: 0.312em;
  width: 0.75em;
  height: 0.75em;
  border-radius: var(--container-radius);
  background-color: var(--spot-color);
  box-shadow: 0 0.0312em 0.062em rgba(0, 0, 0, 0.25) inset;
}
.theme-switch__spot:nth-of-type(2) {
  width: 0.375em;
  height: 0.375em;
  top: 0.937em;
  left: 1.375em;
}
.theme-switch__spot:nth-last-of-type(3) {
  width: 0.25em;
  height: 0.25em;
  top: 0.312em;
  left: 0.812em;
}
.theme-switch__clouds {
  width: 1.25em;
  height: 1.25em;
  background-color: var(--clouds-color);
  border-radius: var(--container-radius);
  position: absolute;
  bottom: -0.625em;
  left: 0.312em;
  box-shadow:
    0.937em 0.312em var(--clouds-color),
    -0.312em -0.312em var(--back-clouds-color),
    1.437em 0.375em var(--clouds-color),
    0.5em -0.125em var(--back-clouds-color),
    2.187em 0 var(--clouds-color),
    1.25em -0.062em var(--back-clouds-color),
    2.937em 0.312em var(--clouds-color),
    2em -0.312em var(--back-clouds-color),
    3.625em -0.062em var(--clouds-color),
    2.625em 0 var(--back-clouds-color),
    4.5em -0.312em var(--clouds-color),
    3.375em -0.437em var(--back-clouds-color),
    4.625em -1.75em 0 0.437em var(--clouds-color),
    4em -0.625em var(--back-clouds-color),
    4.125em -2.125em 0 0.437em var(--back-clouds-color);
  transition: 0.5s cubic-bezier(0, -0.02, 0.4, 1.25);
}
.theme-switch__stars-container {
  position: absolute;
  color: var(--stars-color);
  top: -100%;
  left: 0.312em;
  width: 2.75em;
  height: auto;
  transition: var(--transition);
}
.theme-switch__checkbox:checked + .theme-switch__container {
  background-color: var(--container-night-bg);
}
.theme-switch__checkbox:checked
  + .theme-switch__container
  .theme-switch__circle-container {
  left: calc(
    100% - var(--circle-container-offset) - var(--circle-container-diameter)
  );
}
.theme-switch__checkbox:checked
  + .theme-switch__container
  .theme-switch__circle-container:hover {
  left: calc(
    100% - var(--circle-container-offset) - var(--circle-container-diameter) -
      0.187em
  );
}
.theme-switch__circle-container:hover {
  left: calc(var(--circle-container-offset) + 0.187em);
}
.theme-switch__checkbox:checked + .theme-switch__container .theme-switch__moon {
  transform: translate(0);
}
.theme-switch__checkbox:checked
  + .theme-switch__container
  .theme-switch__clouds {
  bottom: -4.062em;
}
.theme-switch__checkbox:checked
  + .theme-switch__container
  .theme-switch__stars-container {
  top: 50%;
  transform: translateY(-50%);
}

/* 搜索 */
.search-area {
  display: flex;
  align-items: center;
}
.search-box {
  overflow: hidden;
  width: 0;
  transition: width 0.3s ease;
}
.search-box.expanded {
  width: 180px;
}
.search-input {
  width: 100%;
  border: 1px solid var(--blog-border);
  border-radius: 4px;
  padding: 5px 10px;
  font-size: 13px;
  background: var(--blog-card);
  color: var(--blog-text);
  outline: none;
  font-family: inherit;
}
.search-input:focus {
  border-color: #000;
}
.dark .search-input:focus {
  border-color: #aaa;
}
.search-toggle {
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 16px;
  transition: color 0.2s;
  display: flex;
  align-items: center;
}
.search-toggle:hover {
  color: #fff;
}
.scrolled .search-toggle {
  color: #606266;
}
.scrolled .search-toggle:hover {
  color: #000;
}
.dark.scrolled .search-toggle {
  color: #b0b0b0;
}
.dark.scrolled .search-toggle:hover {
  color: #fff;
}

/* 移动端 */
.mobile-menu-btn {
  display: none;
  background: none;
  border: none;
  cursor: pointer;
  width: 28px;
  height: 28px;
  position: relative;
}
.bar,
.bar::before,
.bar::after {
  display: block;
  width: 18px;
  height: 2px;
  background: #fff;
  position: absolute;
  left: 5px;
  transition:
    transform 0.2s,
    background 0.3s;
}
.scrolled .bar,
.scrolled .bar::before,
.scrolled .bar::after {
  background: #303133;
}
.dark.scrolled .bar,
.dark.scrolled .bar::before,
.dark.scrolled .bar::after {
  background: #e5e5e5;
}
.bar {
  top: 13px;
}
.bar::before {
  content: '';
  top: -6px;
}
.bar::after {
  content: '';
  top: 6px;
}
.bar.open {
  background: transparent;
}
.bar.open::before {
  top: 0;
  transform: rotate(45deg);
}
.bar.open::after {
  top: 0;
  transform: rotate(-45deg);
}
.nav-mobile {
  display: none;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  padding: 8px 24px 12px;
  border-top: 1px solid #ebeef5;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}
.dark .nav-mobile {
  background: rgba(35, 35, 35, 0.96);
  border-top-color: #333;
}
.nav-mobile-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 0;
  font-size: 14px;
  color: #606266;
  text-decoration: none;
  border-bottom: 1px solid #f2f6fc;
  cursor: pointer;
}
.nav-mobile-link .iconfont {
  font-size: 15px;
}
.nav-mobile-link .nav-mobile-svg-icon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}
.nav-mobile-link:hover {
  color: #303133;
}
.dark .nav-mobile-link {
  color: #b0b0b0;
  border-bottom-color: #333;
}
.dark .nav-mobile-link:hover {
  color: #e5e5e5;
}

.site-header.compact .nav-desktop {
  position: absolute;
  visibility: hidden;
  pointer-events: none;
  opacity: 0;
}
.site-header.compact .mini-player-wrap {
  position: absolute;
  visibility: hidden;
  pointer-events: none;
  opacity: 0;
}
.site-header.compact .theme-switch {
  position: absolute;
  visibility: hidden;
  pointer-events: none;
  opacity: 0;
}
.site-header.compact .search-box.expanded {
  width: 120px;
}
.site-header.compact .mobile-menu-btn {
  display: block;
}
.site-header.compact .nav-mobile {
  display: flex;
  flex-direction: column;
}
.site-header.compact .header-inner {
  padding: 0 16px;
}
</style>
