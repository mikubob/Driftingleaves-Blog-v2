<script setup>
import { useRoute } from 'vue-router'
import AdminIcon from './AdminIcon.vue'
import { pageRegistry } from '../navigation.js'
defineProps({ nodes: { type: Array, required: true } })
const route = useRoute()
</script>

<template>
  <template v-for="node in nodes" :key="node.menuId">
    <template v-if="node.menuType === 'D'">
      <div class="nav-group-title">{{ node.menuName }}</div>
      <NavMenu :nodes="node.children" />
    </template>
    <router-link
      v-else
      :to="node.path"
      :title="node.menuName"
      :class="[
        'nav-item',
        { active: route.path === node.path || route.path.startsWith(`${node.path}/`) },
      ]"
    >
      <AdminIcon :name="pageRegistry[node.component]?.icon || node.icon" />
      <span class="nav-label">{{ node.menuName }}</span>
    </router-link>
  </template>
</template>

<style scoped>
.nav-group-title {
  padding: 12px 20px 6px;
  color: #909399;
  font-size: 11px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background-color: #f5f7fa;
  color: #303133;
}

.nav-item.active {
  background-color: #f5f7fa;
  color: #000;
  font-weight: 600;
}

.nav-item :deep(.iconfont),
.nav-item :deep(.el-icon) {
  font-size: 18px;
  flex-shrink: 0;
}

@media (max-width: 1024px) {
  .nav-item {
    padding: 11px 18px;
  }
}

@media (max-width: 768px) {
  .nav-item {
    padding: 12px 18px;
  }
}
</style>
