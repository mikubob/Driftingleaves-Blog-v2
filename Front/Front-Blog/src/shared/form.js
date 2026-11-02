/** 校验密码字符数和 BCrypt 字节限制，不裁剪输入。 */
export function validatePassword(value) {
  const length = [...value].length
  return length >= 8 && length <= 64 && new TextEncoder().encode(value).length <= 72
}

