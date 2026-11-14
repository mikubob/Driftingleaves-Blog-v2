<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElDrawer, ElMessage } from 'element-plus'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/message/style/css'
import { logout, session } from '@/session.js'
import { useBlogStore, useThemeStore } from '@/stores'
import { loadLyrics } from '@/shared/lyrics.js'

const router = useRouter()
const route = useRoute()
const blogStore = useBlogStore()
const themeStore = useThemeStore()

const isDark = computed(() => themeStore.isDark)

/* 滚动检测 */
const scrolled = ref(false)
const handleScroll = () => {
  scrolled.value = window.scrollY > 60
}
onMounted(() => {
  handleScroll()
  window.addEventListener('scroll', handleScroll, { passive: true })
})

/* 导航布局 */
const mobileNavVisible = ref(false)
const compactNav = ref(false)
const openPopover = ref('')
const signingOut = ref(false)
const failedAvatarUrl = ref('')
const headerInnerRef = ref(null)
const headerRightRef = ref(null)
const siteTitleRef = ref(null)
const navDesktopRef = ref(null)
const accountControlRef = ref(null)
const miniPlayerWrapRef = ref(null)
const themeSwitchRef = ref(null)

/* 音乐播放 */
const isPlaying = ref(false)
const audioRef = ref(null)
const musicIndex = ref(0)
const musicListVisible = ref(false)
const lyrics = ref([])
const lyricLoading = ref(false)
const lyricError = ref('')
const playbackTime = ref(0)
let lyricController

const currentTrack = computed(() => blogStore.musics[musicIndex.value] || null)
const currentLyric = computed(() => {
  let text = '♪'
  for (const line of lyrics.value) {
    if (line.time > playbackTime.value) {
      break
    }
    text = line.text || '♪'
  }
  return text
})

async function loadCurrentLyrics() {
  lyricController?.abort()
  lyrics.value = []
  lyricError.value = ''
  lyricLoading.value = false
  const track = currentTrack.value
  if (!musicListVisible.value || !track?.lyricUrl) {
    return
  }
  const controller = new AbortController()
  lyricController = controller
  lyricLoading.value = true
  try {
    const lines = await loadLyrics(track.lyricUrl, track.lyricType, controller.signal)
    if (!controller.signal.aborted) {
      lyrics.value = lines
    }
  } catch (failure) {
    if (!controller.signal.aborted) {
      lyricError.value = failure.message
    }
  } finally {
    if (!controller.signal.aborted) {
      lyricLoading.value = false
    }
  }
}

watch([() => currentTrack.value?.id, musicListVisible], loadCurrentLyrics)
onUnmounted(() => lyricController?.abort())
let headerResizeObserver = null

const syncHeaderLayout = () => {
  nextTick(() => {
    const headerInnerEl = headerInnerRef.value
    const siteTitleEl = siteTitleRef.value?.$el ?? siteTitleRef.value
    const navDesktopEl = navDesktopRef.value
    const headerRightEl = headerRightRef.value
    if (!headerInnerEl || !siteTitleEl || !navDesktopEl || !headerRightEl) {
      return
    }

    const titleWidth = siteTitleEl.getBoundingClientRect().width
    const navWidth = navDesktopEl.getBoundingClientRect().width
    // 窄屏时桌面工具仍保留测量宽度，放宽窗口后可以恢复桌面导航。
    const rightItems = [miniPlayerWrapRef.value, themeSwitchRef.value, accountControlRef.value].filter(Boolean)
    const rightGap = parseFloat(getComputedStyle(headerRightEl).gap)
    const rightWidth = rightItems.reduce((sum, item) => sum + item.getBoundingClientRect().width, 0)
      + (rightItems.length - 1) * rightGap
    const headerStyle = getComputedStyle(headerInnerEl)
    const padding = 2 * parseFloat(headerStyle.getPropertyValue('--desktop-header-padding'))
    const requiredWidth = navWidth + 2 * Math.max(titleWidth, rightWidth) + 2 * parseFloat(headerStyle.columnGap) + padding

    compactNav.value = requiredWidth > headerInnerEl.clientWidth
  })
}

