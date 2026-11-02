import xss from 'xss'
import { createPreviewFilter } from '../../shared/previewSanitizer.mjs'

const previewFilter = createPreviewFilter(xss)

/** 清理编辑器实时预览，保留代码工具栏、高亮、行号和折叠图标。 */
export function sanitizeEditorPreview(html) {
  return previewFilter.process(html)
}

/** 将文章附件转为后台鉴权路径，正文与外部普通链接保持原样。 */
export function adminArticleUrl(value) {
  return value.replace(/^(?:https?:\/\/[^/]+)?\/api\/v1\/(?:blog|admin)\/articles\/([1-9]\d*)\/files\/([1-9]\d*)\/content$/, '/api/v1/admin/articles/$1/files/$2/content')
}
