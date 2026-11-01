// 挂载项目实际使用的表单组件，检查内部输入框与外层焦点边框是否重复。
async function formFocusSmoke(page) {
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.route('**/form-focus-fixture', (route) => route.fulfill({
    contentType: 'text/html',
    body: '<html lang="zh-CN"><meta charset="utf-8"><body style="padding:24px"><main id="app"></main></body></html>',
  }))
  await page.goto('http://127.0.0.1:5176/form-focus-fixture')
  await page.evaluate(async () => {
    const { createApp, h } = await import('/node_modules/.vite/deps/vue.js')
    const {
      default: ElementPlus, ElSelect, ElOption, ElInput, ElInputNumber,
      ElDatePicker, ElButton,
    } = await import('/node_modules/.vite/deps/element-plus.js')
    const { default: ListPager } = await import('/src/components/ListPager.vue')
    await import('/node_modules/element-plus/dist/index.css')
    await import('/src/assets/styles/main.css')
    await import('/src/style.css')
    const controls = [
      ['普通选择框', ElSelect, {}],
      ['分类搜索框', ElSelect, { filterable: true }],
      ['标签多选框', ElSelect, { filterable: true, multiple: true, modelValue: [] }],
      ['文本输入框', ElInput, {}],
      ['密码输入框', ElInput, { type: 'password', showPassword: true }],
      ['数字输入框', ElInputNumber, { modelValue: 1 }],
      ['多行输入框', ElInput, { type: 'textarea' }],
      ['日期选择框', ElDatePicker, { type: 'date' }],
      ['日期时间框', ElDatePicker, { type: 'datetime' }],
      ['日期区间框', ElDatePicker, { type: 'daterange' }],
      ['分页控件', ListPager, { page: 1, pageSize: 10, total: '120', totalPages: '12' }],
    ]
    createApp({
      render: () => h('div', { style: 'display:grid;gap:20px;max-width:600px' }, [
        ...controls.map(([label, component, props]) => h('section', { 'data-control': label }, [
          h('p', { style: 'margin-bottom:8px' }, label),
          h(component, { placeholder: label, ...props }, component === ElSelect
            ? () => [h(ElOption, { label: '测试选项', value: 'test' })]
            : undefined),
        ])),
        h(ElButton, { id: 'keyboard-button' }, () => '键盘操作'),
        h('input', { id: 'native-input', placeholder: '原生输入框' }),
      ]),
    }).use(ElementPlus).mount('#app')
  })

  const checked = []
  await page.keyboard.press('Tab')
  for (const field of await page.locator('section input, section textarea').all()) {
    if (!await field.isVisible()) {
      continue
    }
    await field.focus()
    const result = await field.evaluate((input) => {
      const style = getComputedStyle(input)
      return {
        control: input.closest('section').dataset.control,
        outline: style.outlineStyle,
        width: style.outlineWidth,
      }
    })
    if (result.outline !== 'none' && result.width !== '0px') {
      throw new Error(`${result.control}内部出现重复焦点框：${JSON.stringify(result)}`)
    }
    checked.push(result.control)
    await page.keyboard.press('Escape')
  }
  if (new Set(checked).size !== 11) {
    throw new Error(`未完整检查全部表单类型：${JSON.stringify(checked)}`)
  }

  const search = page.locator('[data-control="分类搜索框"] input')
  await search.click()
  await search.fill('测试')
  await page.getByRole('option', { name: '测试选项', exact: true }).click()
  await search.focus()
  if (!await page.locator('[data-control="分类搜索框"] .el-select__wrapper.is-focused').count()) {
    throw new Error('选择框失去外层焦点提示')
  }
  await search.fill('不存在的分类')
  await page.locator('.el-select-dropdown__empty:visible').waitFor()
  if (!await search.evaluate((input) => getComputedStyle(input).outlineStyle === 'none')) {
    throw new Error('无匹配结果时搜索框仍有重复焦点框')
  }
  await page.screenshot({ path: 'output/playwright/form-focus.png', fullPage: true })
  await page.keyboard.press('Escape')
  for (const selector of ['#keyboard-button', '#native-input']) {
    await page.keyboard.press('Tab')
    await page.locator(selector).focus()
    if (!await page.locator(selector).evaluate((element) => {
      const style = getComputedStyle(element)
      return style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) > 0
    })) {
      throw new Error(`${selector}失去键盘焦点提示`)
    }
  }
  return { controls: [...new Set(checked)], internalInputs: checked.length, keyboardFocusPreserved: true }
}
