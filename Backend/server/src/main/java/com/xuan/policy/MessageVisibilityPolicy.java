package com.xuan.policy;

import cn.hutool.core.collection.CollUtil;
import com.xuan.model.MessageNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 留言可见性检查，检查全部上级留言的审核状态和最早的私密限制，管理员 Token 不能放宽前台权限。
 */
public final class MessageVisibilityPolicy {

    private MessageVisibilityPolicy() {
    }

    /** 按id整理上级留言信息，不包含原文、邮箱或 IP。 */
    public static Map<Long, MessageNode> index(List<MessageNode> nodes) {
        Map<Long, MessageNode> result = new HashMap<>();
        nodes.forEach(node -> result.put(node.getMessageId(), node));
        return result;
    }

    /** 判断留言是否可见，从根留言逐级检查，缺少上级或出现循环时拒绝，自己隐藏的留言仅本人可见。 */
    public static boolean visible(long id, Map<Long, MessageNode> nodes, Long viewer) {
        List<MessageNode> path = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        MessageNode row = nodes.get(id);
        while (row != null) {
            if (!seen.add(row.getMessageId())) {
                return false;
            }
            path.add(row);
            if (row.getParentId() == null) {
                break;
            }
            row = nodes.get(row.getParentId());
            if (row == null) {
                return false;
            }
        }
        if (CollUtil.isEmpty(path)) {
            return false;
        }
        Collections.reverse(path);
        Long recipient = null;
        for (MessageNode node : path) {
            boolean own = Objects.equals(viewer, node.getUserId());
            if (node.getStatus() != 1 && node.getStatus() != 3 && !own) {
                return false;
            }
            if (recipient == null && node.getIsSecret() == 1) {
                recipient = node.getUserId();
            }
            if (recipient != null && !Objects.equals(viewer, recipient) && !own) {
                return false;
            }
        }
        return true;
    }
}
