import { computed, nextTick, ref } from 'vue'

/** 为迁移的评论、留言表单保留计数和表情插入；V2 身份来自登录会话。 */
export function useDiscussionForm(textarea) {
  const form = ref({ content: '', isMarkdown: false, isSecret: false, isNotice: false })
  const errors = ref({ content: '' })
  const contentLength = computed(() => [...form.value.content].length)

  function validateField() {
    const length = [...form.value.content.trim()].length
    errors.value.content = length >= 1 && length <= 2000 ? '' : '正文需为 1 到 2000 个字符'
    return !errors.value.content
  }

  async function insertEmoji(char) {
    const element = textarea.value
    const start = element?.selectionStart ?? form.value.content.length
    const end = element?.selectionEnd ?? start
    form.value.content = form.value.content.slice(0, start) + char + form.value.content.slice(end)
    await nextTick()
    element?.focus()
    element?.setSelectionRange(start + char.length, start + char.length)
  }

  function resetForm() {
    form.value = { content: '', isMarkdown: false, isSecret: false, isNotice: false }
    errors.value.content = ''
  }

  return { form, errors, contentLength, validateField, validateForm: validateField, insertEmoji, resetForm }
}
