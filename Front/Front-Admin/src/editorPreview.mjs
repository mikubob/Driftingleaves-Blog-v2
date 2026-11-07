/** 将文章附件转为后台鉴权路径，正文与外部普通链接保持原样。 */
export function adminArticleUrl(value) {
  return value.replace(/^(?:https?:\/\/[^/]+)?\/api\/v1\/(?:blog|admin)\/articles\/([1-9]\d*)\/files\/([1-9]\d*)\/content$/, '/api/v1/admin/articles/$1/files/$2/content')
}

/** 预览当前文章的图片，引用尚未保存时也能按后台文件权限读取原文件。 */
export function editorImageUrl(value, articleId) {
  const path = adminArticleUrl(value)
  const attachment = /^\/api\/v1\/admin\/articles\/([1-9]\d*)\/files\/([1-9]\d*)\/content$/.exec(path)
  if (attachment && attachment[1] === articleId) {
    return `/api/v1/admin/files/${attachment[2]}/content`
  }
  return path
}
