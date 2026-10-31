<script setup>
import { computed } from 'vue'

/** name 为本地注册键；数据库内容不会成为组件名、HTML 或动态模块。 */
const props = defineProps({ name: { type: String, default: 'link' } })

const paths = {
  link: 'm10 13 4-4M8 16l-2 2a4 4 0 0 1-6-6l5-5a4 4 0 0 1 6 0m2 1 2-2a4 4 0 0 1 6 6l-5 5a4 4 0 0 1-6 0',
  github: 'M9 19c-5 2-5-2-7-2m14 5v-4c0-1-.3-2-1-2 4-.5 6-2 6-6 0-1.5-.5-2.5-1-3 .2-1 .2-2-0.3-3-2-.1-3 1-3 1-3-.8-5-.8-8 0 0 0-1-1.1-3-1-.5 1-.5 2-.3 3-.5.5-1 1.5-1 3 0 4 2 5.5 6 6-.7 0-1 1-1 2v4',
  mail: 'M3 5h18v14H3V5Zm0 1 9 7 9-7',
  globe: 'M2 12h20M12 2c5 5 5 15 0 20-5-5-5-15 0-20Zm10 10a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  location: 'M19 10c0 6-7 12-7 12S5 16 5 10a7 7 0 1 1 14 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  document: 'M5 2h9l5 5v15H5V2Zm9 0v6h5M8 12h8m-8 4h8',
  blog: 'M4 3h16v18H4V3Zm4 5h8m-8 4h8m-8 4h5',
  code: 'm8 5-6 7 6 7m8-14 6 7-6 7m-3-16-2 18',
  bilibili: 'm8 2 4 4 4-4M3 6h18v15H3V6Zm4 5v3m10-3v3m-8 3h6',
}
const aliases = {
  youxiang: 'mail',
  email: 'mail',
  jianli: 'document',
  cv: 'document',
  boke: 'blog',
  'bilibili-line': 'bilibili',
  csdn1: 'code',
}
const iconPath = computed(() => {
  const key = Object.hasOwn(aliases, props.name) ? aliases[props.name] : props.name
  return Object.hasOwn(paths, key) ? paths[key] : paths.link
})
</script>

<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="iconPath" />
  </svg>
</template>
