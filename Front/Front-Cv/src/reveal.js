/**
 * 元素进入视口时触发平滑弹出，避免页面下方区块在滚动前就已播完入场动画。
 */
const observers = new WeakMap()

function revealElement(el) {
  el.classList.add('is-visible')
}

const sharedObserver = typeof IntersectionObserver === 'function'
  ? new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) {
        continue
      }
      revealElement(entry.target)
      sharedObserver.unobserve(entry.target)
    }
  }, {
    // threshold 为 0，避免超长卡片在视口占比不足时一直停在透明状态。
    threshold: 0,
    rootMargin: '80px 0px 0px 0px',
  })
  : null

export const vReveal = {
  mounted(el) {
    el.classList.add('reveal-pop')
    if (!sharedObserver) {
      revealElement(el)
      return
    }
    observers.set(el, sharedObserver)
    sharedObserver.observe(el)
  },
  unmounted(el) {
    observers.get(el)?.unobserve(el)
    observers.delete(el)
  },
}
