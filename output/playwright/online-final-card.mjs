async (page) => {
  await page.reload()
  const card = page.locator('.stats-card')
  const status = card.getByRole('status')
  await card.locator('.online-stats .sg-val').first().waitFor()
  await page.context().setOffline(true)
  await status.filter({ hasText: '在线人数连接中断' }).waitFor()
  if (await card.locator('.online-stats .sg-val').count()) throw new Error('中断时仍展示实时人数')
  await page.context().setOffline(false)
  await card.locator('.online-stats .sg-val').first().waitFor()
  await card.screenshot({ path: 'output/playwright/online-statistics-card.png' })
  return { onlineValues: await card.locator('.online-stats .sg-val').allTextContents(), interruptedState: true }
}
