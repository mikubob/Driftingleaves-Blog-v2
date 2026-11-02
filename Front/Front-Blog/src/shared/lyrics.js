/** LRC/JSON 统一为秒数与文本；TXT 保留全文，不把歌词当 HTML。 */
export function parseLyrics(source, type) {
  const text = source.replace(/^\uFEFF/, '')
  if (!text.trim() || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f]/u.test(text)) {
    throw new Error('歌词内容无效。')
  }
  if (type === 'txt') {
    return [{ time: null, text }]
  }
  if (type === 'json') {
    const lines = JSON.parse(text)
    if (!Array.isArray(lines) || !lines.length || !lines.every((line) =>
      line && typeof line.time === 'number' && Number.isFinite(line.time) && line.time >= 0
      && typeof line.text === 'string')) {
      throw new Error('JSON 歌词须为含 time（秒）和 text 的数组。')
    }
    return lines.sort((left, right) => left.time - right.time)
  }
  if (type !== 'lrc') {
    throw new Error('不支持的歌词格式。')
  }
  const offsets = [...text.matchAll(/\[offset:([+-]?\d+)\]/gi)]
  const offset = offsets.length ? Number(offsets.at(-1)[1]) / 1000 : 0
  if (!Number.isFinite(offset)) {
    throw new Error('歌词偏移量无效。')
  }
  const lines = []
  for (const line of text.split(/\r?\n/)) {
    const stamps = [...line.matchAll(/\[(\d{1,6}):([0-5]\d(?:\.\d{1,3})?)\]/g)]
    const words = line.replace(/\[[^\]]*\]/g, '').trim()
    for (const stamp of stamps) {
      lines.push({ time: Number(stamp[1]) * 60 + Number(stamp[2]) - offset, text: words })
    }
  }
  if (!lines.length) {
    throw new Error('LRC 歌词缺少有效时间标签。')
  }
  return lines.sort((left, right) => left.time - right.time)
}

/** 外部歌词同样限 1 MiB，直接匿名读取；切歌或关闭面板时调用方取消请求。 */
export async function loadLyrics(url, type, signal) {
  const response = await fetch(url, { signal, credentials: 'omit', cache: 'no-store' })
  if (!response.ok || !response.body) {
    throw new Error(`歌词读取失败（HTTP ${response.status}）。`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8', { fatal: true })
  let bytes = 0
  let text = ''
  try {
    while (true) {
      const chunk = await reader.read()
      if (chunk.done) {
        break
      }
      bytes += chunk.value.byteLength
      if (bytes > 1024 * 1024) {
        throw new Error('歌词超过 1 MiB。')
      }
      text += decoder.decode(chunk.value, { stream: true })
    }
    text += decoder.decode()
  } finally {
    await reader.cancel()
    reader.releaseLock()
  }
  return parseLyrics(text, type)
}
