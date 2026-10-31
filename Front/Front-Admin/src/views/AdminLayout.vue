<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { session, logout } from '../session.js'
import { allowedPages } from '../navigation.js'
import NavMenu from '../components/NavMenu.vue'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const mobileOpen = ref(false)
const loggingOut = ref(false)
const groups = computed(() => {
  const pages = allowedPages(session.menus, session.capabilities).filter(
    (node) => node.isVisible === 1,
  )
  function visibleGroups(nodes) {
    const result = []
    for (const node of nodes) {
      if (node.status !== 1 || node.isVisible !== 1) {
        continue
      }
      if (node.menuType === 'D') {
        const children = visibleGroups(node.children || [])
        if (children.length) {
          result.push({ ...node, children })
        }
      } else if (pages.some((page) => page.menuId === node.menuId)) {
        result.push(node)
      }
    }
    return result
  }
  return visibleGroups(session.menus)
})

async function signOut() {
  try {
    await ElMessageBox.confirm('确认退出当前管理会话？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  loggingOut.value = true
  try {
    await logout()
    router.replace('/login')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loggingOut.value = false
  }
}

const isMobile = ref(false)

function syncViewport() {
  isMobile.value = window.matchMedia('(max-width: 768px)').matches
  if (!isMobile.value) {
    mobileOpen.value = false
  }
}

watch(() => route.fullPath, () => {
  mobileOpen.value = false
})
onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport, { passive: true })
})
onBeforeUnmount(() => window.removeEventListener('resize', syncViewport))
</script>

<template>
  <div class="admin-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <button
      v-show="mobileOpen"
      aria-label="关闭菜单"
      class="sidebar-scrim"
      @click="mobileOpen = false"
    />

    <!-- 左侧菜单 -->
    <aside :class="['sidebar', { collapsed, 'mobile-open': mobileOpen }]" aria-label="后台导航">
      <div class="sidebar-logo">
        <span v-if="!collapsed || isMobile" class="logo-text">管理控制台</span>
        <span v-else class="logo-icon">
          <span class="iconfont icon-guanliduan" aria-hidden="true" />
        </span>
      </div>

      <nav class="sidebar-nav">
        <NavMenu :nodes="groups" />
      </nav>

      <router-link class="nav-item account-link" to="/account" title="个人资料与安全" active-class="active">
        <span class="iconfont icon-iconfontprofile" aria-hidden="true" />
        <span v-if="!collapsed || isMobile" class="nav-label">个人资料与安全</span>
      </router-link>
      <button
        class="collapse-btn"
        type="button"
        :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
        :aria-expanded="!collapsed"
        @click="collapsed = !collapsed"
      >
        <span
          aria-hidden="true"
          :class="[
            'iconfont',
            collapsed ? 'icon-arrow-right-bold' : 'icon-arrow-left-bold'
          ]"
        />
      </button>
    </aside>

    <!-- 右侧主区域 -->
    <div class="main-wrapper">
      <!-- 顶部栏 -->
      <header class="topbar">
        <button
          class="mobile-menu-btn"
          type="button"
          aria-label="打开导航"
          :aria-expanded="mobileOpen"
          @click="mobileOpen = !mobileOpen"
        >
          <span class="iconfont icon-guanliduan" aria-hidden="true" />
        </button>
        <div class="topbar-breadcrumb">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }"
              >首页</el-breadcrumb-item
            >
            <el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="topbar-title">{{ $route.meta.title }}</div>
        <div class="topbar-right">
          <router-link class="user-name" to="/account">
            <span class="iconfont icon-user" aria-hidden="true" />
            {{ session.user?.nickname || '管理员' }}
          </router-link>
          <button class="logout-btn" aria-label="退出登录" :disabled="loggingOut" @click="signOut">
            <span class="iconfont icon-logout" aria-hidden="true" />
            <span class="logout-text">退出</span>
          </button>
        </div>
      </header>

      <!-- 页面内容 -->
      <main id="main-content" class="page-main" tabindex="-1">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <keep-alive>
              <component :is="Component" v-if="route.meta.keepAlive" :key="route.path" />
            </keep-alive>
          </transition>
          <transition name="page-fade" mode="out-in">
            <component :is="Component" v-if="!route.meta.keepAlive" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  height: 100vh;
  height: 100dvh;
  background-color: #f5f7fa;
  overflow: hidden;
}

