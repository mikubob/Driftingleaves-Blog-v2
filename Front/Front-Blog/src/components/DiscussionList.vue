<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getPage, request } from '@/api.js'
import { discussionView } from '@/api/presentation.js'
import { session, can } from '@/session.js'
import { useResource } from '@/composables/useResource.js'
import { formatDate } from '@/format.js'
import ListPager from './ListPager.vue'
import ResourceState from './ResourceState.vue'
import SafeHtml from './SafeHtml.vue'
import DiscussionMeta from './DiscussionMeta.vue'
const props = defineProps({ kind: String, articleId: String, parentId: { type: String, default: null }, allowReply: { type: Boolean, default: true } })
const emit = defineEmits(['reply', 'edit', 'total'])
const page = ref(1)
const expanded = ref([])
const error = ref('')
const deleting = ref(false)
const listing = useResource(() => [page.value, session.user?.userId], (signal) => {
  const path = props.kind === 'comments' ? '/blog/articles/' + props.articleId + '/comments' : '/blog/messages'
  return getPage(path, { page: page.value, pageSize: 10, parentId: props.parentId }, signal)
})
const rows = computed(() => listing.data?.records.map(discussionView) || [])
watch(() => listing.data?.total, (total) => emit('total', total || '0'))
const fmtDate = formatDate
const getAvatarUrl = (item) => item.avatar
const getInitial = (name) => name?.slice(0, 1) || '叶'
const isOwn = (item) => session.user && item.author?.userId === session.user.userId
const isMine = isOwn
function startReply(item) {
  emit('reply', item)
}
function startEdit(item) {
  emit('edit', item)
}
function toggleReplies(item) {
  if (expanded.value.includes(item.id)) {
    expanded.value = expanded.value.filter((id) => id !== item.id)
  } else {
    expanded.value.push(item.id)
  }
}
async function doDelete(item) {
  if (deleting.value) {
    return
  }
  try {
    await ElMessageBox.confirm('确定删除这条内容及其全部子回复？删除后无法恢复。', '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch {
    return
  }
  deleting.value = true
  error.value = ''
  try {
    await request('/blog/me/' + props.kind + '/' + item.id, { method: 'DELETE' })
    await listing.reload()
  } catch (failure) {
    error.value = failure.message
  } finally {
    deleting.value = false
  }
}
const handleDelete = doDelete
</script>
<template>
  <div class="discussion-list" :class="kind === 'comments' ? 'comment-tree' : 'msg-list'">
    <ResourceState :resource="listing" />
    <p v-if="error" role="alert">{{ error }}</p>
    <template v-for="c in rows" :key="c.id">
      <template v-if="kind === 'comments'">
        <div class="comment-item">
          <div class="c-avatar">
            <img v-if="getAvatarUrl(c)" :src="getAvatarUrl(c)" class="c-avatar-img" loading="lazy" />
            <span v-else class="c-avatar-letter">
              {{
                      getInitial(c.nickname)
                    }}
            </span>
          </div>
          <div class="c-main">
            <div class="c-head">
              <span class="c-nick">{{ c.nickname }}</span>
              <span v-if="c.isApproved === 2" class="c-pending">未通过</span>
              <span v-if="c.isApproved === 0" class="c-pending">未审核</span>
              <DiscussionMeta v-if="c.status !== 3" :item="c" />
              <span class="c-date">{{ fmtDate(c.createTime) }}</span>
            </div>
            <div class="c-body" style="white-space: pre-wrap">{{ c.content }}</div>
            <div class="c-footer">
              <div class="c-actions">
                <a
                  v-if="allowReply && c.status === 1 && can('blog:comment:create')"
                  role="button"
                  tabindex="0"
                  @keydown.enter="startReply(c)"
                  @click="startReply(c)"
                >
                  回复
                </a>
                <a
                  v-if="c.replyCount !== '0'"
                  role="button"
                  tabindex="0"
                  @keydown.enter="toggleReplies(c)"
                  @click="toggleReplies(c)"
                >
                  {{ expanded.includes(c.id) ? '收起回复' : '查看回复' }}
                  (
                  {{ c.replyCount }}
                  )
                </a>
                <template v-if="isOwn(c) && c.status !== 3 && can('blog:comment:deleteOwn')">
                  <a
                    class="danger"
                    role="button"
                    tabindex="0"
                    :aria-disabled="deleting"
                    @keydown.enter="doDelete(c)"
                    @click="doDelete(c)"
                  >
                    删除
                  </a>
                </template>
              </div>
            </div>
            <!-- 子评论 -->
            <div v-if="expanded.includes(c.id)" class="comment-children">
              <DiscussionList
                :kind="kind"
                :article-id="articleId"
                :parent-id="c.id"
                :allow-reply="allowReply"
                @reply="emit('reply', $event)"
                @edit="emit('edit', $event)"
              />
            </div>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="msg-item-card">
          <div class="msg-avatar">
            <img v-if="getAvatarUrl(c)" :src="getAvatarUrl(c)" class="msg-avatar-img" loading="lazy" />
            <span v-else class="msg-avatar-letter">{{
                  getInitial(c.nickname)
                }}</span>
          </div>
          <div class="msg-main">
            <div class="msg-head">
              <span class="msg-nick">{{ c.nickname }}</span>
              <span v-if="c.isSecret === 1" class="msg-pending">悄悄话</span>
              <span v-if="c.isApproved === 2" class="msg-pending">未通过</span>
              <span v-if="c.isApproved === 0" class="msg-pending">未审核</span>
              <DiscussionMeta v-if="c.status !== 3" :item="c" />
              <span class="msg-date">{{ fmtDate(c.createTime) }}</span>
            </div>
            <SafeHtml class="msg-body" :html="c.contentHtml" />
            <div class="msg-footer">
              <div class="msg-actions">
                <span
                  class="act"
                  role="button"
                  tabindex="0"
                  @keydown.enter="toggleReplies(c)"
                  @click="toggleReplies(c)"
                >
                  {{ expanded.includes(c.id) ? '收起回复' : '查看回复' }}
                </span>
                <span
                  class="act"
                  v-if="allowReply && c.status === 1 && can('blog:message:create')"
                  role="button"
                  tabindex="0"
                  @keydown.enter="startReply(c)"
                  @click="startReply(c)"
                >
                  回复
                </span>
                <template v-if="isMine(c) && c.status !== 3">
                  <span
                    class="act"
                    v-if="can('blog:message:updateOwn')"
                    role="button"
                    tabindex="0"
                    @keydown.enter="startEdit(c)"
                    @click="startEdit(c)"
                  >
                    编辑
                  </span>
                  <span
                    class="act del"
                    v-if="can('blog:message:deleteOwn')"
                    role="button"
                    tabindex="0"
                    @keydown.enter="handleDelete(c)"
                    @click="handleDelete(c)"
                  >
                    删除
                  </span>
                </template>
              </div>
            </div>
            <!-- 子留言 -->
            <div v-if="expanded.includes(c.id)" class="msg-children">
              <DiscussionList
                :kind="kind"
                :article-id="articleId"
                :parent-id="c.id"
                :allow-reply="allowReply"
                @reply="emit('reply', $event)"
                @edit="emit('edit', $event)"
              />
            </div>
          </div>
        </div>
      </template>
    </template>
    <p v-if="listing.data && !rows.length" class="empty-comment">{{ parentId ? '暂时没有可见回复' : '暂时没有内容' }}</p>
    <ListPager v-if="listing.data" :data="listing.data" @change="page = $event" />
  </div>
</template>
<style scoped src="../assets/styles/discussion.scss"></style>
