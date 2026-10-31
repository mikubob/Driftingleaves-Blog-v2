/** 数据库组件键只解析本地白名单，不能直接作为模块路径导入。 */
export const pageRegistry = {
  'content/article/index': {
    icon: 'document',
    title: '文章管理',
    permission: 'blog:article:query',
    load: () => import('./views/Articles.vue'),
  },
  'content/category/index': {
    icon: 'folder',
    title: '分类管理',
    permission: 'blog:category:query',
    load: () => import('./views/Categories.vue'),
  },
  'content/tag/index': {
    icon: 'tag',
    title: '标签管理',
    permission: 'blog:tag:query',
    load: () => import('./views/Tags.vue'),
  },
  'content/comment/index': {
    icon: 'chat',
    title: '评论管理',
    permission: 'blog:comment:query',
    load: () => import('./views/Comments.vue'),
  },
  'content/message/index': {
    icon: 'message',
    title: '留言管理',
    permission: 'blog:message:query',
    all: true,
    load: () => import('./views/Messages.vue'),
  },
  'content/link/index': {
    icon: 'link',
    title: '友链管理',
    permission: 'blog:link:query',
    load: () => import('./views/Links.vue'),
  },
  'content/file/index': {
    icon: 'picture',
    title: '文件管理',
    permission: 'blog:file:query',
    load: () => import('./views/Files.vue'),
  },
  'content/music/index': {
    icon: 'music',
    title: '音乐管理',
    permission: 'blog:music:query',
    all: true,
    load: () => import('./views/Music.vue'),
  },
  'content/rss/index': {
    icon: 'mail',
    title: '邮件订阅',
    permission: 'blog:rss:query',
    all: true,
    load: () => import('./views/Subscriptions.vue'),
  },
  'system/user/index': {
    icon: 'user',
    title: '用户管理',
    permission: 'sys:user:query',
    load: () => import('./views/Users.vue'),
  },
  'system/role/index': {
    icon: 'lock',
    title: '角色管理',
    permission: 'sys:role:query',
    load: () => import('./views/Roles.vue'),
  },
  'system/permission/index': {
    icon: 'key',
    title: '权限管理',
    permission: 'sys:permission:query',
    load: () => import('./views/Permissions.vue'),
  },
  'system/menu/index': {
    icon: 'menu',
    title: '菜单管理',
    permission: 'sys:menu:query',
    load: () => import('./views/Menus.vue'),
  },
  'system/config/index': {
    icon: 'setting',
    title: '站点配置',
    permission: 'blog:config:query',
    load: () => import('./views/Settings.vue'),
  },
  'system/log/index': {
    icon: 'log',
    title: '操作日志',
    permission: 'sys:log:query',
    all: true,
    load: () => import('./views/Logs.vue'),
  },
  'home/profile/index': {
    icon: 'home',
    title: '主页资料',
    permission: 'home:profile:query',
    all: true,
    load: () => import('./views/HomeProfile.vue'),
  },
  'home/social/index': {
    icon: 'share',
    title: '社交链接',
    permission: 'home:social:query',
    all: true,
    load: () => import('./views/Socials.vue'),
  },
  'cv/experience/index': {
    icon: 'briefcase',
    title: '简历经历',
    permission: 'cv:experience:query',
    all: true,
    load: () => import('./views/Experiences.vue'),
  },
  'cv/skill/index': {
    icon: 'medal',
    title: '简历技能',
    permission: 'cv:skill:query',
    all: true,
    load: () => import('./views/Skills.vue'),
  },
  'statistics/overview/index': {
    icon: 'chart',
    title: '统计报表',
    permission: 'blog:statistics:query',
    all: true,
    load: () => import('./views/Dashboard.vue'),
  },
  'statistics/visitor/index': {
    icon: 'visitor',
    title: '访客管理',
    permission: 'blog:visitor:query',
    all: true,
    load: () => import('./views/Visitors.vue'),
  },
  'statistics/view/index': {
    icon: 'eye',
    title: '浏览记录',
    permission: 'blog:view:query',
    all: true,
    load: () => import('./views/PageViews.vue'),
  },
}

export function flattenMenus(nodes) {
  const result = []
  for (const node of nodes) {
    result.push(node)
    if (Array.isArray(node.children)) {
      result.push(...flattenMenus(node.children))
    }
  }
  return result
}

export function isInternalPath(path) {
  return typeof path === 'string' && /^\/(?!\/)[A-Za-z0-9/_-]*$/.test(path)
}

/** 隐藏菜单仍可访问；按钮与未知组件都不能生成页面路由。 */
export function allowedPages(menus, capabilities) {
  return flattenMenus(menus).filter((node) => {
    const page = pageRegistry[node.component]
    return (
      node.menuType === 'M' &&
      node.status === 1 &&
      page &&
      isInternalPath(node.path) &&
      capabilities.permissionCodes.includes(page.permission) &&
      (!page.all || capabilities.dataScopes[page.permission] === 'ALL')
    )
  })
}
