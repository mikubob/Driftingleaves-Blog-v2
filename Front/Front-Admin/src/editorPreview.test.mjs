import assert from 'node:assert/strict'
import test from 'node:test'
import { adminArticleUrl, sanitizeEditorPreview } from './editorPreview.mjs'

test('编辑与已保存预览只把完整文章附件地址转为后台接口', () => {
  assert.equal(adminArticleUrl('/api/v1/blog/articles/1/files/7/content'), '/api/v1/admin/articles/1/files/7/content')
  assert.equal(adminArticleUrl('https://api.example.test/api/v1/blog/articles/1/files/7/content'), '/api/v1/admin/articles/1/files/7/content')
  assert.equal(adminArticleUrl('https://images.example.test/photo.png'), 'https://images.example.test/photo.png')
  assert.equal(adminArticleUrl('#中文标题'), '#中文标题')
})

test('保留代码块样式、复制按钮、行号和标题滚动标记', () => {
  const html = `<h2 id="标题" data-line="2">标题</h2>
    <details class="md-editor-code" open data-line="4">
      <summary class="md-editor-code-head"><span class="md-editor-copy-button" data-tips="复制代码">复制代码</span></summary>
      <pre data-code-source="int%20value%20%3D%201%3B%0A"><code class="language-java" language="java"><span class="md-editor-code-block"><span class="hljs-keyword">int</span> value = 1;</span><span rn-wrapper aria-hidden="true"><span></span></span></code></pre>
    </details>`
  const clean = sanitizeEditorPreview(html)
  for (const attribute of ['id="标题"', 'data-line="2"', 'class="md-editor-code"',
    'class="md-editor-code-head"', 'class="md-editor-copy-button"', 'data-tips="复制代码"',
    'class="hljs-keyword"', 'rn-wrapper', 'aria-hidden="true"', 'data-code-source="int%20value%20%3D%201%3B%0A"']) {
    assert.ok(clean.includes(attribute), attribute)
  }
  assert.ok(clean.includes('<details') && clean.includes(' open'))
})

test('保留静态折叠图标和任务列表，不把 SVG 显示成代码', () => {
  const html = `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="md-editor-icon"><circle cx="12" cy="12" r="10"/><path d="m14 16-4-4 4-4"/></svg><input type="checkbox" checked disabled>`
  const clean = sanitizeEditorPreview(html)
  assert.ok(clean.includes('<svg ') && clean.includes('<circle ') && clean.includes('<path '))
  assert.ok(clean.includes('viewbox="0 0 24 24"'))
  assert.ok(!clean.includes('&lt;svg'))
  assert.ok(clean.includes('type="checkbox"') && clean.includes('checked') && clean.includes('disabled'))
})

test('继续清理脚本、事件、危险链接和 SVG 执行入口，代码中的标签仍保留为文本', () => {
  const html = `<script>alert('secret')</script><style>body{display:none}</style>
    <img src="javascript:alert(1)" onerror="alert(1)" style="position:fixed">
    <a href="jav&#x61;script:alert(1)" onclick="alert(1)">链接</a>
    <svg onload="alert(1)"><use href="https://bad.test/x.svg#x"/><animate attributeName="href" values="javascript:alert(1)"/><path d="M0 0" onclick="alert(1)"/></svg>
    <pre><code>&lt;script&gt;示例&lt;/script&gt;</code></pre>`
  const clean = sanitizeEditorPreview(html)
  assert.doesNotMatch(clean, /<script|<style|onerror|onclick|onload|javascript:|<use|<animate|style=|secret/)
  assert.ok(clean.includes('&lt;script&gt;示例&lt;/script&gt;'))
})
