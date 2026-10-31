<script setup>
import { computed } from 'vue'
import { session } from '../session.js'
import { allowedPages } from '../navigation.js'
import AdminIcon from '../components/AdminIcon.vue'
const pages = computed(() =>
  allowedPages(session.menus, session.capabilities).filter((page) => page.isVisible === 1),
)
</script>

<template>
  <section>
    <div class="page-header">
      <div>
        <p class="eyebrow">WORKSPACE</p>
        <h1>你好，{{ session.user?.nickname }}</h1>
        <p class="muted-text">从这里开始，打理属于你的内容与站点。</p>
      </div>
    </div>
    <div v-if="pages.length" class="shortcut-grid">
      <router-link v-for="page in pages" :key="page.menuId" :to="page.path" class="shortcut-card">
        <AdminIcon :name="page.icon" />
        <strong>{{ page.menuName }}</strong>
        <AdminIcon name="right" />
      </router-link>
    </div>
    <el-empty v-else description="当前账号暂无可访问菜单，请联系管理员配置授权。" />
  </section>
</template>