const togglePlay = async () => {
  const audio = audioRef.value
  if (!audio || !currentTrack.value) {
    return
  }
  if (!audio.paused) {
    audio.pause()
    return
  }
  try {
    await audio.play()
  } catch (failure) {
    if (failure.name !== 'AbortError') {
      ElMessage.error('音频暂时无法播放，请重试或切换曲目')
    }
  }
}

const playTrack = (index) => {
  musicIndex.value = index
  playbackTime.value = 0
  isPlaying.value = false
  nextTick(() => {
    if (audioRef.value) {
      audioRef.value.load()
      audioRef.value.play().catch((failure) => {
        if (failure.name !== 'AbortError') {
          ElMessage.error('音频暂时无法播放，请切换曲目')
        }
      })
    }
  })
}

const nextTrack = () => {
  if (!blogStore.musics.length) {
    return
  }
  playTrack((musicIndex.value + 1) % blogStore.musics.length)
}

const toggleMusicList = () => {
  musicListVisible.value = !musicListVisible.value
}

const readingLinks = [
  { label: '博客', to: '/', icon: 'icon-boke' },
  { label: '归档', to: '/archive', icon: 'icon-guidang' },
  { label: '热门', to: '/hot', icon: 'fire' },
  { label: '关于', to: '/about', icon: 'icon-guanyu' },
]
const exploreLinks = computed(() => [
  { label: '主页', href: blogStore.personalInfo.website, icon: 'icon-zhuye' },
  { label: '友链', to: '/links', icon: 'icon-lianjie' },
  { label: '留言', to: '/message', icon: 'icon-liuyan' },
  { label: '开往', href: 'https://www.travellings.cn/go.html', icon: 'icon-subway' },
].filter((item) => item.to || item.href))
const drawerGroups = computed(() => [
  { label: '阅读', links: readingLinks },
  { label: '探索', links: exploreLinks.value },
])

function togglePopover(name) {
  musicListVisible.value = false
  openPopover.value = openPopover.value === name ? '' : name
}

function closePopoverOnFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) {
    openPopover.value = ''
  }
}

function closePopoverOnEscape(event) {
  if (openPopover.value) {
    openPopover.value = ''
    event.currentTarget.querySelector('button').focus()
  }
}

function closePopoverOnOutsideClick(event) {
  if (openPopover.value && !event.target.closest('[data-header-popover]')) {
    openPopover.value = ''
  }
}

function openMobileNav() {
  openPopover.value = ''
  mobileNavVisible.value = true
}

async function signOut() {
  signingOut.value = true
  try {
    await logout()
    await router.push('/')
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    signingOut.value = false
  }
}

watch(() => route.fullPath, () => {
  openPopover.value = ''
  mobileNavVisible.value = false
})

watch(() => session.user, (user) => {
  if (!user && openPopover.value === 'account') {
    openPopover.value = ''
  }
})

watch(compactNav, (compact) => {
  if (compact) {
    musicListVisible.value = false
    if (openPopover.value === 'explore') {
      openPopover.value = ''
    }
  } else {
    mobileNavVisible.value = false
  }
})

watch(currentTrack, () => syncHeaderLayout())

const handleWindowResize = () => {
  syncHeaderLayout()
}

