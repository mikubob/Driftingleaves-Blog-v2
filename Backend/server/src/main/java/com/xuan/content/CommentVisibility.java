package com.xuan.content;

import com.xuan.mapper.ArticleMapper.CommentNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章计数和评论入口共用完整祖先链检查，不能只看直接父节点。
 */
public final class CommentVisibility {

    private CommentVisibility() {
    }

    /** 逐层回溯并记住结果，0/2 隐藏整支，3 保留可展开位置；不受 SQL 递归深度限制。 */
    public static Set<Long> visible(List<CommentNode> nodes) {
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
                if (!seen.add(node.getCommentId()) || (node.getStatus() != 1 && node.getStatus() != 3)) {
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
