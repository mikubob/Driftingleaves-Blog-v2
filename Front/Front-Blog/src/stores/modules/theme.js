import { defineStore } from 'pinia'
import { computed, onScopeDispose, ref, watch } from 'vue'

/** 切换页面主题，Store 销毁时移除系统主题监听，不保存登录信息。 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref('auto')
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
  media.addEventListener('change', systemChanged)
  onScopeDispose(() => media.removeEventListener('change', systemChanged))
  return { mode, isDark, toggle, applyTheme }
})
