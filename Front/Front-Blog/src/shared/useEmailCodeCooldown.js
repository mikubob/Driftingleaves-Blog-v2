import { onMounted, onUnmounted, ref, watch } from 'vue'

const storageKey = 'dl-email-code-resend-at'
const resendAt = ref(0)

/** 读取已保存的重发时间，刷新页面后继续读秒。 */
function readResendAt() {
  try {
    return Number(localStorage.getItem(storageKey)) || 0
  } catch {
    return 0
  }
}

/** 同一站点的发码入口共用重发时间，存储不可用时保留当前页面的倒计时。 */
export function useEmailCodeCooldown() {
  const secondsLeft = ref(0)
  let timer

  function updateSeconds() {
    clearTimeout(timer)
    const remaining = resendAt.value - Date.now()
    secondsLeft.value = Math.max(0, Math.ceil(remaining / 1000))
    if (remaining > 0) {
      timer = setTimeout(updateSeconds, remaining % 1000 || 1000)
    }
  }

  function syncResendAt(event) {
    if (event.key === storageKey || event.key === null) {
      resendAt.value = Math.max(resendAt.value, readResendAt())
    }
  }

  resendAt.value = Math.max(resendAt.value, readResendAt())
  watch(resendAt, updateSeconds, { immediate: true, flush: 'sync' })
  onMounted(() => window.addEventListener('storage', syncResendAt))
  onUnmounted(() => {
    clearTimeout(timer)
    window.removeEventListener('storage', syncResendAt)
  })

  /** 保存新的重发时间，已开始的倒计时只能延长。 */
  function startCooldown(seconds = 60) {
    resendAt.value = Math.max(resendAt.value, readResendAt(), Date.now() + seconds * 1000)
    try {
      localStorage.setItem(storageKey, String(resendAt.value))
    } catch {
      // 无法写入浏览器存储时，本页倒计时继续。
    }
  }

  return { secondsLeft, startCooldown }
}
