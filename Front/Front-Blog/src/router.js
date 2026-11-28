import { createRouter, createWebHistory } from 'vue-router'
import { apiRoot } from './api.js'
import { ensureSessionRestored } from './session.js'
import { reportNotFound } from './shared/notFound.mjs'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: () => import('./view/Layout/index.vue'),
      children: [
        { path: '', name: 'home', component: () => import('./view/Home/index.vue'), meta: { title: '博客' } },
        { path: 'articles/:slug', name: 'article', component: () => import('./view/Article/index.vue'), meta: { title: '文章' } },
        { path: 'category/:slug', component: () => import('./view/Taxonomy/index.vue'), props: { kind: 'category' }, meta: { title: '分类' } },
        { path: 'tag/:slug', component: () => import('./view/Taxonomy/index.vue'), props: { kind: 'tag' }, meta: { title: '标签' } },
        { path: 'archive', component: () => import('./view/Archive/index.vue'), meta: { title: '归档' } },
        { path: 'archive/:month', component: () => import('./view/Home/index.vue'), meta: { title: '归档文章' } },
        { path: 'author/:userId', component: () => import('./view/Home/index.vue'), meta: { title: '作者文章' } },
        { path: 'hot', component: () => import('./view/Hot/index.vue'), meta: { title: '热门文章' } },
        { path: 'links', component: () => import('./view/Links/index.vue'), meta: { title: '友链' } },
        { path: 'message', component: () => import('./view/Message/index.vue'), meta: { title: '留言板' } },
        { path: 'about', component: () => import('./view/About/index.vue'), meta: { title: '关于' } },
        { path: 'category', component: () => import('./views/Directory.vue'), meta: { title: '分类', directory: 'categories', account: true } },
        { path: 'tag', component: () => import('./views/Directory.vue'), meta: { title: '标签', directory: 'tags', account: true } },
        { path: 'authors', component: () => import('./views/Directory.vue'), meta: { title: '作者', directory: 'authors', account: true } },
        { path: 'login', component: () => import('./views/Login.vue'), meta: { title: '登录', account: true, sensitive: true, requiresSession: true } },
        { path: 'account', component: () => import('./views/Account.vue'), meta: { title: '个人中心', account: true, requiresSession: true } },
        { path: 'oauth/complete', component: () => import('./views/OAuthComplete.vue'), meta: { title: '完成授权', account: true, sensitive: true } },
        { path: 'subscription/:action(confirm|unsubscribe)', component: () => import('./views/Subscription.vue'), meta: { title: '邮件订阅', account: true, sensitive: true } },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('./view/NotFound/index.vue') },
  ],
  scrollBehavior(to, from, saved) {
    if (saved) {
      return saved
    }
    if (to.path === from.path && to.hash) {
      return false
    }
    return { top: 0 }
  },
})

// 仅在依赖登录态的账号/登录/订阅确认页面等待首次会话恢复，公开页直接放行。
router.beforeEach(async (to) => {
  const needsSession =
    to.matched.some((record) => record.meta.requiresSession) ||
    to.params.action === 'confirm'
  if (needsSession) {
    await ensureSessionRestored()
  }
  return true
})

router.afterEach((to, from, failure) => {
  if (!failure && to.name === 'not-found') {
    void reportNotFound(apiRoot, 'blog')
  }
})
