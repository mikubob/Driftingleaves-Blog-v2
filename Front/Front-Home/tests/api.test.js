import test, { afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { getProfile, getSocialLinks, getSiteConfig, recordPageView } from '../src/api.js'
import { profile, socials, config, result } from './fixtures.js'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})

function respond(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers })
}

test('三个公开 GET 使用 /api/v1，不附加鉴权、参数或请求体', async () => {
  const responses = new Map([
    ['/api/v1/home/profile', profile],
    ['/api/v1/home/social-links', socials],
    ['/api/v1/home/configs', [config]],
  ])
  globalThis.fetch = async (url, options) => {
    assert.ok(responses.has(url))
    assert.deepEqual(options.headers, { Accept: 'application/json' })
    assert.equal(options.body, undefined)
    assert.equal(options.method, undefined)
    return respond(result(responses.get(url)))
  }

  assert.deepEqual(await getProfile(), profile)
  assert.deepEqual(await getSocialLinks(), socials)
  assert.deepEqual(await getSiteConfig(), config.configValue)
})

test('null 资料和空社交、配置保持成功空状态', async () => {
  globalThis.fetch = async () => respond(result(null))
  assert.equal(await getProfile(), null)
  globalThis.fetch = async () => respond(result([]))
  assert.deepEqual(await getSocialLinks(), [])
  assert.equal(await getSiteConfig(), null)
})

test('只读取明确公开的邮箱，允许纯文本中包含 HTML 字样', async () => {
  globalThis.fetch = async () => respond(result({
    ...profile,
    publicEmail: null,
    email: 'private@example.test',
    description: '<script>alert(1)</script>',
  }))
  const data = await getProfile()
  assert.equal(data.publicEmail, null)
  assert.equal(data.description, '<script>alert(1)</script>')
})

test('字符串 BIGINT 与后端顺序保留，允许未知图标键', async () => {
  const links = [{ ...socials[0], icon: '__proto__' }, socials[1]]
  globalThis.fetch = async () => respond(result(links))
  assert.deepEqual(await getSocialLinks(), links)
  assert.equal((await getSocialLinks())[0].socialId, '9223372036854775807')

  for (const socialId of [21, '0', '01', '9223372036854775808']) {
    globalThis.fetch = async () => respond(result([{ ...socials[0], socialId }]))
    await assert.rejects(getSocialLinks(), /接口数据格式异常/)
  }
})

test('拒绝脚本、凭据 URL、任意 HTML 图标与带查询参数的 mailto', async () => {
  for (const url of ['javascript:alert(1)', 'data:text/html,test', 'https://user:pass@example.test',
    'mailto:a@example.test?bcc=b@example.test', 'mailto:a@example.test#fragment', 'mailto:a@example.test\n']) {
    globalThis.fetch = async () => respond(result([{ ...socials[0], url }]))
    await assert.rejects(getSocialLinks(), /接口数据格式异常/)
  }
  globalThis.fetch = async () => respond(result([{ ...socials[0], icon: '<svg onload="alert(1)">' }]))
  await assert.rejects(getSocialLinks(), /接口数据格式异常/)
  globalThis.fetch = async () => respond(result([{ ...socials[0], icon: 'https://example.test/icon.svg' }]))
  assert.equal((await getSocialLinks()).length, 1)
})

test('站点配置允许空备案和未设置日期，拒绝非公开配置与无效日期', async () => {
  globalThis.fetch = async () => respond(result([{
    ...config, configValue: { ...config.configValue, icp: '', publicSecurityRecord: '', startDate: null },
  }]))
  assert.equal((await getSiteConfig()).startDate, null)

  for (const change of [
    { configKey: 'comment' },
    { isPublic: 0 },
    { configValue: { ...config.configValue, startDate: '2025-02-29' } },
  ]) {
    globalThis.fetch = async () => respond(result([{ ...config, ...change }]))
    await assert.rejects(getSiteConfig(), /接口数据格式异常/)
  }
})

test('限流包含 Retry-After，不自动重复请求', async () => {
  let calls = 0
  globalThis.fetch = async () => {
    calls += 1
    return respond({ code: 'RATE_LIMITED' }, 429, { 'Retry-After': '30' })
  }
  await assert.rejects(getProfile(), /等待 30 秒/)
  assert.equal(calls, 1)
})

test('网络、HTTP、业务和协议错误均保持失败，可再次加载恢复', async () => {
  globalThis.fetch = async () => { throw new TypeError('fetch failed') }
  await assert.rejects(getProfile(), /无法连接服务器/)
  globalThis.fetch = async () => new Response('<html>Bad Gateway</html>', { status: 502 })
  await assert.rejects(getProfile(), /HTTP 502/)
  globalThis.fetch = async () => respond({ code: 'SERVICE_UNAVAILABLE', msg: '服务暂不可用' }, 503)
  await assert.rejects(getSocialLinks(), /服务暂不可用/)
  globalThis.fetch = async () => respond({ code: 'NOT_FOUND', msg: '资源不存在', data: null })
  await assert.rejects(getProfile(), /资源不存在/)
  globalThis.fetch = async () => respond(result({ ...profile, avatar: 'javascript:alert(1)' }))
  await assert.rejects(getProfile(), /接口数据格式异常/)
  globalThis.fetch = async () => respond(result(profile))
  assert.deepEqual(await getProfile(), profile)
})

test('页面离开可取消加载', async () => {
  const controller = new AbortController()
  controller.abort()
  globalThis.fetch = async (url, options) => {
    assert.equal(options.signal, controller.signal)
    options.signal.throwIfAborted()
  }
  await assert.rejects(getProfile(controller.signal), { name: 'AbortError' })
})

test('采集精确发送契约字段与 JSON，无鉴权，失败不重放', async () => {
  const payload = {
    fingerprint: '0123456789abcdef0123456789abcdef',
    siteCode: 'home',
    pagePath: '/',
    pageTitle: '主页',
    referer: null,
    articleId: null,
  }
  let calls = 0
  globalThis.fetch = async (url, options) => {
    calls += 1
    assert.equal(url, '/api/v1/home/analytics/page-views')
    assert.equal(options.method, 'POST')
    assert.deepEqual(options.headers, { Accept: 'application/json', 'Content-Type': 'application/json' })
    assert.deepEqual(JSON.parse(options.body), payload)
    return respond(result({ recorded: true }))
  }
  assert.deepEqual(await recordPageView(payload), { recorded: true })
  assert.equal(calls, 1)
  globalThis.fetch = async () => respond(result({ recorded: false }))
  await assert.rejects(recordPageView(payload), /接口数据格式异常/)
})
