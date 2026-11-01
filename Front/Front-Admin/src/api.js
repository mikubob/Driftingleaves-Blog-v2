import { reportRequestFailure } from '../../shared/requestFailure.mjs'
import { isCaptchaId, setCaptchaHeaders, validateCaptcha } from '../../shared/captcha.mjs'

const apiOrigin = (import.meta.env?.VITE_API_ORIGIN || '').replace(/\/$/, '')
export const apiRoot = `${apiOrigin}/api/v1`
let accessToken = ''
let onUnauthenticated = () => {}

/** 设置当前请求凭据，持久化与恢复由会话模块负责。 */
export function setAuthorization(token, onExpired = onUnauthenticated) {
  accessToken = token
  onUnauthenticated = onExpired
}

export class ApiError extends Error {
  constructor(message, status, code, errors = []) {
    super(message)
    this.status = status
    this.code = code
    this.errors = errors
  }
}

export function isId(value) {
  return (
    typeof value === 'string' &&
    /^[1-9]\d{0,18}$/.test(value) &&
    BigInt(value) <= 9223372036854775807n
  )
}

/** 在响应入口检查 ID 与计数，避免 BIGINT 被当作浮点数继续使用。 */
function validateNumbers(value, key = '') {
  if (key === 'captchaId' && isCaptchaId(value)) {
    return
  }
  if (value === null) {
    return
  }
  if (/(?:Id|Ids)$/.test(key)) {
    const values = Array.isArray(value) ? value : [value]
    if (!values.every(isId)) {
      throw new Error('接口返回的 ID 格式不正确。')
    }
  }
  if (/(?:Count|Bytes)$/.test(key) && (typeof value !== 'string' || !/^\d+$/.test(value))) {
    throw new Error('接口返回的计数格式不正确。')
  }
  if (value && typeof value === 'object') {
    for (const [field, item] of Object.entries(value)) {
      validateNumbers(item, field)
    }
  }
}

/** 统一处理鉴权、Result、版本及限流；失败请求不自动重放。 */
export async function request(path, { body, revision, captcha, blob = false, ...options } = {}) {
  const headers = new Headers({ Accept: blob ? 'image/png, image/jpeg' : 'application/json' })
  setCaptchaHeaders(headers, captcha)
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  if (revision !== undefined) {
    if (typeof revision !== 'string' || !/^"[^"\r\n]+"$/.test(revision)) {
      throw new Error('未取得有效编辑版本，请重新加载详情。')
    }
    headers.set('If-Revision', revision)
  }
  const multipart = body instanceof FormData
  if (body !== undefined && !multipart) {
    headers.set('Content-Type', 'application/json')
  }

  let response
  try {
    response = await fetch(`${apiRoot}${path}`, {
      ...options,
      headers,
      body: body === undefined || multipart ? body : JSON.stringify(body),
      cache: 'no-store',
    })
  } catch (error) {
    if (error.name === 'AbortError') {
      throw error
    }
    throw reportRequestFailure(new Error('无法连接服务器，请检查网络后重试。'), path)
  }
  if (response.status === 429) {
    const seconds = response.headers.get('Retry-After')
    const message = /^\d+$/.test(seconds || '')
      ? `请求过于频繁，请等待 ${seconds} 秒后重试。`
      : '请求过于频繁，请稍后重试。'
    throw reportRequestFailure(new ApiError(message, 429, 'RATE_LIMITED'), path)
  }
  if (blob && response.ok) {
    const image = await response.blob()
    if (!['image/png', 'image/jpeg'].includes(image.type)) {
      throw new Error('文件响应不是可预览的 PNG/JPEG 图片。')
    }
    return { data: image }
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
    let message =
      typeof result?.msg === 'string' ? result.msg : `请求失败（HTTP ${response.status}）。`
    const errors = Array.isArray(result?.errors)
      ? result.errors.filter(
          (item) => typeof item?.field === 'string' && typeof item.message === 'string',
        )
      : []
    if (response.status === 400 && errors.length) {
      message += `：${errors.map((item) => `${item.field} ${item.message}`).join('；')}`
    }
    if (response.status === 401 && result?.code === 'UNAUTHENTICATED') {
      accessToken = ''
      onUnauthenticated()
    }
    if (response.status === 412) {
      message = '内容已被其他人修改。请保留当前输入，重新获取详情后合并修改。'
    }
    if (response.status === 428) {
      message = '缺少编辑版本，请重新加载详情后操作。'
    }
    const error = new ApiError(message, response.status, result?.code, errors)
    throw response.status >= 500 ? reportRequestFailure(error, path) : error
  }
  if (!Object.hasOwn(result, 'data') || !Array.isArray(result.errors)) {
    throw new Error('接口响应格式不正确。')
  }
  if (path === '/admin/auth/captcha') {
    validateCaptcha(result.data)
  }
  validateNumbers(result.data)
  return { data: result.data, revision: response.headers.get('X-Revision') }
}

/** 只发送非空筛选条件，保留数字 0 和字符串 ID。 */
export function queryString(params) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== '' && value !== null && value !== undefined) {
      if (['begin', 'end'].includes(key) && typeof value === 'string' && value.includes('T')) {
        query.set(key, new Date(value).toISOString())
      } else {
        query.set(key, String(value))
      }
    }
  }
  const search = query.toString()
  return search ? `?${search}` : ''
}

export async function getPage(path, params, signal) {
  const { data } = await request(`${path}${queryString(params)}`, { signal })
  if (
    !data ||
    !Array.isArray(data.records) ||
    !Number.isInteger(data.page) ||
    !Number.isInteger(data.pageSize) ||
    !/^\d+$/.test(data.total) ||
    typeof data.total !== 'string' ||
    typeof data.totalPages !== 'string' ||
    !/^\d+$/.test(data.totalPages)
  ) {
    throw new Error('分页响应格式不正确。')
  }
  return data
}

/** 选择器读取全部页，避免字典超过一页后选项缺失。 */
export async function getOptions(path, params = {}, signal) {
  const records = []
  let page = 1
  while (true) {
    const data = await getPage(path, { ...params, page, pageSize: 100 }, signal)
    records.push(...data.records)
    if (BigInt(page) >= BigInt(data.totalPages)) {
      return records
    }
    page += 1
  }
}

/** 私密图片只从已知 API 路径携带 Bearer 读取，不向外部 URL 发送 Token。 */
export async function getImage(path, signal) {
  const prefix = '/api/v1'
  if (
    !/^\/api\/v1\/admin\/(?:files\/[1-9]\d*|articles\/[1-9]\d*\/files\/[1-9]\d*)\/content$/.test(
      path,
    )
  ) {
    throw new Error('不支持的后台文件地址。')
  }
  const { data } = await request(path.slice(prefix.length), { blob: true, signal })
  return URL.createObjectURL(data)
}
