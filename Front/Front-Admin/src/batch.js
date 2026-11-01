import { isId, request } from './api.js'

/** 仅收集用户显式选择的目标；详情读取失败时整批停止，不退回逐条写入。 */
export async function prepareBatch(resource, idKey, rows, needsRevision = true) {
  const ids = rows.map((row) => row[idKey])
  if (!ids.length || ids.length > 100 || !ids.every(isId) || new Set(ids).size !== ids.length) {
    throw new Error('请选择 1～100 条不重复的有效记录。')
  }
  if (!needsRevision) {
    return { ids }
  }
  const items = []
  // 顺序读取最多一页详情，避免一次发出100个并发请求；写入仍只发送一个批次。
  for (const id of ids) {
    const { revision } = await request(`${resource}/${id}`)
    if (typeof revision !== 'string' || !/^"[0-9a-f]{64}"$/.test(revision)) {
      throw new Error('未取得有效版本，请刷新列表后重试。')
    }
    items.push({ id, revision })
  }
  return { items }
}
