import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import {
  ApiError,
  getImage,
  getOptions,
  getPage,
  isId,
  queryString,
  request,
  setAuthorization,
} from '../src/api.js'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
  setAuthorization('')
})

function result(data, options = {}) {
  return new Response(JSON.stringify({ code: 'OK', msg: '成功', data, errors: [] }), {
    status: 200,
    headers: { 'Content-Type': 'application/json', 'X-Revision': '"opaque-version"' },
    ...options,
  })
}

test('请求保留字符串 ID、带引号版本与内存 Bearer，只发送明确字段', async () => {
  const id = '9223372036854775807'
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/admin/articles/' + id)
    assert.equal(options.headers.get('Authorization'), 'Bearer test-token')
    assert.equal(options.headers.get('If-Revision'), '"revision-1"')
    assert.deepEqual(JSON.parse(options.body), { title: '测试文章', categoryId: id, summary: '' })
    assert.equal(options.cache, 'no-store')
    return result({ articleId: id })
  }
  setAuthorization('test-token')
  const response = await request('/admin/articles/' + id, {
    method: 'PUT',
    revision: '"revision-1"',
    body: { title: '测试文章', categoryId: id, summary: '' },
  })
  assert.equal(response.data.articleId, id)
  assert.equal(response.revision, '"opaque-version"')
})

test('数字 ID、越界 ID 与浮点计数在响应入口拒绝', async () => {
  for (const data of [
    { articleId: 9007199254740992 },
    { userId: '9223372036854775808' },
    { viewCount: 4 },
    { roleIds: ['1', 2] },
  ]) {
    globalThis.fetch = async () => result(data)
    await assert.rejects(request('/test'), /格式不正确/)
  }
  assert.equal(isId('0001'), false)
  assert.equal(isId('9223372036854775807'), true)
})

test('分页筛选保留 0、忽略空条件并将本地时间转为 UTC 毫秒', () => {
  const query = new URLSearchParams(
    queryString({
      status: 0,
      keyword: '',
      userId: '9007199254740993',
      optional: null,
      begin: '2026-10-31T12:00:00.000+08:00',
      end: '2026-10-31',
    }).slice(1),
  )
  assert.equal(query.get('status'), '0')
  assert.equal(query.get('userId'), '9007199254740993')
  assert.equal(query.has('keyword'), false)
  assert.equal(query.get('begin'), '2026-10-31T04:00:00.000Z')
  assert.equal(query.get('end'), '2026-10-31')
})

test('字典选择器遍历所有页并保留后端顺序', async () => {
  const requestedPages = []
  globalThis.fetch = async (url) => {
    const page = Number(new URL(url, 'http://test.invalid').searchParams.get('page'))
    requestedPages.push(page)
    return result({
      page,
      pageSize: 100,
      total: '101',
      totalPages: '2',
      records: [{ tagId: String(page), tagName: `标签${page}` }],
    })
  }
  assert.deepEqual(
    (await getOptions('/admin/tags')).map((row) => row.tagId),
    ['1', '2'],
  )
  assert.deepEqual(requestedPages, [1, 2])
})

test('错误分页不能当作成功空列表', async () => {
  globalThis.fetch = async () =>
    result({ page: 1, pageSize: 10, total: 0, totalPages: '0', records: [] })
  await assert.rejects(getPage('/admin/tags', {}), /分页响应/)
})

test('AUTH_FAILED 保留有效会话，UNAUTHENTICATED 清除当前 Token', async () => {
  let expired = 0
  setAuthorization('test-token', () => {
    expired += 1
  })
  for (const code of ['AUTH_FAILED', 'UNAUTHENTICATED']) {
    globalThis.fetch = async () =>
      new Response(JSON.stringify({ code, msg: '认证失败', data: null, errors: [] }), {
        status: 401,
      })
    await assert.rejects(request('/admin/me'), ApiError)
    assert.equal(expired, code === 'AUTH_FAILED' ? 0 : 1)
  }
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.has('Authorization'), false)
    return result(null)
  }
  await request('/admin/auth/logout', { method: 'POST' })
})

test('版本冲突保留错误语义，且不自动重试覆盖', async () => {
  let calls = 0
  globalThis.fetch = async () => {
    calls += 1
    return new Response(
      JSON.stringify({ code: 'REVISION_CONFLICT', msg: '版本冲突', data: null, errors: [] }),
      { status: 412 },
    )
  }
  await assert.rejects(
    request('/admin/articles/1', { method: 'PUT', revision: '"old"', body: { title: '我的修改' } }),
    (error) => error.status === 412 && /保留当前输入/.test(error.message),
  )
  assert.equal(calls, 1)
  await assert.rejects(request('/admin/articles/1', { revision: null }), /编辑版本/)
  assert.equal(calls, 1)
})

