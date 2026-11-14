<script setup>
/**
 * 减速滚动数字组件：从旧值平滑计数到新值，接近目标时逐渐停下，并保留 BIGINT 字符串精度。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  value: {
    type: [Number, String],
    default: 0
  },
  duration: {
    type: Number,
    default: 520
  },
  formatter: {
    type: Function,
    default: null
  }
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

watch(
  () => props.value,
  (nextRaw) => {
    stopAnimation()
    const nextValue = Number(nextRaw)
    if (!Number.isFinite(nextValue)) {
      isRolling.value = false
      displayNumber.value = 0
      return
    }

    const startValue = displayNumber.value
    if (startValue === nextValue) {
      isRolling.value = false
      return
    }

    isRolling.value = true
    const startTime = performance.now()
    const duration = Math.max(120, props.duration)

    const tick = (now) => {
      const progress = Math.min(1, (now - startTime) / duration)
      // 三次方减速曲线（ease-out）：先快后慢，接近目标值时自然停下。
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
  const raw = isRolling.value ? displayNumber.value : (props.value ?? 0)
  if (typeof props.formatter === 'function') {
    return props.formatter(raw)
  }
  return String(raw)
})
</script>

<template>
  <span class="rolling-number">{{ formattedText }}</span>
</template>