onMounted(() => {
  window.addEventListener('resize', handleWindowResize, { passive: true })
  document.addEventListener('pointerdown', closePopoverOnOutsideClick)
  syncHeaderLayout()

  if (typeof ResizeObserver !== 'undefined') {
    headerResizeObserver = new ResizeObserver(() => {
      syncHeaderLayout()
    })
    for (const target of [headerInnerRef.value, headerRightRef.value, navDesktopRef.value, siteTitleRef.value?.$el]) {
      if (target) {
        headerResizeObserver.observe(target)
      }
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleWindowResize)
  document.removeEventListener('pointerdown', closePopoverOnOutsideClick)
  headerResizeObserver?.disconnect()
  headerResizeObserver = null
})
</script>

<template>
  <header
    class="site-header"
    :class="{ scrolled, dark: isDark, compact: compactNav }"
  >
    <svg class="nav-icon-definitions" width="0" height="0" aria-hidden="true">
      <symbol id="blog-nav-fire" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
        stroke-linecap="round" stroke-linejoin="round">
        <path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z" />
      </symbol>
    </svg>
    <div ref="headerInnerRef" class="header-inner">
      <router-link ref="siteTitleRef" to="/" class="site-title">
        {{ blogStore.site.title || 'DriftingLeaves Blog' }}
      </router-link>
      <nav ref="navDesktopRef" class="nav-desktop" aria-label="博客导航">
        <router-link v-for="item in readingLinks" :key="item.to" :to="item.to" class="nav-link">
          <svg v-if="item.icon === 'fire'" class="nav-item-icon" aria-hidden="true">
            <use href="#blog-nav-fire" />
          </svg>
          <i v-else :class="['nav-item-icon', 'iconfont', item.icon]" aria-hidden="true" />
          {{ item.label }}
        </router-link>
        <div
          class="header-popover"
          data-header-popover
          @focusout="closePopoverOnFocusOut"
          @keydown.esc="closePopoverOnEscape"
        >
          <button
            type="button"
            class="nav-link"
            aria-controls="blog-explore-menu"
            :aria-expanded="openPopover === 'explore'"
            @click="togglePopover('explore')"
          >
            探索 <span class="nav-chevron" aria-hidden="true" />
          </button>
          <Transition name="fade">
            <div
              v-show="openPopover === 'explore'"
              id="blog-explore-menu"
              class="header-popover-panel"
              :inert="openPopover !== 'explore'"
            >
              <component
                :is="item.href ? 'a' : RouterLink"
                v-for="item in exploreLinks"
                :key="item.label"
                v-bind="item.href ? { href: item.href, target: '_blank', rel: 'noopener noreferrer' } : { to: item.to }"
                class="header-popover-item"
                @click="openPopover = ''"
              >
                <i :class="['nav-item-icon', 'iconfont', item.icon]" aria-hidden="true" />
                {{ item.label }} <span v-if="item.href" aria-label="在新标签页打开">↗</span>
              </component>
            </div>
          </Transition>
        </div>
      </nav>

      <div ref="headerRightRef" class="header-right">
        <div
          v-if="currentTrack"
          ref="miniPlayerWrapRef"
          class="mini-player-wrap"
        >
          <button
            type="button"
            class="mini-player"
            aria-label="播放列表"
            :aria-expanded="musicListVisible"
            @click="toggleMusicList"
          >
            <img
              v-if="currentTrack.coverImage"
              :src="currentTrack.coverImage"
              alt=""
              class="player-cover"
              :class="{ spinning: isPlaying }"
            />
            <span class="player-title">{{ currentTrack.title }}</span>
          </button>
          <button class="player-btn" @click.stop="togglePlay" title="播放/暂停">
            <i
              aria-hidden="true"
              class="iconfont"
              :class="isPlaying ? 'icon-zanting' : 'icon-play-full'"
            />
          </button>
          <button class="player-btn" @click.stop="nextTrack" title="下一首">
            <i aria-hidden="true" class="iconfont icon-next" />
          </button>
          <audio
            ref="audioRef"
            :src="currentTrack.musicUrl"
            preload="none"
            @ended="nextTrack"
            @timeupdate="playbackTime = $event.target.currentTime"
            @play="isPlaying = true"
            @pause="isPlaying = false"
            @error="isPlaying = false"
          />
          <transition name="fade">
            <div v-show="musicListVisible" class="music-panel">
              <div class="music-panel-header">
                <span><i aria-hidden="true" class="iconfont icon-yinle" /> 播放列表</span>
                <span class="music-panel-count">
                  {{ blogStore.musics.length }} 首
                </span>
              </div>
              <div v-if="currentTrack.lyricUrl" class="player-lyrics" aria-label="当前曲目歌词">
                <p v-if="lyricLoading">正在加载歌词…</p>
                <p v-else-if="lyricError" role="alert">
                  {{ lyricError }}
                  <button type="button" @click="loadCurrentLyrics">重试</button>
                </p>
                <pre v-else-if="currentTrack.lyricType === 'txt'">{{ lyrics[0]?.text }}</pre>
                <p v-else>{{ currentLyric }}</p>
              </div>
              <ul class="music-panel-list">
                <li
                  v-for="(m, idx) in blogStore.musics"
                  :key="m.id"
                >
                  <button
                    type="button"
                    class="music-panel-item"
                    :class="{ active: idx === musicIndex }"
                    :aria-label="`播放 ${m.title}`"
                    :aria-pressed="idx === musicIndex && isPlaying"
                    @click="playTrack(idx)"
                  >
                    <img
                      v-if="m.coverImage"
                      :src="m.coverImage"
                      alt=""
                      class="music-panel-cover"
                    />
                    <span class="music-panel-info">
                      <span class="music-panel-name">{{ m.title }}</span>
                      <span class="music-panel-artist">{{ m.artist }}</span>
                    </span>
                    <i
                      aria-hidden="true"
                      v-if="idx === musicIndex && isPlaying"
                      class="iconfont icon-yinle playing-icon"
                    />
                  </button>
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

        <button
          type="button"
          class="mobile-menu-btn"
          :class="{ 'is-open': mobileNavVisible }"
          :aria-label="mobileNavVisible ? '关闭栏目菜单' : '打开栏目菜单'"
          aria-controls="blog-mobile-drawer"
          :aria-expanded="mobileNavVisible"
          @click="mobileNavVisible = !mobileNavVisible"
        >
          <span class="bar" aria-hidden="true" />
        </button>
        <div
          ref="accountControlRef"
          class="header-popover"
          data-header-popover
          @focusout="closePopoverOnFocusOut"
          @keydown.esc="closePopoverOnEscape"
        >
          <template v-if="session.user">
            <button
              type="button"
              class="account-trigger"
              aria-label="打开账户菜单"
              aria-controls="blog-account-menu"
              :aria-expanded="openPopover === 'account'"
              @click="togglePopover('account')"
            >
              <img
                v-if="session.user.avatar && session.user.avatar !== failedAvatarUrl"
                :src="session.user.avatar"
                alt=""
                @error="failedAvatarUrl = session.user.avatar"
              />
              <i v-else class="iconfont icon-user" aria-hidden="true" />
            </button>
            <Transition name="fade">
              <div
                v-show="openPopover === 'account'"
                id="blog-account-menu"
                class="header-popover-panel account-panel"
                :inert="openPopover !== 'account'"
              >
                <strong class="account-name">{{ session.user.nickname }}</strong>
                <router-link to="/account" class="header-popover-item" @click="openPopover = ''">
                  个人中心
                </router-link>
                <button type="button" class="header-popover-item" :disabled="signingOut" @click="signOut">
                  {{ signingOut ? '正在退出…' : '退出登录' }}
                </button>
              </div>
            </Transition>
          </template>
          <router-link v-else to="/login" class="account-trigger login-link">
            <i class="nav-item-icon iconfont icon-user" aria-hidden="true" /> 登录
          </router-link>
        </div>
      </div>
    </div>
  </header>

  <ElDrawer
    id="blog-mobile-drawer"
    v-model="mobileNavVisible"
    title="导航"
    direction="rtl"
    size="min(84vw, 340px)"
    class="blog-nav-drawer"
    modal-class="blog-nav-overlay"
    header-class="blog-nav-drawer-header"
    body-class="blog-nav-drawer-body"
    :append-to-body="true"
    :lock-scroll="false"
    :show-close="false"
    @close="mobileNavVisible = false"
  >
    <template #header="{ close }">
      <strong>导航</strong>
      <button type="button" class="drawer-close" aria-label="关闭栏目菜单" @click="close">×</button>
    </template>
    <nav class="drawer-nav" aria-label="博客栏目">
      <div v-for="group in drawerGroups" :key="group.label">
        <h2>{{ group.label }}</h2>
        <div class="drawer-links">
          <component
            :is="item.href ? 'a' : RouterLink"
            v-for="item in group.links"
            :key="item.label"
            v-bind="item.href ? { href: item.href, target: '_blank', rel: 'noopener noreferrer' } : { to: item.to }"
            class="drawer-link"
            @click="mobileNavVisible = false"
          >
            <svg v-if="item.icon === 'fire'" class="nav-item-icon" aria-hidden="true">
              <use href="#blog-nav-fire" />
            </svg>
            <i v-else :class="['nav-item-icon', 'iconfont', item.icon]" aria-hidden="true" />
            {{ item.label }} <span v-if="item.href" aria-label="在新标签页打开">↗</span>
          </component>
        </div>
      </div>
    </nav>
    <div class="drawer-tools">
      <button
        type="button"
        :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
        @click="themeStore.toggle"
      >
        <span aria-hidden="true">{{ isDark ? '☾' : '☀' }}</span>
        {{ isDark ? '深色' : '浅色' }}
      </button>
      <template v-if="currentTrack">
        <button
          type="button"
          class="drawer-track"
          :aria-label="`${isPlaying ? '暂停' : '播放'} ${currentTrack.title}`"
          @click="togglePlay"
        >
          <i aria-hidden="true" class="iconfont" :class="isPlaying ? 'icon-zanting' : 'icon-play-full'" />
          <span>{{ currentTrack.title }}</span>
        </button>
        <button type="button" aria-label="下一首" @click="nextTrack">
          <i aria-hidden="true" class="iconfont icon-next" />
        </button>
      </template>
    </div>
  </ElDrawer>
</template>

<style scoped>
.site-header {
  --header-action: #fff;
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
  --header-action: var(--blog-text);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
}
.site-header.dark.scrolled {
  background: rgba(35, 35, 35, 0.95);
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.3);
}
.header-inner {
  --desktop-header-padding: 28px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--desktop-header-padding);
  height: 58px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) max-content minmax(0, 1fr);
  align-items: center;
  column-gap: 12px;
}
.site-title {
  justify-self: start;
  min-width: 0;
  max-width: min(100%, 240px);
  overflow: hidden;
  text-overflow: ellipsis;
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
  width: max-content;
}
.nav-icon-definitions {
  position: absolute;
}
.nav-item-icon {
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
  font-size: 14px;
  line-height: 1;
}
.nav-link {
  border: 0;
  background: transparent;
  font-family: inherit;
  font-size: 13px;
  line-height: 1;
  color: rgba(255, 255, 255, 0.9);
  text-decoration: none;
  height: 32px;
  padding: 0 10px;
  border-radius: 4px;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  box-sizing: border-box;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  transition:
    color 0.2s,
    background 0.2s,
    text-shadow 0.3s;
}
.nav-link:hover,
.nav-link[aria-expanded='true'],
.nav-link.router-link-exact-active {
  color: #fff;
  background: rgba(255, 255, 255, 0.15);
}
.scrolled .nav-link {
  color: #606266;
  text-shadow: none;
}
.scrolled .nav-link:hover,
.scrolled .nav-link[aria-expanded='true'],
.scrolled .nav-link.router-link-exact-active {
  color: #303133;
  background: #f5f7fa;
}
.dark.scrolled .nav-link {
  color: #b0b0b0;
}
.dark.scrolled .nav-link:hover,
.dark.scrolled .nav-link[aria-expanded='true'],
.dark.scrolled .nav-link.router-link-exact-active {
  color: #e5e5e5;
  background: #333;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  justify-self: end;
}

