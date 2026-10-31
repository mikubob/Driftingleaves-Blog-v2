// 以下内容仅用于隔离测试，不会作为正式页面的回退数据。
export const profile = {
  nickname: '落叶',
  tagline: '记录与分享',
  description: '热爱技术，也喜欢生活里的小事。\n在这里，留下一点思考与日常。',
  avatar: null,
  website: 'https://example.test',
  publicEmail: 'hello@example.test',
  githubUrl: 'https://github.com/example',
  location: '中国 · 上海',
}

export const socials = [
  { socialId: '9223372036854775807', name: '我的博客', icon: 'boke', url: 'https://blog.example.test', sortOrder: 0 },
  { socialId: '22', name: '在线简历', icon: 'jianli', url: 'https://cv.example.test', sortOrder: 1 },
  { socialId: '23', name: 'GitHub', icon: 'github', url: 'https://github.com/example', sortOrder: 2 },
  { socialId: '24', name: '哔哩哔哩', icon: 'bilibili-line', url: 'https://www.bilibili.com', sortOrder: 3 },
  { socialId: '25', name: '联系我', icon: 'youxiang', url: 'mailto:hello@example.test', sortOrder: 4 },
]

export const config = {
  configKey: 'site',
  configValue: {
    title: 'DriftingLeaves',
    description: '记录与分享，欢迎来到我的主页。',
    icp: '测试备案号（非真实备案）',
    publicSecurityRecord: '测试公安备案（非真实备案）',
    startDate: '2024-02-29',
  },
  description: '站点信息',
  isPublic: 1,
  updateTime: '2026-10-29T02:00:00.000Z',
}

export const result = (data) => ({ code: 'OK', msg: '成功', data, errors: [] })
