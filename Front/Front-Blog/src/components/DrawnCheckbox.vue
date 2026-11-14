<script setup>
/**
 * 手写勾选复选框：选中时对勾从左下到右上在约 200ms 内一笔画出。
 */
defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  label: {
    type: String,
    default: ''
  },
  ariaLabel: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

function onChange(event) {
  emit('update:modelValue', event.target.checked)
}
</script>

<template>
  <label class="drawn-checkbox" :class="{ 'is-disabled': disabled }">
    <input
      type="checkbox"
      class="drawn-checkbox__input"
      :checked="modelValue"
      :disabled="disabled"
      :aria-label="ariaLabel || label || undefined"
      @change="onChange"
    />
    <span class="drawn-checkbox__box" aria-hidden="true">
      <svg viewBox="0 0 16 16" class="drawn-checkbox__svg">
        <path
          d="M3.5 8.5 L6.5 11.5 L12.5 4.5"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="drawn-checkbox__path"
        />
      </svg>
    </span>
    <span class="drawn-checkbox__label">
      <slot>{{ label }}</slot>
    </span>
  </label>
</template>

<style scoped>
.drawn-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--blog-text2, #606266);
  user-select: none;
}

.drawn-checkbox.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.drawn-checkbox__input {
  position: absolute;
  opacity: 0;
  width: 1px;
  height: 1px;
  margin: 0;
  pointer-events: none;
}

.drawn-checkbox__box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  border: 1.5px solid var(--blog-border, #c0c4cc);
  background: var(--blog-card, #ffffff);
  color: var(--blog-card, #ffffff);
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease;
}

.drawn-checkbox__input:focus-visible + .drawn-checkbox__box {
  outline: 2px solid var(--blog-text, #111111);
  outline-offset: 2px;
}

.drawn-checkbox__input:checked + .drawn-checkbox__box {
  background: var(--blog-text, #303133);
  border-color: var(--blog-text, #303133);
}

.drawn-checkbox__svg {
  width: 12px;
  height: 12px;
}

.drawn-checkbox__path {
  stroke-dasharray: 16;
  stroke-dashoffset: 16;
  transition: stroke-dashoffset 0.2s cubic-bezier(0.22, 1, 0.36, 1);
}

.drawn-checkbox__input:checked + .drawn-checkbox__box .drawn-checkbox__path {
  stroke-dashoffset: 0;
}
</style>
