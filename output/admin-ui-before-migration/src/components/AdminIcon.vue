<script setup>
import { computed } from 'vue'
import {
  DataAnalysis,
  Document,
  Folder,
  PriceTag,
  ChatDotRound,
  Link,
  Picture,
  Headset,
  Message,
  User,
  Lock,
  Menu,
  Key,
  Setting,
  House,
  ArrowLeft,
  ArrowRight,
  SwitchButton,
  Plus,
  Search,
  Refresh,
  EditPen,
  Delete,
  MoreFilled,
} from '@element-plus/icons-vue'
const props = defineProps({ name: String })
const iconFonts = {
  chart: 'icon-yibiaopan',
  document: 'icon-bianjiwenzhang_huaban',
  folder: 'icon-folder',
  tag: 'icon-folder',
  chat: 'icon-comment',
  link: 'icon-link',
  picture: 'icon-image-fill',
  music: 'icon-music',
  mail: 'icon-rss',
  user: 'icon-user',
  lock: 'icon-lock',
  menu: 'icon-guanliduan',
  key: 'icon-shield',
  setting: 'icon-setting',
  home: 'icon-iconfontprofile',
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
  chart: DataAnalysis,
  document: Document,
  folder: Folder,
  tag: PriceTag,
  chat: ChatDotRound,
  link: Link,
  picture: Picture,
  music: Headset,
  mail: Message,
  user: User,
  lock: Lock,
  menu: Menu,
  key: Key,
  setting: Setting,
  home: House,
  left: ArrowLeft,
  right: ArrowRight,
  logout: SwitchButton,
  plus: Plus,
  search: Search,
  refresh: Refresh,
  edit: EditPen,
  delete: Delete,
  more: MoreFilled,
}
const icon = computed(() => icons[props.name] || Document)
</script>

<template>
  <span v-if="fontClass" :class="['iconfont', fontClass]" aria-hidden="true" />
  <el-icon v-else aria-hidden="true"><component :is="icon" /></el-icon>
</template>
