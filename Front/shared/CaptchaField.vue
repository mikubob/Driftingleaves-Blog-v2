<script setup>
import { computed, onBeforeUnmount, onMounted, ref, useId } from 'vue'

const props = defineProps({
  request: { type: Function, required: true },
  endpoint: { type: String, required: true },
  disabled: Boolean,
})
const inputId = useId()
const image = ref(null)
const code = ref('')
const loading = ref(false)
const error = ref('')
const ready = computed(() => !!image.value && /^[0-9]{6}$/.test(code.value) && !loading.value)
let controller

/** 刷新立即丢弃旧输入，请求结束后只能提交新图片对应的答案。 */
async function refresh() {
  if (loading.value) {
    return
  }
  image.value = null
  code.value = ''
  error.value = ''
  loading.value = true
  controller = new AbortController()
  try {
    const { data } = await props.request(props.endpoint, { signal: controller.signal })
    image.value = data
  } catch (failure) {
    if (failure.name !== 'AbortError') {
      error.value = failure.message
    }
  } finally {
    loading.value = false
  }
}

/** 父表单只在提交时读取验证码，格式由统一请求入口检查。 */
function getValue() {
  return { captchaId: image.value?.captchaId, code: code.value }
}

defineExpose({ refresh, getValue, ready })
onMounted(refresh)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div class="captcha-field" :aria-busy="loading">
    <label :for="inputId">图形验证码</label>
    <div class="captcha-controls">
      <input
        :id="inputId"
        v-model="code"
        aria-label="六位数字验证码"
        inputmode="numeric"
        autocomplete="off"
        pattern="[0-9]{6}"
        maxlength="6"
        required
        placeholder="六位数字"
        :disabled="disabled || loading || !image"
      />
      <button type="button" :disabled="disabled || loading" aria-label="刷新验证码" @click="refresh">
        <img v-if="image" :src="image.imageBase64" alt="六位数字图形验证码，点击刷新" width="200" height="80" />
        <span v-else>{{ loading ? '加载中…' : '重新获取' }}</span>
      </button>
    </div>
    <p v-if="error" class="captcha-error" role="alert">{{ error }}</p>
    <small>两分钟内有效，点击图片可换一张。</small>
  </div>
</template>

<style scoped>
.captcha-field {
  display: grid;
  gap: 6px;
  margin: 12px 0;
  min-width: 0;
  font-size: 14px;
}
.captcha-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.captcha-controls input {
  box-sizing: border-box;
  flex: 1;
  min-width: 100px;
  width: 120px;
  padding: 10px 12px;
  border: 1px solid #c5c8ce;
  border-radius: 6px;
  font: inherit;
}
.captcha-controls input:focus {
  border-color: currentColor;
  outline: none;
}
.captcha-controls button {
  padding: 0;
  min-width: 125px;
  min-height: 50px;
  border: 1px solid #c5c8ce;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  overflow: hidden;
}
.captcha-controls img {
  display: block;
  width: 125px;
  height: 50px;
}
.captcha-field small {
  color: #6b7280;
}
.captcha-error {
  margin: 0;
  color: #c0392b;
}
</style>
