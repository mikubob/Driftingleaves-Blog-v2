import { validateOnlineTicket } from '../../shared/online.mjs'
import { isCaptchaId, setCaptchaHeaders, validateCaptcha } from '../../shared/captcha.mjs'
import { reportRequestFailure } from '../../shared/requestFailure.mjs'

export const apiRoot = `${(import.meta.env?.VITE_API_ORIGIN || '').replace(/\/$/, '')}/api/v1`
let accessToken = ''
let onExpired = () => {}

/** 设置当前请求凭据；所有请求始终指向已配置的 API 根路径。 */
export function setAuthorization(token, expired = onExpired) {
  accessToken = token
  onExpired = expired
}

export function isId(value) {
  return typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value)
    && BigInt(value) <= 9223372036854775807n
}

export function isWebUrl(value) {
  if (typeof value !== 'string') {
    return false
  }
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password
  } catch {
    return false
  }
}

const publicAttachment = /^\/api\/v1\/blog\/articles\/[1-9]\d*\/files\/[1-9]\d*\/content$/

/** 外链与媒体在响应入口检查，不接受脚本、凭据或协议相对地址。 */
function validateFields(value, key = '') {
  if (key === 'captchaId' && isCaptchaId(value)) {
    return
  }
  if (value === null) {
    return
  }
  if (/(?:Id)$/.test(key) && !isId(value)) {
    throw new Error('接口返回的 ID 格式不正确。')
  }
  if (/(?:Count|Bytes|hotValue)$/.test(key)
    && (typeof value !== 'string' || !/^\d+$/.test(value))) {
    throw new Error('接口返回的计数格式不正确。')
  }
  if (['avatar', 'coverUrl', 'audioUrl', 'originalUrl', 'website', 'githubUrl', 'authorizationUrl', 'url'].includes(key)
    && !isWebUrl(value)
    && !(key === 'coverUrl' && publicAttachment.test(value))
    && !(key === 'url' && typeof value === 'string' && /^mailto:[^\s@?#]+@[^\s@?#]+\.[^\s@?#]+$/.test(value))) {
    throw new Error('接口返回了不支持的链接。')
  }
  if (typeof value === 'object') {
    for (const [field, child] of Object.entries(value)) {
      validateFields(child, field)
      if (field === 'coverUrl' && typeof child === 'string' && publicAttachment.test(child)) {
        value[field] = `${apiRoot}${child.slice('/api/v1'.length)}`
      }
    }
  }
}

const text = (value) => typeof value === 'string'
const nullableText = (value) => value === null || text(value)
const count = (value) => text(value) && /^\d+$/.test(value)
const time = (value) => text(value) && /Z$/.test(value) && !Number.isNaN(Date.parse(value))
const flag = (value) => value === 0 || value === 1
const slug = (value) => text(value) && /^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$/.test(value)

function model(value, fields) {
  return !!value && typeof value === 'object' && !Array.isArray(value)
    && Object.entries(fields).every(([key, check]) => check(value[key]))
}

const author = (value) => model(value, { userId: isId, nickname: text, avatar: nullableText })
const category = (value) => model(value, { categoryId: isId, categoryName: text, slug })
const tag = (value) => model(value, { tagId: isId, tagName: text, slug })
const navigation = (value) => model(value, { articleId: isId, title: text, slug })
const article = (value) => model(value, {
  articleId: isId, title: text, slug, summary: text, author,
  category: (item) => item === null || category(item),
  tags: (items) => Array.isArray(items) && items.every(tag),
  coverUrl: nullableText, articleType: (item) => [1, 2, 3].includes(item),
  isTop: flag, allowComment: flag, viewCount: count, likeCount: count, commentCount: count,
  publishTime: time, updateTime: time,
})
const me = (value) => model(value, {
  userId: isId, username: text, nickname: text, email: nullableText,
  avatar: nullableText, intro: nullableText, hasPassword: (item) => typeof item === 'boolean',
})
const subscription = (value) => model(value, {
  subscriptionId: isId, email: text, status: (item) => [0, 1, 2].includes(item),
})
const message = (value) => model(value, {
  messageId: isId, content: text, htmlContent: text, contentFormat: flag,
  isSecret: flag, isOwn: (item) => typeof item === 'boolean',
  status: (item) => [0, 1, 2, 3].includes(item), createTime: time,
  author: (item) => item === null || author(item),
})
const comment = (value) => model(value, {
  commentId: isId, articleId: isId, content: text, createTime: time,
  status: (item) => [0, 1, 2, 3].includes(item),
})
const session = (value) => model(value, {
  tokenType: (item) => item === 'Bearer', accessToken: (item) => text(item) && !!item, user: me,
})

/** 只检查本博客实际使用的响应模型，错误数据在进入页面状态前拒绝。 */
function validateData(path, data, method) {
  const route = path.split('?')[0]
  const read = method === 'GET'
  let check
  const pageModels = {
    '/blog/articles': article,
    '/blog/categories': category,
    '/blog/tags': tag,
    '/blog/authors': (item) => author(item) && count(item.articleCount),
    '/blog/archives': (item) => model(item, { month: (month) => /^[1-9]\d{3}-(0[1-9]|1[0-2])$/.test(month), articleCount: count }),
    '/blog/links': (item) => model(item, { linkId: isId, linkName: text, url: text, avatar: nullableText, description: nullableText }),
    '/blog/music': (item) => model(item, { musicId: isId, title: text, artist: nullableText, audioUrl: text }),
    '/blog/messages': message,
    '/blog/me/messages': message,
    '/blog/me/comments': comment,
    '/blog/me/liked-articles': (item) => model(item, { article, likedAt: time }),
  }
  if (read && (pageModels[route] || /^\/blog\/articles\/[1-9]\d*\/comments$/.test(route))) {
    const itemCheck = pageModels[route] || ((item) => comment(item)
      && count(item.replyCount) && (item.author === null || author(item.author)))
    check = (value) => model(value, {
      page: Number.isInteger, pageSize: Number.isInteger, total: count, totalPages: count,
      records: (items) => Array.isArray(items) && items.every(itemCheck),
    })
  } else if (read && /^\/blog\/articles\/[^/]+$/.test(route)) {
    check = (value) => article(value) && model(value, {
      htmlContent: text, wordCount: count, readingMinutes: (item) => Number.isInteger(item) && item >= 1,
      prevArticle: (item) => item === null || navigation(item),
      nextArticle: (item) => item === null || navigation(item),
      relatedArticles: (items) => Array.isArray(items) && items.every(navigation),
    })
  } else if (route.endsWith('/like')) {
    check = (value) => model(value, { liked: (item) => typeof item === 'boolean', likeCount: count })
  } else if (read && route.startsWith('/blog/categories/')) {
    check = (value) => category(value) && count(value.articleCount)
  } else if (read && route.startsWith('/blog/tags/')) {
    check = (value) => tag(value) && count(value.articleCount)
  } else if (read && route.startsWith('/blog/authors/')) {
    check = (value) => author(value) && count(value.articleCount)
  } else if (route === '/blog/me' || route === '/blog/me/profile' || route === '/blog/auth/register') {
    check = me
  } else if (route === '/blog/auth/login') {
    check = session
  } else if (route === '/blog/auth/oauth/exchange') {
    check = (value) => model(value, { operation: (item) => item === 'LOGIN', session })
  } else if (route.endsWith('/authorize')) {
    check = (value) => model(value, { authorizationUrl: text })
  } else if (route === '/blog/me/capabilities') {
    check = (value) => model(value, { permissionCodes: (items) => Array.isArray(items) && items.every(text) })
  } else if (route === '/blog/me/rss-subscription' || route === '/blog/me/rss-subscription/confirm') {
    check = (value) => value === null || subscription(value)
  } else if (read && /^\/blog\/me\/messages\/[1-9]\d*$/.test(route)) {
    check = message
  } else if (route === '/blog/messages' && method === 'POST') {
    check = message
  } else if (method === 'POST' && /^\/blog\/articles\/[1-9]\d*\/comments$/.test(route)) {
    check = comment
  } else if (read && route === '/blog/profile') {
    check = (value) => value === null || model(value, {
      nickname: text, tagline: text, description: nullableText, avatar: nullableText,
      website: nullableText, githubUrl: nullableText, publicEmail: nullableText, location: nullableText,
    })
  } else if (read && route === '/blog/statistics') {
    check = (value) => model(value, {
      articleCount: count, categoryCount: count, tagCount: count,
      pageViewCount: count, visitorCount: count, todayPageViewCount: count,
    })
  } else if (read && route === '/blog/article-rankings') {
    check = (items) => Array.isArray(items) && items.every((item) => navigation(item) && count(item.hotValue))
  } else if (read && route === '/blog/configs') {
    check = (items) => Array.isArray(items) && items.every((item) => model(item, {
      configKey: (key) => key === 'site', isPublic: (flag) => flag === 1,
      configValue: (value) => model(value, { title: text, description: text, icp: text, publicSecurityRecord: text }),
    }))
  } else if (read && route === '/blog/social-links') {
    check = (items) => Array.isArray(items) && items.every((item) => model(item, { socialId: isId, name: text, url: text }))
  }
  if (check && !check(data)) {
    throw new Error('接口数据格式不正确，请重新加载或联系站点管理员。')
  }
}

class ApiError extends Error {
  constructor(message, status, code) {
    super(message)
    this.status = status
    this.code = code
  }
}

/** 统一处理 Result、失效身份和 Retry-After，不重放写请求。 */
export async function request(path, { body, revision, captcha, ...options } = {}) {
  const headers = new Headers({ Accept: 'application/json' })
  setCaptchaHeaders(headers, captcha)
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (revision !== undefined) {
    if (typeof revision !== 'string' || !/^"[^"\r\n]+"$/.test(revision)) {
      throw new Error('缺少有效编辑版本，请重新加载详情。')
    }
    headers.set('If-Revision', revision)
  }
  let response
  try {
    response = await fetch(`${apiRoot}${path}`, {
      ...options,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
    })
  } catch (error) {
    if (error.name === 'AbortError') {
      throw error
    }
    throw reportRequestFailure(new Error('暂时无法连接服务器，请检查网络后重试。'), path)
  }
  if (response.status === 429) {
    const seconds = response.headers.get('Retry-After')
    const error = new ApiError(/^\d+$/.test(seconds || '')
      ? `请求过于频繁，请等待 ${seconds} 秒后重试。`
      : '请求过于频繁，请稍后重试。', 429, 'RATE_LIMITED')
    error.retryAfter = Number(seconds) || 60
    throw reportRequestFailure(error, path)
  }
  let result
  try {
    result = await response.json()
  } catch (failure) {
    if (failure.name === 'AbortError') {
      throw failure
    }
    const error = new Error(`服务器返回异常（HTTP ${response.status}）。`)
    throw response.ok || response.status >= 500 ? reportRequestFailure(error, path) : error
  }
  if (!response.ok || result?.code !== 'OK') {
    if (response.status === 401 && result?.code === 'UNAUTHENTICATED') {
      accessToken = ''
      onExpired()
    }
    let message = typeof result?.msg === 'string' ? result.msg : `请求失败（HTTP ${response.status}）。`
    if (response.status === 412) {
      message = '内容已更新，请保留当前输入，重新加载详情后合并修改。'
    } else if (response.status === 428) {
      message = '缺少编辑版本，请重新加载详情后操作。'
    } else if (Array.isArray(result?.errors) && result.errors.length) {
      const errors = result.errors.filter((item) => typeof item.message === 'string')
      message += `：${errors.map((item) => item.message).join('；')}`
    }
    const error = new ApiError(message, response.status, result?.code)
    throw response.status >= 500 || result?.code === 'ACCESS_BLOCKED' ? reportRequestFailure(error, path) : error
  }
  if (!Object.hasOwn(result, 'data') || !Array.isArray(result.errors)) {
    throw new Error('接口响应格式不正确。')
  }
  if (path === '/blog/auth/captcha') {
    validateCaptcha(result.data)
  }
  validateFields(result.data)
  validateData(path, result.data, options.method || 'GET')
  return { data: result.data, revision: response.headers.get('X-Revision') }
}

export function queryString(params = {}) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== '' && value !== null && value !== undefined) {
      query.set(key, String(value))
    }
  }
  return query.size ? `?${query}` : ''
}

/** 分页计数保留字符串，只将普通页码作为数字使用。 */
export async function getPage(path, params = {}, signal) {
  const { data } = await request(`${path}${queryString(params)}`, { signal })
  if (!data || !Array.isArray(data.records) || !Number.isInteger(data.page)
    || !Number.isInteger(data.pageSize) || data.page < 1 || data.pageSize < 1
    || typeof data.total !== 'string' || !/^\d+$/.test(data.total)
    || typeof data.totalPages !== 'string' || !/^\d+$/.test(data.totalPages)) {
    throw new Error('分页响应格式不正确。')
  }
  return data
}

export function pageNumber(value) {
  if (typeof value !== 'string' || !/^[1-9]\d{0,5}$/.test(value)) {
    return 1
  }
  return Math.min(Number(value), 100000)
}

export function validatePassword(value) {
  const length = [...value].length
  return length >= 8 && length <= 64 && new TextEncoder().encode(value).length <= 72
}

/** 在线票据复用当前内存 Bearer，只在 HTTP Header 中发送。 */
export async function getOnlineTicket(body, signal) {
  const { data } = await request('/blog/analytics/online/tickets', { method: 'POST', body, signal })
  return validateOnlineTicket(data)
}
