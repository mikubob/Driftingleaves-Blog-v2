<script setup>
/**
 * 减速滚动数字组件：从旧值平滑计数到新值，接近目标时逐渐停下。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { formatCount } from '../format.js'

const props = defineProps({
  value: {
    type: [String, Number],
    default: null
  },
  duration: {
    type: Number,
    default: 520
  }
})

const MAX_SAFE_BIGINT = BigInt(Number.MAX_SAFE_INTEGER)

const isValidCount = computed(() => {
  if (typeof props.value === 'number') return Number.isFinite(props.value)
  return typeof props.value === 'string' && /^\d+$/.test(props.value)
})

const displayNumber = ref(0)
const isRolling = ref(false)
let rafId = 0

function stopAnimation() {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = 0
  }
}

/** 判断计数是否在安全整数范围内；超出范围时跳过数值滚动，直接保留 BIGINT 字符串精度。 */
function isSafeCount(raw) {
  if (typeof raw === 'number') {
    return Number.isSafeInteger(raw)
  }
  return BigInt(raw) <= MAX_SAFE_BIGINT
}

watch(
  () => props.value,
  (nextRaw) => {
    stopAnimation()
    if (!isValidCount.value) {
      isRolling.value = false
      displayNumber.value = 0
      return
    }
    if (!isSafeCount(nextRaw)) {
      isRolling.value = false
      return
    }

    const nextValue = Number(nextRaw)
    const startValue = displayNumber.value
    if (!Number.isFinite(nextValue) || startValue === nextValue) {
      isRolling.value = false
      displayNumber.value = nextValue
      return
    }

    isRolling.value = true
    const startTime = performance.now()
    const duration = Math.max(120, props.duration)

    const tick = (now) => {
      const progress = Math.min(1, (now - startTime) / duration)
      // 三次方减速曲线（ease-out）：接近目标值时自然停下。
      const eased = 1 - Math.pow(1 - progress, 3)
      displayNumber.value = Math.round(startValue + (nextValue - startValue) * eased)

      if (progress < 1) {
        rafId = requestAnimationFrame(tick)
      } else {
        displayNumber.value = nextValue
        isRolling.value = false
        rafId = 0
      }
    }

    rafId = requestAnimationFrame(tick)
  },
  { immediate: true }
)

onBeforeUnmount(stopAnimation)

const formattedText = computed(() => {
  if (!isValidCount.value) return '—'
  if (isRolling.value) {
    return formatCount(String(Math.max(0, displayNumber.value)))
  }
  return formatCount(String(props.value))
})
</script>

<template>
  <span class="rolling-number">{{ formattedText }}</span>
</template>