.sidebar-scrim {
  display: none;
}

/* 侧边栏 */
.sidebar {
  width: 220px;
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease;
  flex-shrink: 0;
}

.sidebar.collapsed {
  width: 60px;
}

.sidebar-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #e4e7ed;
  font-size: 16px;
  font-weight: 700;
  color: #303133;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
}

.logo-icon .iconfont {
  font-size: 22px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}

.nav-item:hover {
  background-color: #f5f7fa;
  color: #303133;
}

.nav-item.active {
  background-color: #f5f7fa;
  color: #000000;
  font-weight: 600;
}

.nav-item .iconfont {
  font-size: 18px;
  flex-shrink: 0;
}

.collapse-btn {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-top: 1px solid #e4e7ed;
  background: transparent;
  cursor: pointer;
  color: #909399;
  transition: color 0.15s;
}

.collapse-btn:hover {
  color: #303133;
}

/* 顶部栏 */
.main-wrapper {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.topbar {
  height: 60px;
  background-color: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  gap: 16px;
}

.mobile-menu-btn,
.topbar-title {
  display: none;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #606266;
}

.user-name .iconfont {
  font-size: 16px;
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.15s;
}

.logout-btn:hover {
  border-color: #000000;
  color: #000000;
}

/* 主内容 */
.page-main {
  flex: 1;
  overflow-y: auto;
  padding: clamp(16px, 2vw, 24px);
}

.page-main.editor-page {
  padding: 0;
  overflow: hidden;
}

/* 路由切换过渡 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 1180px) {
  .sidebar {
    width: 208px;
  }

  .topbar {
    padding: 0 20px;
  }
}

@media (max-width: 1024px) {
  .sidebar {
    width: 200px;
  }

  .sidebar.collapsed {
    width: 56px;
  }

  .nav-item {
    padding: 11px 18px;
  }

  .topbar {
    padding: 0 18px;
  }
}

@media (max-width: 768px) {
  .sidebar-scrim {
    position: fixed;
    inset: 0;
    z-index: 900;
    display: block;
    background: rgba(0, 0, 0, 0.28);
  }

  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 1000;
    width: min(82vw, 300px) !important;
    transform: translateX(-100%);
    box-shadow: 12px 0 32px rgba(15, 23, 42, 0.16);
    transition: transform 0.24s ease;
  }

  .sidebar.mobile-open {
    transform: translateX(0);
  }

  .sidebar-logo {
    justify-content: flex-start;
    padding: 0 18px;
  }

  .nav-item {
    padding: 12px 18px;
  }

  .collapse-btn {
    display: none;
  }

  .topbar {
    height: 56px;
    padding: 0 14px;
  }

  .mobile-menu-btn {
    width: 36px;
    height: 36px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    background: #fff;
    color: #303133;
  }

  .mobile-menu-btn .iconfont {
    font-size: 18px;
  }

  .topbar-breadcrumb {
    display: none;
  }

  .topbar-title {
    display: block;
    flex: 1;
    min-width: 0;
    overflow: hidden;
    color: #303133;
    font-size: 15px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .topbar-right {
    gap: 8px;
  }

  .user-name {
    max-width: 108px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .logout-btn {
    width: 36px;
    height: 36px;
    justify-content: center;
    padding: 0;
  }

  .logout-text {
    display: none;
  }

  .page-main {
    padding: 14px;
  }

  .page-main.editor-page {
    padding: 0;
  }
}

@media (max-width: 520px) {
  .topbar {
    padding: 0 10px;
  }

  .user-name {
    max-width: 86px;
  }

  .page-main {
    padding: 10px;
  }

  .page-main.editor-page {
    padding: 0;
  }
}

.sidebar :deep(.nav-group-title) {
  padding: 12px 20px 6px;
  color: #909399;
  font-size: 11px;
}

.sidebar.collapsed :deep(.nav-label),
.sidebar.collapsed :deep(.nav-group-title) {
  display: none;
}

.account-link {
  border-top: 1px solid #e4e7ed;
}

.sidebar-scrim {
  padding: 0;
  border: 0;
}

@media (max-width: 768px) {
  .sidebar.collapsed :deep(.nav-label),
  .sidebar.collapsed :deep(.nav-group-title) {
    display: block;
  }
}
</style>
