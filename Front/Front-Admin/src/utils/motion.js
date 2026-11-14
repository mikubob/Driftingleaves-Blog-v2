/**
 * 管理端全局交互动效：
 * 1. 按压回弹（按下 96%，松手 103% 再落回 100%）
 * 2. 波纹跟手（从指针按下坐标扩散）
 * 3. 弹性开关（切换时圆点横向拉成胶囊形，到达终点缩回圆形带轻微回弹）
 */

const PRESS_SELECTOR = '.el-button:not(.is-disabled), button:not(:disabled):not(.mobile-menu-btn)'

/**
 * 在指针按下位置生成跟手扩散波纹（使用独立裁切层，不破坏焦点环与徽标）。
 * @param {HTMLElement} target
 * @param {PointerEvent} event
 */
function spawnRipple(target, event) {
  if (target.classList.contains('el-switch')) return

  const style = window.getComputedStyle(target)
  if (style.position === 'static') {
    target.style.position = 'relative'
  }

  const rect = target.getBoundingClientRect()
  if (!rect.width || !rect.height) return

  let layer = target.querySelector(':scope > .dl-ripple-layer')
  if (!layer) {
    layer = document.createElement('span')
    layer.className = 'dl-ripple-layer'
    layer.setAttribute('aria-hidden', 'true')
    target.appendChild(layer)
  }

  const size = Math.max(rect.width, rect.height) * 2
  const x = event.clientX ? event.clientX - rect.left : rect.width / 2
  const y = event.clientY ? event.clientY - rect.top : rect.height / 2

  const ripple = document.createElement('span')
  ripple.className = 'dl-ripple'
  ripple.style.width = `${size}px`
  ripple.style.height = `${size}px`
  ripple.style.left = `${x - size / 2}px`
  ripple.style.top = `${y - size / 2}px`

  const existing = layer.querySelectorAll('.dl-ripple')
  if (existing.length > 2) {
    existing[0].remove()
  }

  layer.appendChild(ripple)
  ripple.addEventListener(
    'animationend',
    () => {
      ripple.remove()
      if (!layer.childElementCount) {
        layer.remove()
      }
    },
    { once: true }
  )
}

/**
 * 触发松手回弹关键帧（96% -> 103% -> 100%）。
 * @param {HTMLElement} target
 */
function triggerRebound(target) {
  if (!target.classList.contains('is-pressing')) return
  target.classList.remove('is-pressing')
  target.classList.remove('is-rebounding')
  void target.offsetWidth
  target.classList.add('is-rebounding')
  target.addEventListener(
    'animationend',
    () => target.classList.remove('is-rebounding'),
    { once: true }
  )
}

/**
 * 为 Element Plus 开关触发胶囊形弹性形变。
 * @param {HTMLElement} switchEl
 */
function triggerSwitchStretch(switchEl) {
  const action = switchEl.querySelector('.el-switch__action')
  if (!action) return
  action.classList.remove('is-stretching')
  void action.offsetWidth
  action.classList.add('is-stretching')
  action.addEventListener(
    'animationend',
    () => action.classList.remove('is-stretching'),
    { once: true }
  )
}

/** 安装管理端全局交互动效监听器。 */
export function installAdminMotion() {
  let activeTarget = null

  const cancelPress = () => {
    if (!activeTarget) return
    activeTarget.classList.remove('is-pressing')
    activeTarget = null
  }

  document.addEventListener('pointerdown', (event) => {
    if (event.button !== 0 && event.pointerType === 'mouse') return
    const target = event.target?.closest?.(PRESS_SELECTOR)
    if (!target) return

    cancelPress()
    activeTarget = target
    target.classList.remove('is-rebounding')
    target.classList.add('is-pressing')
    target.addEventListener('pointerleave', cancelPress, { once: true })
    spawnRipple(target, event)
  })

  document.addEventListener('pointerup', () => {
    if (!activeTarget) return
    const target = activeTarget
    activeTarget = null
    triggerRebound(target)
  })

  document.addEventListener('pointercancel', cancelPress)
  window.addEventListener('blur', cancelPress)

  document.addEventListener('click', (event) => {
    const switchEl = event.target?.closest?.('.el-switch:not(.is-disabled)')
    if (switchEl) {
      triggerSwitchStretch(switchEl)
    }
  })
}
