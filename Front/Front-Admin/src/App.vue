<script setup>
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import RequestFailure from './shared/RequestFailure.vue'
import { setRequestFailureHandler } from './shared/requestFailure.mjs'
import { onBeforeUnmount, ref, watch } from 'vue'
import { session } from './session.js'
import router from './router.js'

watch(
  () => session.ready,
  (ready, previous) => {
    if (previous && !ready) {
      router.replace('/login')
    }
  },
)
const requestFailure = ref(null)
setRequestFailureHandler((error) => {
  requestFailure.value ??= error
})
onBeforeUnmount(() => setRequestFailureHandler())
</script>

<template>
  <RequestFailure v-if="requestFailure" :error="requestFailure" />
  <ElConfigProvider v-else :locale="zhCn">
    <router-view />
  </ElConfigProvider>
</template>
