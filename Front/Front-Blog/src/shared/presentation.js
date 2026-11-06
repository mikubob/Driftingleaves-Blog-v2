/** 整理文章和互动组件使用的展示字段，id和计数保持字符串。 */
export function articleView(article) {
  return {
    ...article,
    id: article.articleId,
    coverImage: article.coverUrl,
    categoryName: article.category?.categoryName,
    tagNames: article.tags?.map((tag) => tag.tagName) || [],
    readingTime: article.readingMinutes,
  }
}

/** 整理评论和留言的展示字段，作者已删除时使用占位昵称。 */
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
