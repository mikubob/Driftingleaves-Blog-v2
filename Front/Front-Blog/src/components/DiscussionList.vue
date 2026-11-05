<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { getPage, request } from '@/api.js'
import { discussionView } from '@/shared/presentation.js'
import { session, can } from '@/session.js'
import { useResource } from '@/composables/useResource.js'
import { formatDateTime } from '@/format.js'
import ListPager from './ListPager.vue'
import ResourceState from './ResourceState.vue'
import SafeHtml from './SafeHtml.vue'
import DiscussionMeta from './DiscussionMeta.vue'

const props = defineProps({
  kind: String,
  articleId: String,
  articleAuthorId: String,
  parentId: { type: String, default: null },
  allowReply: { type: Boolean, default: true },
})
const emit = defineEmits(['reply', 'total', 'changed'])
const page = ref(1)
const expanded = ref([])
const replyData = ref(null)
const showAll = ref(false)
const activeContext = ref(null)
const error = ref('')
const deleting = ref(false)
const isComment = computed(() => props.kind === 'comments')
const itemPrefix = computed(() => isComment.value ? 'c' : 'msg')
const permissionPrefix = computed(() => isComment.value ? 'blog:comment:' : 'blog:message:')

// 切换读者或讨论时清空已追加的回复，不能残留上一位读者的私密或待审内容。
watch(() => [props.kind, props.articleId, props.parentId, session.user?.userId], () => {
  page.value = 1
  expanded.value = []
  replyData.value = null
  showAll.value = false
  activeContext.value = null
}, { flush: 'sync' })

const listing = useResource(
  () => [page.value, props.kind, props.articleId, props.parentId, session.user?.userId],
  async (signal) => {
    const path = isComment.value ? '/blog/articles/' + props.articleId + '/comments' : '/blog/messages'
    const result = await getPage(path, { page: page.value, pageSize: 10, parentId: props.parentId }, signal)
    if (props.parentId && !signal.aborted) {
      const records = result.page === 1 ? result.records : [...replyData.value.records, ...result.records]
      replyData.value = { ...result, records }
    }
    return result
  },
)
const rows = computed(() => {
  const data = props.parentId ? replyData.value : listing.data
  return data?.records.map(discussionView) || []
})
const visibleRows = computed(() => props.parentId && !showAll.value ? rows.value.slice(0, 3) : rows.value)
const remaining = computed(() => BigInt(replyData.value?.total || '0') - BigInt(visibleRows.value.length))
watch(() => listing.data?.total, (total) => emit('total', total || '0'))
const getInitial = (name) => name?.slice(0, 1) || '叶'
const isOwn = (item) => session.user && item.author?.userId === session.user.userId

function toggleReplies(item) {
  if (expanded.value.includes(item.id)) {
    expanded.value = expanded.value.filter((id) => id !== item.id)
  } else {
    expanded.value.push(item.id)
  }
}

function showMore() {
  if (!showAll.value) {
    showAll.value = true
    return
  }
  page.value = replyData.value.page + 1
}

