import { computed, nextTick, ref } from 'vue'

/** 管理评论和留言表单的字数统计与表情插入。 */
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
