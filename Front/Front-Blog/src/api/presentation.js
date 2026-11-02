/** 把 V2 接口字段转换成老组件需要的属性，id和计数保持字符串。 */
export function articleView(article) {
  return {
    ...article,
    id: article.articleId,
    coverImage: article.coverUrl,
    categoryName: article.category?.categoryName,
    tagNames: article.tags?.map((tag) => tag.tagName) || [],
    contentHtml: article.htmlContent,
    readingTime: article.readingMinutes,
  }
}

export function discussionView(item) {
  return {
    ...item,
    id: item.commentId || item.messageId,
    nickname: item.author?.nickname || '已删除的用户',
    avatar: item.author?.avatar,
    isApproved: item.status,
    contentHtml: item.htmlContent,
  }
}
