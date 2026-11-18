import { request } from './api.js'

export const fileAccept = {
  image: '.png,.jpg,.jpeg,.webp',
  audio: '.mp3,.wav,.ogg,.flac',
  lyric: '.lrc,.txt,.json',
}

/** 准备上传表单，文件内容仍由服务端校验。 */
function prepareUpload(file, kind, usage) {
  const extension = file.name.split('.').pop().toLowerCase()
  const actualKind = Object.keys(fileAccept).find((key) =>
    fileAccept[key].split(',').includes(`.${extension}`))
  if (!actualKind || (kind && actualKind !== kind)) {
    throw new Error('请选择 PNG/JPEG/WebP 图片、MP3/WAV/OGG/FLAC 音频或 LRC/TXT/JSON 歌词。')
  }
  const limit = { image: 10, audio: 60, lyric: 1 }[actualKind] * 1024 * 1024
  if (!file.size || file.size > limit) {
    throw new Error('文件不能为空；图片限 10 MiB、音频限 60 MiB、歌词限 1 MiB。')
  }
  const body = new FormData()
  body.append('file', file)
  if (usage) {
    body.append('usage', usage)
  }
  return { body, extension }
}

/** 上传普通附件。 */
export async function uploadFile(file, kind, usage) {
  const { body, extension } = prepareUpload(file, kind, usage)
  const { data } = await request('/admin/files', { method: 'POST', body })
  return { file: data, extension }
}

/** 上传公开展示图片，返回可用于前台的地址。 */
export async function uploadDisplayImage(file, usage) {
  const { body } = prepareUpload(file, 'image', usage)
  const { data } = await request('/admin/files/display-images', { method: 'POST', body })
  if (typeof data !== 'string' || !/^https?:\/\//i.test(data)) {
    throw new Error('未收到可用的图片地址，请在文件管理中检查上传结果。')
  }
  return data
}
