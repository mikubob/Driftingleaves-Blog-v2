<script setup>
import RequestFailure from './components/RequestFailure.vue'
import { setRequestFailureHandler } from '../../shared/requestFailure.mjs'
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
const requestFailure = ref(false)
setRequestFailureHandler(() => {
  if (requestFailure.value) {
    return
  }
  requestFailure.value = true
  document.title = '404 - 页面不存在'
})
onBeforeUnmount(() => setRequestFailureHandler())
</script>

<template>
  <RequestFailure v-if="requestFailure" />
  <div v-else>
    <router-view />
  </div>
</template>
