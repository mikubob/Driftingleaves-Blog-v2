<script setup>
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session } from '../session.js'
import AuthForm from '../components/AuthForm.vue'
const route = useRoute()
const router = useRouter()

function authenticated() {
  const target = route.query.redirect
  if (typeof target === 'string' && /^\/(?!\/)/.test(target) && !target.includes('\\')
    && !target.startsWith('/login') && !target.startsWith('/oauth/')) {
    router.replace(target)
  } else {
    router.replace('/account')
  }
}

watch(() => session.user?.userId, (userId) => {
  if (userId) {
    authenticated()
  }
}, { immediate: true })
</script>

<template>
  <section class="panel auth-panel">
    <p class="eyebrow">WELCOME TO THE JOURNAL</p>
    <h2>很高兴，又见到你</h2>
    <p class="muted">登录后，可以点赞、评论，和这里保持联系。</p>
    <div v-if="session.user">
      <p>已登录为 {{ session.user.nickname }}。</p>
      <router-link to="/account" class="button primary">进入个人中心 →</router-link>
    </div>
    <AuthForm v-else />
  </section>
</template>
