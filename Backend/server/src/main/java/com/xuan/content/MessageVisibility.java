package com.xuan.content;

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
 * 沿整条父链检查审核状态和最早私密祖先，不凭管理员Token放宽前台。
 */
public final class MessageVisibility {

    private MessageVisibility() {
    }

    /** 构造当前读取需要的父链索引；没有原文、邮箱和IP。 */
    public static Map<Long, MessageNode> index(List<MessageNode> nodes) {
        Map<Long, MessageNode> result = new HashMap<>();
        nodes.forEach(node -> result.put(node.getMessageId(), node));
        return result;
    }

    /** 根到目标逐级可见，缺祖先或环都拒绝，自己的隐藏节点仅对本人可见。 */
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
        if (path.isEmpty()) {
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