async function doDelete(item) {
  if (deleting.value) {
    return
  }
  try {
    await ElMessageBox.confirm('确定删除这条内容及其全部子回复？删除后无法恢复。', '确认删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch {
    return
  }
  deleting.value = true
  error.value = ''
  try {
    await request('/blog/me/' + props.kind + '/' + item.id, { method: 'DELETE' })
    if (props.parentId) {
      // 删除一条回复也会删除它的后代，整组重新查询，避免残留已删除的回复和计数。
      emit('changed')
    } else {
      await listing.reload()
    }
  } catch (failure) {
    error.value = failure.message
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="discussion-list" :class="[isComment ? 'comment-tree' : 'msg-list', { 'discussion-replies': parentId }]">
    <p v-if="error" role="alert">{{ error }}</p>
    <div
      v-for="c in visibleRows"
      :key="c.id"
      :class="[isComment ? 'comment-item' : 'msg-item-card', { 'discussion-reply': parentId }]"
    >
      <div :class="itemPrefix + '-avatar'">
        <img v-if="c.avatar" :src="c.avatar" :class="itemPrefix + '-avatar-img'" alt="" loading="lazy" />
        <span v-else :class="itemPrefix + '-avatar-letter'">{{ getInitial(c.nickname) }}</span>
      </div>
      <div :class="itemPrefix + '-main'">
        <div :class="itemPrefix + '-head'">
          <span :class="itemPrefix + '-nick'">{{ c.nickname }}</span>
          <span v-if="isComment && articleAuthorId && c.author?.userId === articleAuthorId" class="c-badge badge-article-author">本文作者</span>
          <span v-if="c.siteAuthor" class="c-badge badge-site-author">本站作者</span>
          <span v-if="c.siteAdmin" class="c-badge badge-site-admin">管理员</span>
          <span v-if="parentId && c.replyTarget" class="c-reply-to">
            <button
              type="button"
              class="reply-target"
              :aria-expanded="activeContext === c.id"
              @click="activeContext = activeContext === c.id ? null : c.id"
            >
              <i class="iconfont icon-zhuanfa reply-icon" aria-hidden="true" />
              {{ c.replyTarget.nickname || '已删除的用户' }}
            </button>
          </span>
          <span v-if="!isComment && c.isSecret === 1" :class="itemPrefix + '-pending'">悄悄话</span>
          <span v-if="c.isApproved === 2" :class="itemPrefix + '-pending'">未通过</span>
          <span v-if="c.isApproved === 0" :class="itemPrefix + '-pending'">未审核</span>
          <DiscussionMeta :item="c" />
          <time :class="itemPrefix + '-date'" :datetime="c.createTime">{{ formatDateTime(c.createTime) }}</time>
        </div>
        <p v-if="activeContext === c.id && c.replyTarget" class="reply-quote">{{ c.replyTarget.content }}</p>
        <div v-if="isComment" class="c-body" style="white-space: pre-wrap">{{ c.content }}</div>
        <SafeHtml v-else class="msg-body" :html="c.contentHtml" />
        <div class="c-footer">
          <div class="c-actions">
            <button
              v-if="allowReply && c.replyable && can(permissionPrefix + 'create')"
              type="button"
              @click="emit('reply', c)"
            >回复</button>
            <button
              v-if="!parentId && c.replyCount !== '0'"
              type="button"
              :aria-expanded="expanded.includes(c.id)"
              @click="toggleReplies(c)"
            >{{ expanded.includes(c.id) ? '收起回复' : '查看回复' }}（{{ c.replyCount }}）</button>
            <button
              v-if="isOwn(c) && can(permissionPrefix + 'deleteOwn')"
              class="danger"
              type="button"
              :disabled="deleting"
              @click="doDelete(c)"
            >删除</button>
          </div>
        </div>
        <!-- 所有深度的回复只渲染这一层列表，不再递归嵌套。 -->
        <div v-if="!parentId && expanded.includes(c.id)" :class="isComment ? 'comment-children' : 'msg-children'">
          <DiscussionList
            :kind="kind"
            :article-id="articleId"
            :article-author-id="articleAuthorId"
            :parent-id="c.id"
            :allow-reply="allowReply"
            @reply="emit('reply', $event)"
            @changed="listing.reload()"
          />
        </div>
      </div>
    </div>
    <ResourceState :resource="listing" />
    <p v-if="listing.data && !rows.length" class="empty-comment">{{ parentId ? '暂时没有可见回复' : '暂时没有内容' }}</p>
    <button
      v-if="parentId && remaining > 0 && !listing.error"
      class="replies-more"
      type="button"
      :disabled="listing.loading"
      @click="showMore"
    >{{ showAll ? '加载更多回复' : '展开剩余 ' + remaining + ' 条回复' }}</button>
    <ListPager v-if="!parentId && listing.data" :data="listing.data" @change="page = $event" />
  </div>
</template>
<style scoped src="../assets/styles/discussion.scss"></style>
