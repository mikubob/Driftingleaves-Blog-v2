<script setup>
import { ElEmpty } from 'element-plus'
import 'element-plus/es/components/empty/style/css'
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
        <h1>你好，{{ session.user?.nickname }}</h1>
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
