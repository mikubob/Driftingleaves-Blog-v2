<script setup>
import { onMounted } from 'vue'

/** 展示请求错误，路由没有传入错误时展示 404 页面。 */
const props = defineProps({ error: Error })
const title = props.error ? '请求失败' : '页面不存在'

onMounted(() => {
  document.title = props.error ? title : `404 - ${title}`
})
</script>

<template>
  <main class="error-page" role="alert">
    <div class="error-content">
      <h1 class="error-code">{{ error ? error.status ?? '!' : '404' }}</h1>
      <p class="error-title">{{ title }}</p>
      <p class="error-desc">{{ error?.message ?? '你访问的页面已经走丢了' }}</p>
      <a href="/" class="error-btn">返回首页</a>
    </div>
  </main>
</template>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cv-bg);
}
.error-content {
  text-align: center;
  padding: 40px 24px;
}
.error-code {
  font-size: 120px;
  font-weight: 900;
  color: var(--cv-text);
  margin: 0;
  line-height: 1;
  letter-spacing: -4px;
}
.error-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--cv-text);
  margin: 16px 0 8px;
}
.error-desc {
  font-size: 14px;
  color: var(--cv-muted);
  margin: 0 0 32px;
}
.error-btn {
  display: inline-block;
  padding: 10px 32px;
  font-size: 14px;
  color: var(--cv-bg);
  background: var(--cv-text);
  border-radius: 6px;
  text-decoration: none;
  font-weight: 500;
  transition: background 0.2s;
}
.error-btn:focus-visible {
  outline: 2px solid var(--cv-text);
  outline-offset: 3px;
}
.error-btn:hover {
  background: var(--cv-muted);
}

@media (max-width: 480px) {
  .error-code {
    font-size: 80px;
  }
  .error-title {
    font-size: 17px;
  }
}
</style>
