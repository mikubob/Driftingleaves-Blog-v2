<script setup>
import RequestFailure from './shared/RequestFailure.vue'
import { setRequestFailureHandler } from './shared/requestFailure.mjs'
import { onBeforeUnmount, ref } from 'vue'
import { ElConfigProvider } from 'element-plus'
import 'element-plus/es/components/config-provider/style/css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useThemeStore } from './stores/modules/theme.js'
useThemeStore()
const requestFailure = ref(null)
setRequestFailureHandler((error) => {
  requestFailure.value ??= error
})
onBeforeUnmount(() => setRequestFailureHandler())
</script>

<template>
  <RequestFailure v-if="requestFailure" :error="requestFailure" />
  <div v-else>
    <!-- 一级路由入口 -->
    <el-config-provider :locale="zhCn">
      <router-view />
    </el-config-provider>
  </div>
</template>

<style scoped></style>
