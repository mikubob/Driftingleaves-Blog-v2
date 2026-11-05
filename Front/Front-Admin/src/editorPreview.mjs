/** 将文章附件转为后台鉴权路径，正文与外部普通链接保持原样。 */
export function adminArticleUrl(value) {
  return value.replace(/^(?:https?:\/\/[^/]+)?\/api\/v1\/(?:blog|admin)\/articles\/([1-9]\d*)\/files\/([1-9]\d*)\/content$/, '/api/v1/admin/articles/$1/files/$2/content')
}
