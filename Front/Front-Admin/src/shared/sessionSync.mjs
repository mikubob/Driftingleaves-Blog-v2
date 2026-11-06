/** 通知同源标签页重新核验会话，消息只表示会话发生变化。 */
export function syncSession(site, recheck) {
  const channel = typeof globalThis.window?.BroadcastChannel === 'function'
    ? new window.BroadcastChannel('dl-' + site + '-session-changed') : null
  if (channel) {
    channel.onmessage = recheck
  }
  return () => channel?.postMessage(null)
}

/** 重载页面以丢弃旧账号的界面和待提交内容。 */
export function reloadSessionPage() {
  if (globalThis.window && window.location.pathname !== '/oauth/complete') {
    window.location.reload()
  }
}
