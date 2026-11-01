import { validateOnlineTicket } from '../../shared/online.mjs'
import { reportRequestFailure } from '../../shared/requestFailure.mjs'

export const apiRoot = `${(import.meta.env?.VITE_API_ORIGIN || '').replace(/\/$/, '')}/api/v1`
const isText = (value) => typeof value === 'string'
const isNullableText = (value) => value === null || isText(value)
const isId = (value) => isText(value) && /^[1-9]\d{0,18}$/.test(value)
  && BigInt(value) <= 9223372036854775807n

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

function assertResponse(valid) {
  if (!valid) {
    throw new Error('接口数据格式异常，请稍后重试或联系站点管理员。')
  }
}

/** 公开请求统一解析 Result，保留 HTTP、业务错误和限流反馈。 */
async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${apiRoot}${path}`, {
      ...options,
      headers: { Accept: 'application/json', ...options.headers },
    })
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

/** 获取站点主人公开资料；null 表示尚未配置，邮箱仅取 publicEmail。 */
export async function getProfile(signal) {
  const data = await request('/home/profile', { signal })
  assertResponse(data === null || matchesFields(data, {
    nickname: isText,
    tagline: isText,
    description: isNullableText,
    avatar: (value) => value === null || isWebUrl(value),
    website: (value) => value === null || isWebUrl(value),
    publicEmail: (value) => value === null || isEmail(value),
    githubUrl: (value) => value === null || isWebUrl(value),
    location: isNullableText,
  }))
  return data
}

/** 保留后端社交顺序与字符串 ID，链接与图标在响应入口校验。 */
export async function getSocialLinks(signal) {
  const data = await request('/home/social-links', { signal })
  assertResponse(Array.isArray(data) && data.every((item) => matchesFields(item, {
    socialId: isId,
    name: isText,
    icon: isIcon,
    url: isSocialUrl,
    sortOrder: Number.isInteger,
  })))
  return data
}

/** 仅使用公开 site 配置，空数组表示没有公开配置。 */
export async function getSiteConfig(signal) {
  const data = await request('/home/configs', { signal })
  assertResponse(Array.isArray(data) && data.every((item) => matchesFields(item, {
    configKey: (value) => value === 'site',
    isPublic: (value) => value === 1,
    configValue: (value) => matchesFields(value, {
      title: isText,
      description: isText,
      icp: isText,
      publicSecurityRecord: isText,
      startDate: (date) => date === null || isDate(date),
    }),
  })))
  return data[0]?.configValue ?? null
}

/** 浏览统计单次发送；失败由调用方处理，不自动重放可能已写入的 POST。 */
export async function recordPageView(payload, signal) {
  const data = await request('/home/analytics/page-views', {
    method: 'POST',
    signal,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  assertResponse(matchesFields(data, { recorded: (value) => value === true }))
  return data
}

/** 公开站点匿名参与，不恢复其他站点的登录身份。 */
export async function getOnlineTicket(body, signal) {
  return validateOnlineTicket(await request('/home/analytics/online/tickets', {
    signal,
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }))
}
