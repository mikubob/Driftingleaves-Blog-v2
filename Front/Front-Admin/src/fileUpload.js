import { request } from './api.js'

export const fileAccept = {
  image: '.png,.jpg,.jpeg',
  audio: '.mp3,.wav,.ogg,.flac',
  lyric: '.lrc,.txt,.json',
}

/** 文件页和音乐表单共用上传入口；扩展名只用于交互提示，内容仍由服务端识别。 */
export async function uploadFile(file, kind) {
  const extension = file.name.split('.').pop().toLowerCase()
  const actualKind = Object.keys(fileAccept).find((key) =>
    fileAccept[key].split(',').includes(`.${extension}`))
  if (!actualKind || (kind && actualKind !== kind)) {
    throw new Error('请选择 PNG/JPEG 图片、MP3/WAV/OGG/FLAC 音频或 LRC/TXT/JSON 歌词。')
  }
  const limit = { image: 10, audio: 60, lyric: 1 }[actualKind] * 1024 * 1024
  if (!file.size || file.size > limit) {
    throw new Error('文件不能为空；图片限 10 MiB、音频限 60 MiB、歌词限 1 MiB。')
  }
  const body = new FormData()
  body.append('file', file)
  const { data } = await request('/admin/files', { method: 'POST', body })
  return { file: data, extension }
}
