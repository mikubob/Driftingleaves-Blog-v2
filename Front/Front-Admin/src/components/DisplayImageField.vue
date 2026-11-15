<script setup>
import { ElInput, ElMessage } from 'element-plus'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import { uploadDisplayImage } from '../fileUpload.js'
import { can } from '../session.js'
import FileUploadButton from './FileUploadButton.vue'
import ImagePreview from './ImagePreview.vue'

/** 编辑展示图片地址，上传成功后自动填写并预览。 */
const props = defineProps({
  label: { type: String, required: true },
  usage: { type: String, required: true },
  type: { type: String, default: 'url' },
  disabled: Boolean,
})
const url = defineModel({ type: String, required: true })
const uploading = defineModel('uploading', { type: Boolean, required: true })

async function upload(file) {
  if (props.disabled || uploading.value) {
    return
  }
  uploading.value = true
  try {
    url.value = await uploadDisplayImage(file, props.usage)
  } catch (failure) {
    ElMessage.error(failure.message)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="file-url-field" :aria-busy="uploading">
    <div class="file-url-row">
      <label>
        {{ label }}
        <el-input v-model="url" :type="type" maxlength="2048" :disabled="disabled || uploading" />
      </label>
      <FileUploadButton
        v-if="can('blog:file:upload')"
        kind="image"
        :disabled="disabled"
        :loading="uploading"
        @select="upload"
      >上传公开图片</FileUploadButton>
    </div>
    <ImagePreview :src="url" :alt="label + '预览'" />
  </div>
</template>