.header-popover {
  position: relative;
  flex-shrink: 0;
}
.nav-chevron {
  width: 6px;
  height: 6px;
  margin: 0 1px 3px 3px;
  border: solid currentColor;
  border-width: 0 1px 1px 0;
  transform: rotate(45deg);
}
.header-popover-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 2;
  display: grid;
  min-width: 128px;
  padding: 6px;
  border: 1px solid var(--blog-border);
  border-radius: 10px;
  background: var(--blog-card);
  color: var(--blog-text);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.14);
}
.account-panel {
  width: 168px;
}
.account-name {
  padding: 7px 9px;
  overflow-wrap: anywhere;
  border-bottom: 1px solid var(--blog-border);
  font-size: 14px;
}
.header-popover-item {
  display: flex;
  align-items: center;
  gap: 5px;
  min-height: 44px;
  padding: 0 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--blog-text);
  font: inherit;
  font-size: 14px;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}
.header-popover-item:hover,
.header-popover-item.router-link-exact-active {
  background: var(--blog-hover);
}
.header-popover-item:disabled {
  color: var(--blog-text3);
  cursor: wait;
}
.account-trigger {
  display: grid;
  place-items: center;
  min-width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 22px;
  background: transparent;
  color: var(--header-action);
  font: inherit;
  text-decoration: none;
  cursor: pointer;
}
.account-trigger img,
button.account-trigger .iconfont {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--blog-border);
  border-radius: 50%;
  background: var(--blog-card);
  color: var(--blog-text);
  object-fit: cover;
}
.login-link {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  padding: 0 6px;
  font-size: 14px;
}

