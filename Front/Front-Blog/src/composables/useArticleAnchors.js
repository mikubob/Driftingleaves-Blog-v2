import { nextTick, onBeforeUnmount, onMounted } from 'vue'
import { ensureHeadingIds, findFragmentTarget } from '@/utils/markdownAnchors'

/**
 * 文章内容渲染后补齐锚点并恢复 URL 片段；保留原生片段历史和键盘链接行为。
 * @param {import('vue').Ref<HTMLElement|null>} contentRef 当前文章容器
 * @returns {{onContentRendered: Function, onAnchorClick: Function}} 渲染后回调与捕获点击处理
 */
export const useArticleAnchors = (contentRef) => {
  let active = true
  const scrollToFragment = (hash, behavior = 'smooth') => {
    const target = findFragmentTarget(contentRef.value, hash)
    if (!target) return false
    target.scrollIntoView({ block: 'start', behavior })
    return true
  }

  const restoreFragment = () => scrollToFragment(window.location.hash, 'auto')

  const onContentRendered = async () => {
    await nextTick()
    if (!active) return
    ensureHeadingIds(contentRef.value)
    restoreFragment()
  }

  const onAnchorClick = (event) => {
    if (
      event.button !== 0 ||
      event.ctrlKey ||
      event.metaKey ||
      event.shiftKey ||
      event.altKey
    )
      return
    const link = event.target.closest('a[href^="#"]')
    if (!link || !contentRef.value?.contains(link)) return
    const hash = link.getAttribute('href')
    if (!findFragmentTarget(contentRef.value, hash)) return
    event.preventDefault()
    // 使用原生片段导航，保留浏览历史和复制链接的锚点。
    if (window.location.hash !== hash) window.location.hash = hash
    scrollToFragment(hash)
  }

  onMounted(() => window.addEventListener('hashchange', restoreFragment))
  onBeforeUnmount(() => {
    active = false
    window.removeEventListener('hashchange', restoreFragment)
  })

  return { onContentRendered, onAnchorClick }
}
