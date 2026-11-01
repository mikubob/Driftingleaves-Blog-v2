<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import emojiList from './emoji.json'

/** 局部表情面板，无输入 props；select(char) 通知调用者插入，卸载移除外部点击监听。 */

const emit = defineEmits(['select'])
const show = ref(false)
const pickerRef = ref(null)
const triggerRef = ref(null)

const toggle = () => {
  show.value = !show.value
}

const pickEmoji = (emoji) => {
  emit('select', emoji.char)
}

function closePicker() {
  show.value = false
  triggerRef.value?.focus()
}

/* 点击外部关闭 */
const onClickOutside = (e) => {
  if (
    pickerRef.value &&
    !pickerRef.value.contains(e.target) &&
    triggerRef.value &&
    !triggerRef.value.contains(e.target)
  ) {
    show.value = false
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div class="emoji-picker-wrap">
    <button
      type="button"
      ref="triggerRef"
      class="emoji-trigger"
      title="插入表情"
      :aria-expanded="show"
      @keydown.esc="show = false"
      @click="toggle"
    >
      <span aria-hidden="true">😊</span>
    </button>
    <Transition name="emoji-fade">
      <div v-show="show" ref="pickerRef" class="emoji-panel" @keydown.esc="closePicker">
        <div class="emoji-grid">
          <button
            type="button"
            v-for="e in emojiList"
            :key="e.codes"
            class="emoji-item"
            @click="pickEmoji(e)"
          >
            {{ e.char }}
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.emoji-picker-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
}
.emoji-trigger {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 20px;
  color: #909399;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  transition:
    color 0.15s,
    background 0.15s;
}
.emoji-trigger:hover {
  color: #303133;
  background: #f5f7fa;
}
.emoji-trigger .iconfont {
  font-size: 20px;
}
.emoji-panel {
  position: absolute;
  bottom: 40px;
  left: 0;
  width: 320px;
  max-height: 220px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  padding: 8px;
  z-index: 50;
  overscroll-behavior: contain;
}
.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
}
.emoji-item {
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 22px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.12s;
  user-select: none;
}
.emoji-item:hover {
  background: #f0f2f5;
}

/* 过渡动画 */
.emoji-fade-enter-active,
.emoji-fade-leave-active {
  transition:
    opacity 0.15s,
    transform 0.15s;
}
.emoji-fade-enter-from,
.emoji-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

/* 手机端适配 */
@media (max-width: 600px) {
  .emoji-panel {
    width: 280px;
    max-height: 200px;
    left: 50%;
    transform: translateX(-50%);
  }
  .emoji-grid {
    grid-template-columns: repeat(7, 1fr);
  }
  .emoji-item {
    border: 0;
    background: transparent;
    width: 34px;
    height: 34px;
    font-size: 20px;
  }
}
</style>