/* 迷你播放器 */
.mini-player-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
  position: relative;
  flex-shrink: 0;
}
.mini-player {
  font: inherit;
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

/* 音乐列表 */
.music-panel {
  display: flex;
  flex-direction: column;
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
  color: var(--blog-text3);
  font-weight: 400;
}
.music-panel-list {
  min-height: 0;
  list-style: none;
  margin: 0;
  padding: 4px 0;
  max-height: 300px;
  overflow-y: auto;
}
.player-lyrics {
  flex-shrink: 0;
  padding: 8px 14px;
  max-height: 120px;
  overflow: auto;
  color: var(--blog-text);
  font-size: 12px;
  border-bottom: 1px solid var(--blog-border);
}
.player-lyrics p,
.player-lyrics pre {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
}
.music-panel-item {
  width: 100%;
  border: 0;
  background: transparent;
  font: inherit;
  text-align: left;
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
  color: var(--blog-text3);
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
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
.theme-switch__checkbox:focus-visible + .theme-switch__container {
  outline: 2px solid currentColor;
  outline-offset: -1px;
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

/* 移动端 */
.mobile-menu-btn {
  position: relative;
  display: none;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--header-action);
  cursor: pointer;
}
.bar,
.bar::before,
.bar::after {
  position: absolute;
  width: 18px;
  height: 2px;
  border-radius: 2px;
  background: currentColor;
  transition:
    transform 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    background-color 0.2s ease,
    opacity 0.2s ease;
}
.bar {
  top: 21px;
  left: 13px;
}
.bar::before,
.bar::after {
  left: 0;
  content: '';
}
.bar::before {
  top: -6px;
}
.bar::after {
  top: 6px;
}
/* 汉堡变叉：打开时上下两条横线旋转交叉成叉，中间一条淡出；关闭时反向还原。 */
.mobile-menu-btn.is-open .bar {
  background-color: transparent;
}
.mobile-menu-btn.is-open .bar::before {
  transform: translateY(6px) rotate(45deg);
}
.mobile-menu-btn.is-open .bar::after {
  transform: translateY(-6px) rotate(-45deg);
}

.site-header.compact .nav-desktop,
.site-header.compact .mini-player-wrap,
.site-header.compact .theme-switch {
  position: absolute;
  visibility: hidden;
  pointer-events: none;
}
.site-header.compact .mobile-menu-btn {
  display: block;
}
.site-header.compact .header-inner {
  display: flex;
  justify-content: space-between;
  padding: 0 12px;
}

/* 页面由 html 滚动，抽屉收回后再恢复背景滚动。 */
:global(html:has(.blog-nav-drawer.open, .blog-nav-overlay.el-drawer-fade-leave-active)) {
  overflow-y: hidden;
  scrollbar-gutter: stable;
}
:global(.blog-nav-overlay.el-drawer-fade-enter-active) {
  --el-transition-duration: 260ms;
}
:global(.blog-nav-overlay.el-drawer-fade-leave-active) {
  --el-transition-duration: 200ms;
}
:global(.blog-nav-overlay.el-drawer-fade-enter-from),
:global(.blog-nav-overlay.el-drawer-fade-leave-to) {
  opacity: 0;
}
:global(.blog-nav-drawer.el-drawer) {
  --el-drawer-bg-color: var(--blog-card);
  border-radius: 16px 0 0 16px;
  color: var(--blog-text);
  transition-property: transform;
  transition-timing-function: cubic-bezier(0.22, 1, 0.36, 1);
}
:global(.blog-nav-overlay.el-drawer-fade-leave-active .blog-nav-drawer) {
  transition-timing-function: ease-in;
}
:global(.blog-nav-drawer-header.el-drawer__header) {
  margin-bottom: 0;
  padding: max(8px, env(safe-area-inset-top)) max(12px, env(safe-area-inset-right)) 0 18px;
  color: var(--blog-text);
}
:global(.blog-nav-drawer-body.el-drawer__body) {
  padding: 6px max(12px, env(safe-area-inset-right)) max(16px, env(safe-area-inset-bottom)) 12px;
  overscroll-behavior: contain;
}
.drawer-close {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--blog-text2);
  font: 26px/1 var(--blog-sans);
  cursor: pointer;
}
.drawer-nav {
  display: grid;
  gap: 12px;
}
.drawer-nav h2 {
  margin: 0 0 4px;
  padding-inline: 8px;
  color: var(--blog-text3);
  font-size: 13px;
  font-weight: 500;
}
.drawer-links {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
}
.drawer-link,
.drawer-tools button {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 8px;
  border: 0;
  border-radius: 8px;
  color: var(--blog-text);
  font: inherit;
  font-size: 14px;
  text-decoration: none;
}
.drawer-link:hover,
.drawer-link:active,
.drawer-link.router-link-exact-active,
.drawer-close:hover,
.drawer-close:active {
  background: var(--blog-hover);
}
.drawer-link:active,
.drawer-close:active,
.drawer-tools button:active {
  transform: scale(0.97);
}
.drawer-link.router-link-exact-active {
  font-weight: 600;
}
.drawer-tools {
  display: flex;
  gap: 6px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--blog-border);
}
.drawer-tools button {
  flex-shrink: 0;
  justify-content: center;
  min-width: 44px;
  background: var(--blog-hover);
  cursor: pointer;
}
.drawer-tools .drawer-track {
  flex-shrink: 1;
}
.drawer-track span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@supports (corner-shape: squircle) {
  /* 主题切换开关内部图形、旋转唱片封面与用户头像保持正圆。 */
  .theme-switch,
  .theme-switch *,
  .theme-switch *::before,
  .theme-switch *::after,
  .player-cover,
  .account-trigger img,
  button.account-trigger .iconfont {
    corner-shape: round;
  }

  /* 弹出面板外圆角 = 内层菜单项圆角(6px) + 内边距(6px)，保持同心。 */
  .header-popover-panel {
    border-radius: 12px;
  }
}
</style>
