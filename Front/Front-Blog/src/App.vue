<script setup>
import RequestFailure from './components/RequestFailure.vue'
import { setRequestFailureHandler } from '../../shared/requestFailure.mjs'
import { onBeforeUnmount, ref } from 'vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
const requestFailure = ref('')
setRequestFailureHandler((error) => {
  if (requestFailure.value) {
    return
  }
  requestFailure.value = error.message
  document.title = '404 - 页面不存在'
})
onBeforeUnmount(() => setRequestFailureHandler())
</script>

<template>
  <RequestFailure v-if="requestFailure" :message="requestFailure" />
  <div v-else>
    <!-- 一级路由入口 -->
    <el-config-provider :locale="zhCn">
      <router-view />
    </el-config-provider>
  </div>
</template>

<style scoped></style>
