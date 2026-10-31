import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { acceptSession, can, clearSession, session } from '../src/session.js'
import { allowedPages, flattenMenus, isInternalPath } from '../src/navigation.js'
import { formatCount, ratio, validatePassword } from '../src/format.js'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
  clearSession()
})

test('登录从后端读取有效权限，ALL 与 SELF 按独立权限判断', async () => {
  const responses = {
    '/api/v1/me': { userId: '9007199254740993', nickname: '测试作者' },
    '/api/v1/me/capabilities': {
      roleCodes: ['author'],
      permissionCodes: ['admin:access', 'blog:article:query', 'blog:statistics:query'],
      dataScopes: { 'blog:article:query': 'SELF', 'blog:statistics:query': 'SELF' },
    },
    '/api/v1/me/menus': [],
  }
  globalThis.fetch = async (url, options) => {
    assert.equal(options.headers.get('Authorization'), 'Bearer fake-session')
    return new Response(
      JSON.stringify({ code: 'OK', msg: '成功', data: responses[url], errors: [] }),
    )
  }
  await acceptSession({ tokenType: 'Bearer', accessToken: 'fake-session' })
  assert.equal(session.ready, true)
  assert.equal(can('blog:article:query'), true)
  assert.equal(can('blog:statistics:query', true), false)
  assert.equal(can('sys:role:grant'), false)
  clearSession()
  assert.equal(session.ready, false)
  assert.equal(session.user, null)
})

test('没有 admin:access 的普通账号不能进入后台', async () => {
  globalThis.fetch = async (url) => {
    let data = []
    if (url.endsWith('/me')) {
      data = { userId: '1' }
    }
    if (url.endsWith('/capabilities')) {
      data = { roleCodes: ['member'], permissionCodes: [], dataScopes: {} }
    }
    return new Response(JSON.stringify({ code: 'OK', data, errors: [] }))
  }
  await assert.rejects(
    acceptSession({ tokenType: 'Bearer', accessToken: 'member-token' }),
    /没有进入/,
  )
  assert.equal(session.ready, false)
})

test('菜单白名单支持隐藏页面，按钮、停用节点、未知组件不生成路由', () => {
  const base = {
    menuType: 'M',
    status: 1,
    isVisible: 0,
    component: 'content/article/index',
    path: '/content/articles',
  }
  const tree = [
    {
      menuId: '1',
      menuType: 'D',
      children: [
        { ...base, menuId: '2' },
        { ...base, menuId: '3', menuType: 'B' },
        { ...base, menuId: '4', component: 'https://untrusted.invalid/script.js' },
        { ...base, menuId: '5', status: 0 },
        { ...base, menuId: '6', path: '//untrusted.invalid' },
        {
          ...base,
          menuId: '7',
          path: '/statistics/overview',
          component: 'statistics/overview/index',
        },
      ],
    },
  ]
  const capabilities = {
    permissionCodes: ['blog:article:query', 'blog:statistics:query'],
    dataScopes: { 'blog:statistics:query': 'SELF' },
  }
  assert.deepEqual(
    allowedPages(tree, capabilities).map((node) => node.menuId),
    ['2'],
  )
  capabilities.dataScopes['blog:statistics:query'] = 'ALL'
  assert.deepEqual(
    allowedPages(tree, capabilities).map((node) => node.menuId),
    ['2', '7'],
  )
  assert.equal(flattenMenus(tree).length, 7)
  assert.equal(isInternalPath('/content/../login'), false)
})

test('密码不 trim、不截断，并按 Unicode 字符与 UTF-8 字节限制判断', () => {
  assert.equal(validatePassword('   pass   '), true)
  assert.equal(validatePassword('1234567'), false)
  assert.equal(validatePassword('😀'.repeat(18)), true)
  assert.equal(validatePassword('😀'.repeat(19)), false)
  assert.equal(validatePassword('a'.repeat(65)), false)
})

test('统计展示保留大整数精度，图表只转换最终百分比', () => {
  assert.equal(formatCount('9223372036854775807'), '9,223,372,036,854,775,807')
  assert.equal(ratio('4611686018427387903', '9223372036854775807'), 49.99)
  assert.equal(ratio('0', '0'), 0)
})
