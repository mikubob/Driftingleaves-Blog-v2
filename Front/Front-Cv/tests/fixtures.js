// 仅用于测试的虚构资料，不作为页面离线兜底内容。
export const profile = {
  nickname: '示例开发者',
  tagline: '软件开发 · 认真构建每一个细节',
  description: '这里是用于验证页面的虚构简历。关注清晰的设计、可靠的工程与持续的学习。',
  avatar: null,
  website: 'https://example.test',
  publicEmail: 'developer@example.test',
  githubUrl: 'https://github.com/example',
  location: '中国 · 上海',
}

export const experiences = [
  {
    experienceId: '9223372036854775805',
    experienceType: 0,
    title: '示例大学',
    subtitle: '计算机科学与技术 · 本科',
    logoUrl: null,
    startDate: '2020-09-01',
    endDate: '2024-06-30',
    sortOrder: 0,
    htmlContent: '<p>学习计算机基础、数据结构与软件工程，参与课程设计与团队协作。</p>',
  },
  {
    experienceId: '9223372036854775806',
    experienceType: 1,
    title: '示例科技团队',
    subtitle: '软件开发工程师',
    logoUrl: null,
    startDate: '2024-07-01',
    endDate: null,
    sortOrder: 1,
    htmlContent: '<ul><li>参与业务接口与交互页面的设计和实现。</li><li>维护自动化测试与项目文档，改善团队协作体验。</li></ul>',
  },
  {
    experienceId: '9223372036854775807',
    experienceType: 2,
    title: '示例个人内容平台',
    subtitle: '独立开发 · 设计与工程',
    logoUrl: null,
    startDate: '2025-03-01',
    endDate: null,
    sortOrder: 2,
    htmlContent: '<p>围绕内容创作与个人展示构建完整的网站体验。</p><ul><li>设计文章、标签与简历展示模块。</li><li>使用 <strong>Vue 与 Spring Boot</strong> 实现前后端协作。</li></ul>',
  },
]

export const skills = [
  { skillId: '41', name: 'Java', description: '熟悉面向对象编程、Spring Boot 与 REST API 设计。', icon: null, sortOrder: 0 },
  { skillId: '42', name: 'Vue', description: '使用 Vue 3 构建响应式界面，关注交互细节与可访问性。', icon: 'document', sortOrder: 1 },
  { skillId: '43', name: 'MySQL & Redis', description: '理解关系型数据建模与常见缓存场景。', icon: null, sortOrder: 2 },
]

export const socials = [
  { socialId: '21', name: '联系我', icon: 'mail', url: 'mailto:hello@example.test', sortOrder: 0 },
]

export function result(data) {
  return { code: 'OK', msg: '成功', data, errors: [] }
}

export function pageResult(records, page = 1, totalPages = '1') {
  return result({ page, pageSize: 100, total: String(records.length), totalPages, records })
}
