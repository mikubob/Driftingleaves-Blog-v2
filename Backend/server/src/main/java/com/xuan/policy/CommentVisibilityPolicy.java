package com.xuan.policy;

import com.xuan.mapper.ArticleMapper.CommentNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 评论可见性检查，文章计数和评论查询都检查全部上级评论。
 */
public final class CommentVisibilityPolicy {

    private CommentVisibilityPolicy() {
    }

    /** 判断评论是否可见，逐级检查并缓存结果；仅审核通过的评论可见，未通过的上级隐藏全部下级回复，不受 SQL 递归深度限制。 */
    public static Set<Long> visible(List<CommentNode> nodes) {
        return visible(nodes, null);
    }

    /** 判断当前读者可见的评论，未通过的节点仅作者本人可见，全部上级仍须逐级检查。 */
    public static Set<Long> visible(List<CommentNode> nodes, Long viewer) {
        Map<Long, CommentNode> byId = new HashMap<>();
        nodes.forEach(node -> byId.put(node.getCommentId(), node));
        Map<Long, Boolean> visible = new HashMap<>();
        for (CommentNode start : nodes) {
            List<Long> path = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            CommentNode node = start;
            boolean allowed = true;
            while (node != null) {
                if (visible.containsKey(node.getCommentId())) {
                    allowed = visible.get(node.getCommentId());
                    break;
                }
                if (!seen.add(node.getCommentId())
                        || (node.getStatus() != 1 && (viewer == null || !Objects.equals(viewer, node.getUserId())))) {
                    allowed = false;
                    break;
                }
                path.add(node.getCommentId());
                if (node.getParentId() != null && !byId.containsKey(node.getParentId())) {
                    allowed = false;
                    break;
                }
                node = node.getParentId() == null ? null : byId.get(node.getParentId());
            }
            for (long descendant : path) {
                visible.put(descendant, allowed);
            }
        }
        Set<Long> result = new HashSet<>();
        visible.forEach((id, allowed) -> {
            if (allowed) {
                result.add(id);
            }
        });
        return result;
    }
}
