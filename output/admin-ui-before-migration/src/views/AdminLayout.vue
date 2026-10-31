<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { session, logout } from '../session.js'
import { allowedPages } from '../navigation.js'
import AdminIcon from '../components/AdminIcon.vue'
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
</script>

<template>
  <div :class="['admin-shell', { collapsed, 'mobile-open': mobileOpen }]">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <button
      v-if="mobileOpen"
      class="sidebar-mask"
      aria-label="关闭菜单"
      @click="mobileOpen = false"
    ></button>
    <aside class="sidebar" aria-label="后台导航">
      <router-link class="sidebar-logo" to="/" title="Drifting Leaves 管理控制台">
        <span class="brand-symbol">
          D
          <span>↗</span>
        </span>
        <span class="brand-label">
          管理控制台
          <small>DRIFTING LEAVES</small>
        </span>
      </router-link>
      <nav class="sidebar-nav" @click="mobileOpen = false">
        <NavMenu :nodes="groups" />
      </nav>
      <router-link
        class="nav-item account-link"
        to="/account"
        title="个人资料"
        active-class="active"
      >
        <AdminIcon name="user" />
        <span class="nav-label">个人资料与安全</span>
      </router-link>
      <button
        class="collapse-button"
        :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
        :aria-expanded="!collapsed"
        @click="collapsed = !collapsed"
      >
        <AdminIcon :name="collapsed ? 'right' : 'left'" />
        <span class="nav-label">收起导航</span>
      </button>
    </aside>
    <div class="main-wrapper">
      <header class="topbar">
        <div class="topbar-start">
          <button class="mobile-menu icon-button" aria-label="打开导航" @click="mobileOpen = true">
            <AdminIcon name="menu" />
          </button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">控制台</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="topbar-right">
          <span class="session-indicator">
            <i></i>
            已登录
          </span>
          <router-link class="user-link" to="/account">
            <span class="avatar-letter">{{ session.user?.nickname?.slice(0, 1) }}</span>
            <span>{{ session.user?.nickname }}</span>
          </router-link>
          <el-button text :loading="loggingOut" @click="signOut">
            <AdminIcon name="logout" />
            <span>退出</span>
          </el-button>
        </div>
      </header>
      <main id="main-content" class="page-main" tabindex="-1">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" v-if="route.meta.keepAlive" :key="route.path" />
          </keep-alive>
          <component :is="Component" v-if="!route.meta.keepAlive" :key="route.path" />
        </router-view>
      </main>
      <footer class="page-footer">
        Drifting Leaves
        <span>·</span>
        管理控制台
      </footer>
    </div>
  </div>
</template>
