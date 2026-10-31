<script setup>
defineProps({ name: { type: String, default: 'link' } })

// 数据库图标键只在此白名单解析，未知键使用链接图标。
const paths = {
  education: 'M2 9 12 4l10 5-10 5L2 9Zm4 2v7c4 3 8 3 12 0v-7M22 9v7',
  work: 'M8 7V4h8v3M3 7h18v14H3V7Zm0 6c6 3 12 3 18 0M12 12v5',
  project: 'm12 3 10 5-10 5L2 8l10-5ZM2 12l10 5 10-5M2 16l10 5 10-5',
  code: 'm8 5-6 7 6 7m8-14 6 7-6 7m-3-16-2 18',
  mail: 'M3 5h18v14H3V5Zm0 1 9 7 9-7',
  globe: 'M2 12h20M12 2c5 5 5 15 0 20-5-5-5-15 0-20Zm10 10a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
  location: 'M19 10c0 6-7 12-7 12S5 16 5 10a7 7 0 1 1 14 0ZM15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  print: 'M6 9V3h12v6M6 18H3v-7h18v7h-3M6 14h12v8H6v-8Z',
  sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0Z',
  moon: 'M21 13A9 9 0 0 1 11 3a9 9 0 1 0 10 10Z',
  link: 'm10 13 4-4M8 16l-2 2a4 4 0 0 1-6-6l5-5a4 4 0 0 1 6 0m2 1 2-2a4 4 0 0 1 6 6l-5 5a4 4 0 0 1-6 0',
  github: 'M9 19c-5 2-5-2-7-2m14 5v-4c0-1-.3-2-1-2 4-.5 6-2 6-6 0-1.5-.5-2.5-1-3 .2-1 .2-2-0.3-3-2-.1-3 1-3 1-3-.8-5-.8-8 0 0 0-1-1.1-3-1-.5 1-.5 2-.3 3-.5.5-1 1.5-1 3 0 4 2 5.5 6 6-.7 0-1 1-1 2v4',
  document: 'M5 2h9l5 5v15H5V2Zm9 0v6h5M8 12h8m-8 4h8',
  setting: 'm9 3-1 3-3 1 1 3-2 2 2 2-1 3 3 1 1 3h6l1-3 3-1-1-3 2-2-2-2 1-3-3-1-1-3H9Zm6 9a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  home: 'm2 11 10-9 10 9M5 9v13h5v-7h4v7h5V9',
  chart: 'M3 3v18h18M7 16v-5m5 5V6m5 10V9',
  folder: 'M2 5h8l2 3h10v12H2V5Z',
  tag: 'M3 3h9l10 10-9 9L3 12V3Zm4 4h.01',
  chat: 'M3 3h18v14H8l-5 4V3Z',
  picture: 'M3 3h18v18H3V3Zm0 14 6-6 5 5 3-3 4 4M8 7h.01',
  music: 'M9 18V5l12-3v13M9 7l12-3M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm12-3a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 22v-3a8 8 0 0 1 16 0v3',
  lock: 'M7 10V6a5 5 0 0 1 10 0v4M4 10h16v12H4V10Zm8 5v3',
  menu: 'M3 6h18M3 12h18M3 18h18',
  key: 'M14 10a5 5 0 1 1 0-1m0 1 8 8v4h-4v-3h-3l-4-4',
}
</script>

<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="Object.hasOwn(paths, name) ? paths[name] : paths.link" />
  </svg>
</template>
