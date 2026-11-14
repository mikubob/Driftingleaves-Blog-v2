/**
 * 简历端交互动效：按压回弹（96% -> 103% -> 100%）与波纹跟手。
 */

const INTERACTIVE_SELECTOR =
  '.icon-button, .contact-list li:has(a), .section-nav a, .error-message button'

/**
 * 在指针按下位置生成跟手扩散波纹。
 * @param {HTMLElement} target
 * @param {PointerEvent} event
 */
function spawnRipple(target, event) {
  const rect = target.getBoundingClientRect()
  if (!rect.width || !rect.height) return

  const size = Math.max(rect.width, rect.height) * 2
  const x = event.clientX ? event.clientX - rect.left : rect.width / 2
  const y = event.clientY ? event.clientY - rect.top : rect.height / 2

  const ripple = document.createElement('span')
  ripple.className = 'dl-ripple'
  ripple.style.width = `${size}px`
  ripple.style.height = `${size}px`
  ripple.style.left = `${x - size / 2}px`
  ripple.style.top = `${y - size / 2}px`

  const existing = target.querySelectorAll('.dl-ripple')
  if (existing.length > 2) {
    existing[0].remove()
  }

  target.appendChild(ripple)
  ripple.addEventListener('animationend', () => ripple.remove(), { once: true })
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

/** 安装简历端全局指针交互动效。 */
export function installCvMotion() {
  let activeTarget = null

  const cancelPress = () => {
    if (!activeTarget) return
    activeTarget.classList.remove('is-pressing')
    activeTarget = null
  }

  document.addEventListener('pointerdown', (event) => {
    if (event.button !== 0 && event.pointerType === 'mouse') return
    const target = event.target?.closest?.(INTERACTIVE_SELECTOR)
    if (!target || target.disabled) return

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
}
