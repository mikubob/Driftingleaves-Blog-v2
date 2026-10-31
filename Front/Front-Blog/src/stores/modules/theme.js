import { defineStore } from 'pinia'
import { computed, onScopeDispose, ref, watch } from 'vue'

/** 沿用原站主题开关；系统监听随 Store 销毁，不持久化身份。 */
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
