import { defineStore } from 'pinia'
import { computed, onScopeDispose, ref, watch } from 'vue'

/** 保存页面主题偏好，Store 销毁时移除系统主题监听。 */
export const useThemeStore = defineStore('theme', () => {
  let storedMode
  try {
    storedMode = localStorage.getItem('dl-blog-theme')
  } catch {
    // 浏览器禁用存储时仍允许本页切换。
  }
  const mode = ref(['light', 'dark'].includes(storedMode) ? storedMode : 'auto')
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(media.matches)
  const isDark = computed(() => mode.value === 'dark' || (mode.value === 'auto' && systemDark.value))

  function applyTheme() {
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  function toggle() {
    mode.value = isDark.value ? 'light' : 'dark'
  }

  function systemChanged(event) {
    systemDark.value = event.matches
  }

  watch(isDark, applyTheme, { immediate: true })
  watch(mode, value => {
    try {
      localStorage.setItem('dl-blog-theme', value)
    } catch {
      // 浏览器禁用存储时只保留本页偏好。
    }
  })
  media.addEventListener('change', systemChanged)
  onScopeDispose(() => media.removeEventListener('change', systemChanged))
  return { mode, isDark, toggle, applyTheme }
})
