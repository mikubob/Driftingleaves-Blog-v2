<script setup>
import { computed } from 'vue'
import {
  Document,
  Refresh,
  MoreFilled,
  PriceTag,
  House,
  Share,
  Suitcase,
  Medal,
  UserFilled,
} from '@element-plus/icons-vue'
const props = defineProps({ name: String })
const iconFonts = {
  chart: 'icon-yibiaopan',
  document: 'icon-bianjiwenzhang_huaban',
  folder: 'icon-folder',
  chat: 'icon-comment',
  message: 'icon-liuyan',
  link: 'icon-link',
  picture: 'icon-image-fill',
  music: 'icon-music',
  mail: 'icon-rss',
  user: 'icon-user',
  lock: 'icon-lock',
  menu: 'icon-guanliduan',
  key: 'icon-shield',
  setting: 'icon-setting',
  log: 'icon-wj-rz',
  eye: 'icon-eye',
  left: 'icon-arrow-left-bold',
  right: 'icon-arrow-right-bold',
  logout: 'icon-logout',
  plus: 'icon-plus',
  search: 'icon-search',
  edit: 'icon-bianjiwenzhang_huaban',
  delete: 'icon-delete',
}
const fontClass = computed(() => iconFonts[props.name])
const icons = {
  tag: PriceTag,
  home: House,
  share: Share,
  briefcase: Suitcase,
  medal: Medal,
  visitor: UserFilled,
  refresh: Refresh,
  more: MoreFilled,
}
const icon = computed(() => icons[props.name] || Document)
</script>

<template>
  <span v-if="fontClass" :class="['iconfont', fontClass]" aria-hidden="true" />
  <el-icon v-else aria-hidden="true"><component :is="icon" /></el-icon>
</template>
