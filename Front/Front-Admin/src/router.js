import { createRouter, createWebHistory } from 'vue-router'
import { apiRoot } from './api.js'
import { reportNotFound } from '../../shared/notFound.mjs'
import { allowedPages, pageRegistry } from './navigation.js'
import { session } from './session.js'
import RequestFailure from './components/RequestFailure.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/Login.vue') },
    { path: '/oauth/complete', component: () => import('./views/OAuthComplete.vue') },
    {
      path: '/',
      name: 'admin',
      component: () => import('./views/AdminLayout.vue'),
      children: [
        {
          path: '',
          name: 'welcome',
          component: () => import('./views/Welcome.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'account',
          name: 'account',
          component: () => import('./views/Account.vue'),
          meta: { title: '个人资料' },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: RequestFailure,
      meta: { title: '页面不存在' },
    },
  ],
})

const removeRoutes = []
export function registerMenuRoutes() {
  removeRoutes.splice(0).forEach((remove) => remove())
  for (const node of allowedPages(session.menus, session.capabilities)) {
    const page = pageRegistry[node.component]
    removeRoutes.push(
      router.addRoute('admin', {
        path: node.path.slice(1),
        name: `menu-${node.menuId}`,
        component: page.load,
        meta: {
          title: node.menuName,
          permission: page.permission,
          all: page.all,
          componentKey: node.component,
          keepAlive: node.keepAlive === 1,
        },
      }),
    )
    if (node.component === 'content/article/index') {
      removeRoutes.push(
        router.addRoute('admin', {
          path: `${node.path.slice(1)}/edit/:id?`,
          name: 'article-editor',
          component: () => import('./views/ArticleEditor.vue'),
          meta: {
            title: '文章编辑',
            permission: page.permission,
            componentKey: node.component,
            articleListRoute: `menu-${node.menuId}`,
          },
        }),
      )
    }
  }
}

export function firstPage() {
  const pages = allowedPages(session.menus, session.capabilities)
  return (
    pages.find((node) => node.component === 'statistics/overview/index')?.path ||
    pages.find((node) => node.isVisible === 1)?.path ||
    '/'
  )
}

router.beforeEach((to) => {
  if (to.path === '/oauth/complete') {
    return true
  }
  if (!session.ready && to.name !== 'login') {
    return { name: 'login' }
  }
  if (session.ready && to.name === 'login') {
    return firstPage()
  }
  if (to.meta.permission) {
    const permitted = allowedPages(session.menus, session.capabilities).some(
      (node) => node.component === to.meta.componentKey,
    )
    if (!permitted) {
      return '/'
    }
  }
  document.title = `${to.meta.title || '管理控制台'} · Drifting Leaves`
  return true
})

router.afterEach((to, from, failure) => {
  if (!failure && to.name === 'not-found') {
    void reportNotFound(apiRoot, 'admin')
  }
})

export default router
