import assert from 'node:assert/strict'
import test from 'node:test'
import { setCaptchaHeaders, validateCaptcha, validateArithmeticCaptcha } from './captcha.mjs'

const captchaId = 'a'.repeat(43)

test('算术答案支持零和两位数，图形验证码保留前导零', () => {
  for (const code of ['0', '9', '81', '012345']) {
    const headers = new Headers()
    setCaptchaHeaders(headers, { captchaId, code })
    assert.equal(headers.get('X-Captcha-Id'), captchaId)
    assert.equal(headers.get('X-Captcha-Code'), code)
  }
  for (const code of ['', '-1', '1.5', '1e1', '123', ' 9', 9]) {
    assert.throws(() => setCaptchaHeaders(new Headers(), { captchaId, code }))
  }
})

test('题目和图形响应分别检查，拒绝损坏的响应', () => {
  const arithmetic = { captchaId, question: '9 × 9 = ?', expiresIn: 120 }
  const image = { captchaId, imageBase64: 'data:image/png;base64,AAAA', expiresIn: 120 }
  assert.equal(validateArithmeticCaptcha(arithmetic), arithmetic)
  assert.equal(validateCaptcha(image), image)
  assert.throws(() => validateCaptcha(arithmetic))
  assert.throws(() => validateArithmeticCaptcha(image))
  assert.throws(() => validateArithmeticCaptcha({ ...arithmetic, captchaId: '12' }))
  assert.throws(() => validateArithmeticCaptcha({ ...arithmetic, expiresIn: 0 }))
  for (const question of ['9 ÷ 9 = ?', '9 a 9 = ?', '<img onerror=alert(1)>']) {
    assert.throws(() => validateArithmeticCaptcha({ ...arithmetic, question }))
  }
})
