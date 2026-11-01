import test, { afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { getProfile, getSocialLinks, getExperiences, getSkills } from '../src/api.js'
import { profile, experiences, skills, socials, result, pageResult } from './fixtures.js'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})

function respond(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers })
}

test('公开资料使用新契约字段，无鉴权、无请求体', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/cv/profile')
    assert.deepEqual(options.headers, { Accept: 'application/json' })
    assert.equal(options.body, undefined)
    return respond(result({ ...profile, email: 'private@example.test' }))
  }
  const data = await getProfile()
  assert.equal(data.publicEmail, 'developer@example.test')
  assert.equal(data.githubUrl, profile.githubUrl)
})

test('未配置资料与空集合是正常成功状态', async () => {
  globalThis.fetch = async () => respond(result(null))
  assert.equal(await getProfile(), null)
  globalThis.fetch = async () => respond(result([]))
  assert.deepEqual(await getSocialLinks(), [])
  globalThis.fetch = async () => respond(pageResult([], 1, '0'))
  assert.deepEqual(await getExperiences(), [])
})

test('经历读取所有页，保留顺序、类型、安全 HTML 和 BIGINT 字符串', async () => {
  const requests = []
  globalThis.fetch = async (url) => {
    requests.push(url)
    const page = Number(new URL(url, 'http://localhost').searchParams.get('page'))
    return respond(pageResult([experiences[page - 1]], page, '3'))
  }
  const data = await getExperiences()
  assert.deepEqual(data, experiences)
  assert.equal(data[2].experienceId, '9223372036854775807')
  assert.equal(requests.length, 3)
  assert.equal(requests[2], '/api/v1/cv/experiences?page=3&pageSize=100')
})

test('技能分页失败不返回截断的成功列表，再次加载可以恢复', async () => {
  globalThis.fetch = async (url) => {
    if (url.includes('page=2&')) {
      return respond({ code: 'SERVICE_UNAVAILABLE', msg: '服务暂不可用' }, 503)
    }
    return respond(pageResult([skills[0]], 1, '2'))
  }
  await assert.rejects(getSkills(), /服务暂不可用/)
  globalThis.fetch = async () => respond(pageResult(skills))
  assert.deepEqual(await getSkills(), skills)
})

test('社交 mailto 正常展示，未知图标键可使用文本安全回落', async () => {
  const links = [{ ...socials[0], icon: 'unregistered', url: 'mailto:a&b%tag@example.test' }]
  globalThis.fetch = async () => respond(result(links))
  assert.deepEqual(await getSocialLinks(), links)
  globalThis.fetch = async () => respond(result({ ...profile, publicEmail: 'a?tag@example.test' }))
  assert.equal((await getProfile()).publicEmail, 'a?tag@example.test')
})

test('拒绝脚本链接与带额外邮件参数的 mailto', async () => {
  for (const url of ['javascript:alert(1)', 'mailto:a@example.test?bcc=other@example.test']) {
    globalThis.fetch = async () => respond(result([{ ...socials[0], url }]))
    await assert.rejects(getSocialLinks(), /接口数据格式异常/)
  }
})

test('拒绝数字 ID、未知经历枚举和不存在的日期', async () => {
  for (const change of [{ experienceId: 31 }, { experienceType: 3 }, { startDate: '2024-02-30' }]) {
    globalThis.fetch = async () => respond(pageResult([{ ...experiences[0], ...change }]))
    await assert.rejects(getExperiences(), /接口数据格式异常/)
  }
})

test('限流向用户传达 Retry-After，不自动重复请求', async () => {
  let calls = 0
  globalThis.fetch = async () => {
    calls += 1
    return respond({ code: 'RATE_LIMITED' }, 429, { 'Retry-After': '30' })
  }
  await assert.rejects(getProfile(), /等待 30 秒/)
  assert.equal(calls, 1)
})

test('网络错误、非 JSON 和业务失败保持失败语义', async () => {
  globalThis.fetch = async () => { throw new TypeError('fetch failed') }
  await assert.rejects(getSkills(), /无法连接服务器/)
  globalThis.fetch = async () => new Response('<html>Bad Gateway</html>', { status: 502 })
  await assert.rejects(getProfile(), /HTTP 502/)
  globalThis.fetch = async () => respond({ code: 'NOT_FOUND', msg: '资源不存在', data: null })
  await assert.rejects(getProfile(), /资源不存在/)
})

test('页面离开时可取消当前请求', async () => {
  const controller = new AbortController()
  controller.abort()
  globalThis.fetch = async (url, options) => {
    assert.equal(options.signal, controller.signal)
    options.signal.throwIfAborted()
  }
  await assert.rejects(getProfile(controller.signal), { name: 'AbortError' })
})
