<script setup>
import { computed, ref } from 'vue'
import { ElButton, ElInput, ElMessage } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import { request } from '../api.js'
import { useEmailCodeCooldown } from '../shared/useEmailCodeCooldown.js'

const props = defineProps({
  currentEmail: { type: String, default: '' },
  codePath: { type: String, required: true },
  disabled: Boolean,
})
const email = defineModel({ type: String, required: true })
const code = defineModel('code', { type: String, required: true })
const sending = ref(false)
const { secondsLeft, startCooldown } = useEmailCodeCooldown()
const normalizedEmail = computed(() => email.value.trim().toLowerCase())
const needsCode = computed(() => normalizedEmail.value !== ''
  && normalizedEmail.value !== props.currentEmail.toLowerCase())

/** 向当前填写的新邮箱发送换绑验证码。 */
async function sendCode() {
  if (props.disabled || sending.value || secondsLeft.value || !needsCode.value) {
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail.value)) {
    ElMessage.error('请输入有效的新邮箱。')
    return
  }
  sending.value = true
  try {
    await request(props.codePath, {
      method: 'POST',
      body: { email: normalizedEmail.value },
      rateLimitInForm: true,
    })
    startCooldown()
    code.value = ''
    ElMessage.success('验证码已发送到新邮箱。')
  } catch (failure) {
    if (failure.status === 429) {
      startCooldown(failure.retryAfter)
    }
    ElMessage.error(failure.message)
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <label>
    绑定邮箱
    <el-input
      v-model.trim="email"
      type="email"
      maxlength="254"
      :required="Boolean(currentEmail)"
      :disabled="disabled || sending"
      @update:model-value="code = ''"
    />
  </label>
  <label v-if="needsCode">
    新邮箱验证码
    <el-input
      v-model="code"
      inputmode="numeric"
      autocomplete="one-time-code"
      maxlength="6"
      pattern="[0-9]{6}"
      required
      :disabled="disabled"
    />
  </label>
  <el-button
    v-if="needsCode || secondsLeft"
    type="primary"
    native-type="button"
    :loading="sending"
    :disabled="disabled || !needsCode || secondsLeft > 0"
    @click="sendCode"
  >
    {{ secondsLeft ? `${secondsLeft} 秒后重发` : '发送验证码到新邮箱' }}
  </el-button>
</template>
