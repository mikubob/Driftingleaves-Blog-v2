export const isCaptchaId = (value) => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value)

/** 验证码标识独立于业务 ID；只接受 PNG Data URI，不接收外部图片地址。 */
export function validateCaptcha(value) {
  if (!value || !isCaptchaId(value.captchaId) || value.expiresIn !== 120
    || typeof value.imageBase64 !== 'string'
    || !/^data:image\/png;base64,[A-Za-z0-9+/]+={0,2}$/.test(value.imageBase64)) {
    throw new Error('验证码响应格式不正确，请重新获取。')
  }
  return value
}

/** 校验算术题响应，只接收一至九的加减乘题，不包含答案。 */
export function validateArithmeticCaptcha(value) {
  if (!value || !isCaptchaId(value.captchaId) || value.expiresIn !== 120
    || typeof value.question !== 'string' || !/^[1-9] [+×-] [1-9] = \?$/.test(value.question)) {
    throw new Error('验证题目响应格式不正确，请重新获取。')
  }
  return value
}

/** 发送图形或算术验证码请求头，图形验证码保留开头的零。 */
export function setCaptchaHeaders(headers, captcha) {
  if (captcha === undefined) {
    return
  }
  if (!captcha || !isCaptchaId(captcha.captchaId) || typeof captcha.code !== 'string'
    || !/^(?:[0-9]{6}|[0-9]{1,2})$/.test(captcha.code)) {
    throw new Error('请获取验证码并填写图片中的数字或题目答案。')
  }
  headers.set('X-Captcha-Id', captcha.captchaId)
  headers.set('X-Captcha-Code', captcha.code)
}
