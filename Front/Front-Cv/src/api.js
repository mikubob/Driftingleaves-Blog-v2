import { validateOnlineTicket } from '../../shared/online.mjs'
import { reportRequestFailure } from '../../shared/requestFailure.mjs'

export const apiRoot = `${(import.meta.env?.VITE_API_ORIGIN || '').replace(/\/$/, '')}/api/v1`
const isText = (value) => typeof value === 'string'
const isNullableText = (value) => value === null || isText(value)
const isId = (value) => isText(value) && /^[1-9]\d*$/.test(value)
  && BigInt(value) <= 9223372036854775807n
const isCount = (value) => isText(value) && /^(0|[1-9]\d*)$/.test(value)

function isWebUrl(value) {
  if (!isText(value)) {
    return false
  }

  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password
  } catch {
    return false
  }
}

function isEmail(value) {
  return isText(value) && /^[A-Za-z0-9.!#$%&'*+\/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$/.test(value)
}

function isSocialUrl(value) {
  return isWebUrl(value) || (isText(value) && value.startsWith('mailto:')
    && !/[?#]/.test(value) && isEmail(value.slice(7)))
}

function isIcon(value) {
  return value === null || (isText(value) && (/^[\w-]+$/.test(value) || isWebUrl(value)))
}

function isDate(value) {
  if (!isText(value) || !/^[1-9]\d{3}-\d{2}-\d{2}$/.test(value)) {
    return false
  }

  const date = new Date(`${value}T00:00:00.000Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}

function matchesFields(value, fields) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    && Object.entries(fields).every(([key, validate]) => validate(value[key]))
}

const profileFields = {
  nickname: isText,
  tagline: isText,
  description: isNullableText,
  avatar: (value) => value === null || isWebUrl(value),
  website: (value) => value === null || isWebUrl(value),
  publicEmail: (value) => value === null || isEmail(value),
  githubUrl: (value) => value === null || isWebUrl(value),
  location: isNullableText,
}

const socialFields = {
  socialId: isId,
  name: isText,
  icon: isIcon,
  url: isSocialUrl,
  sortOrder: Number.isInteger,
}

const experienceFields = {
  experienceId: isId,
  experienceType: (value) => [0, 1, 2].includes(value),
  title: isText,
  subtitle: isNullableText,
  logoUrl: (value) => value === null || isWebUrl(value),
  startDate: isDate,
  endDate: (value) => value === null || isDate(value),
  htmlContent: isText,
  sortOrder: Number.isInteger,
}

const skillFields = {
  skillId: isId,
  name: isText,
  description: isNullableText,
  icon: isIcon,
  sortOrder: Number.isInteger,
}

function assertResponse(valid) {
  if (!valid) {
    throw new Error('接口数据格式异常，请稍后重试或联系站点管理员。')
  }
}

/** 公开请求统一解析 Result；失败保留为错误，不伪装成空数据。 */
async function request(path, signal, options = {}) {
  let response
  try {
    response = await fetch(`${apiRoot}${path}`, { ...options, signal, headers: { Accept: 'application/json', ...options.headers } })
  } catch (error) {
    if (error.name === 'AbortError') {
      throw error
    }
    throw reportRequestFailure(new Error('暂时无法连接服务器，请检查网络后重试。'), path)
  }

  if (response.status === 429) {
    const seconds = response.headers.get('Retry-After')
    const wait = /^\d+$/.test(seconds || '') ? `请等待 ${seconds} 秒后重试。` : '请稍后重试。'
    const error = Object.assign(new Error(`访问过于频繁，${wait}`), {
      status: 429, code: 'RATE_LIMITED', retryAfter: Number(seconds) || 60,
    })
    throw reportRequestFailure(error, path)
  }

  let result
  try {
    result = await response.json()
  } catch (failure) {
    if (failure.name === 'AbortError') {
      throw failure
    }
    const error = new Error(`服务器返回异常（HTTP ${response.status}），请稍后重试。`)
    throw response.ok || response.status >= 500 ? reportRequestFailure(error, path) : error
  }

  if (!response.ok || result?.code !== 'OK') {
    const error = Object.assign(new Error(isText(result?.msg) ? result.msg : `请求失败（HTTP ${response.status}）。`),
      { status: response.status, code: result?.code })
    throw response.status >= 500 || result?.code === 'ACCESS_BLOCKED' ? reportRequestFailure(error, path) : error
  }
  assertResponse(Object.hasOwn(result, 'data'))
  return result.data
}

/** 获取公开资料；null 表示管理员尚未配置。 */
export async function getProfile(signal) {
  const data = await request('/cv/profile', signal)
  assertResponse(data === null || matchesFields(data, profileFields))
  return data
}

/** 获取可见社交链接，图标只接受 URL 或本地注册键。 */
export async function getSocialLinks(signal) {
  const data = await request('/cv/social-links', signal)
  assertResponse(Array.isArray(data) && data.every((item) => matchesFields(item, socialFields)))
  return data
}

async function getAllPages(path, fields, signal) {
  const records = []
  let page = 1

  // 打印完整简历需要读取全部分页；ID 和分页总数始终保留字符串精度。
  while (page <= 100000) {
    const data = await request(`${path}?page=${page}&pageSize=100`, signal)
    assertResponse(matchesFields(data, {
      page: (value) => value === page,
      pageSize: (value) => value === 100,
      total: isCount,
      totalPages: isCount,
      records: (value) => Array.isArray(value) && value.every((item) => matchesFields(item, fields)),
    }))
    records.push(...data.records)
    if (BigInt(page) >= BigInt(data.totalPages)) {
      return records
    }
    page += 1
  }

  throw new Error('简历内容超过接口分页范围，请联系站点管理员。')
}

/** 按后端顺序读取全部可见经历，0 教育、1 工作、2 项目。 */
export function getExperiences(signal) {
  return getAllPages('/cv/experiences', experienceFields, signal)
}

/** 技能只展示名称和描述，不推断评分或熟练度百分比。 */
export function getSkills(signal) {
  return getAllPages('/cv/skills', skillFields, signal)
}

/** 公开站点匿名参与，不恢复其他站点的登录身份。 */
export async function getOnlineTicket(body, signal) {
  return validateOnlineTicket(await request('/cv/analytics/online/tickets', signal, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }))
}
