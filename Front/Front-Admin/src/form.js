/** 将表单中的空白文本转为空值，其他类型保持原样。 */
export function nullable(value) {
  return typeof value === 'string' && !value.trim() ? null : value
}

/** 校验密码字符数和 BCrypt 字节限制，不裁剪输入。 */
export function validatePassword(value) {
  const length = [...value].length
  return length >= 8 && length <= 64 && new TextEncoder().encode(value).length <= 72
}

