/** 审计时间只在显示时转换，不把本地字符串写回接口。 */
export function formatTime(value) {
  if (!value) {
    return '—'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function formatCount(value) {
  return typeof value === 'string' && /^\d+$/.test(value)
    ? new Intl.NumberFormat('zh-CN').format(BigInt(value))
    : '—'
}

export function nullable(value) {
  return typeof value === 'string' && !value.trim() ? null : value
}

export function validatePassword(value) {
  const length = [...value].length
  return length >= 8 && length <= 64 && new TextEncoder().encode(value).length <= 72
}

/** 只将计算后的有限比例转换为数字，计数本身保留 BIGINT 精度。 */
export function ratio(value, maximum) {
  const max = BigInt(maximum || '0')
  return max === 0n ? 0 : Number((BigInt(value) * 10000n) / max) / 100
}
