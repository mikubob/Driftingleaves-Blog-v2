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

/** 在请求边界写入校验头，保留六位数字的前导零。 */
export function setCaptchaHeaders(headers, captcha) {
  if (captcha === undefined) {
    return
  }
  if (!captcha || !isCaptchaId(captcha.captchaId) || typeof captcha.code !== 'string'
    || !/^[0-9]{6}$/.test(captcha.code)) {
    throw new Error('请获取验证码并输入图片中的六位数字。')
  }
  headers.set('X-Captcha-Id', captcha.captchaId)
  headers.set('X-Captcha-Code', captcha.code)
}