test('429 展示 Retry-After，文件上传由浏览器生成 multipart 边界', async () => {
  globalThis.fetch = async () =>
    new Response(JSON.stringify({ code: 'RATE_LIMITED', errors: [] }), {
      status: 429,
      headers: { 'Retry-After': '45' },
    })
  await assert.rejects(request('/test'), /45 秒/)
  const body = new FormData()
  body.append('file', new Blob(['test'], { type: 'image/png' }), 'test.png')
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.body, body)
    assert.equal(options.headers.has('Content-Type'), false)
    return result({ fileId: '1' })
  }
  await request('/admin/files', { method: 'POST', body })
})

test('私密图片只读取白名单后台路径，不向外部地址发送 Bearer', async () => {
  let calls = 0
  globalThis.fetch = async (_url, options) => {
    calls += 1
    assert.equal(options.headers.get('Authorization'), 'Bearer image-token')
    return new Response(new Blob(['png'], { type: 'image/png' }))
  }
  setAuthorization('image-token')
  await assert.rejects(getImage('https://external.invalid/file'), /不支持/)
  assert.equal(calls, 0)
  const url = await getImage('/api/v1/admin/articles/1/files/2/content')
  assert.equal(url.startsWith('blob:'), true)
  URL.revokeObjectURL(url)
  assert.equal(calls, 1)
})

test('表单校验错误展示具体字段信息，不回显请求中的密码', async () => {
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        code: 'VALIDATION_ERROR',
        msg: '参数校验失败',
        data: null,
        errors: [{ field: 'newPassword', message: 'UTF-8 不能超过72字节' }],
      }),
      { status: 400 },
    )
  await assert.rejects(
    request('/admin/me/password', { method: 'PUT', body: { newPassword: 'fake-password' } }),
    (error) => {
      assert.match(error.message, /newPassword UTF-8 不能超过72字节/)
      assert.equal(error.message.includes('fake-password'), false)
      return true
    },
  )
})


test('验证码保留前导零，经请求头提交并校验图片响应', async () => {
  const captcha = { captchaId: 'a'.repeat(43), imageBase64: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', expiresIn: 120 }
  let seen
  globalThis.fetch = async (url, options) => {
    seen = { url, options }
    return new Response(JSON.stringify({ code: 'OK', msg: '成功', data: captcha, errors: [] }), {
      headers: { 'Content-Type': 'application/json' },
    })
  }
  assert.deepEqual((await request('/admin/auth/captcha')).data, captcha)
  await request('/test', { method: 'POST', body: { operation: 'LOGIN' },
    credentials: 'include', captcha: { captchaId: captcha.captchaId, code: '012345' } })
  assert.equal(seen.options.headers.get('X-Captcha-Id'), captcha.captchaId)
  assert.equal(seen.options.headers.get('X-Captcha-Code'), '012345')
  assert.equal(seen.options.credentials, 'include')
  assert.deepEqual(JSON.parse(seen.options.body), { operation: 'LOGIN' })
  assert.equal(Object.hasOwn(seen.options, 'captcha'), false)
  await assert.rejects(request('/test', { captcha: { captchaId: captcha.captchaId, code: '12345' } }), /六位数字/)
  captcha.captchaId = '1'
  await assert.rejects(request('/admin/auth/captcha'), /验证码响应/)
  captcha.captchaId = 'a'.repeat(43)
  captcha.imageBase64 = 'https://example.test/image.png'
  await assert.rejects(request('/admin/auth/captcha'), /验证码响应/)
})


test('统一操作提交逐条版本并保留批量响应中的大整数ID', async () => {
  const id = '9223372036854775807'
  const revision = '"revision-for-selected-article"'
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/v1/admin/articles/batch-status')
    assert.equal(options.headers.has('If-Revision'), false)
    assert.deepEqual(JSON.parse(options.body), { items: [{ id, revision }], status: 1 })
    return result({ items: [{ id, revision }] })
  }
  const response = await request('/admin/articles/batch-status', {
    method: 'PUT', body: { items: [{ id, revision }], status: 1 },
  })
  assert.equal(response.data.items[0].id, id)
  globalThis.fetch = async () => result({ items: [{ id: 9007199254740992, revision }] })
  await assert.rejects(request('/admin/articles/batch-status'), /格式不正确/)
})
