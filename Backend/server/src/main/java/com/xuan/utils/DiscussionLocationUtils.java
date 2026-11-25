package com.xuan.utils;

import com.xuan.dto.DiscussionConversationQueryDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 查找讨论主记录并计算回复的分页位置。
 */
public final class DiscussionLocationUtils {

    private static final int PAGE_SIZE = 10;

    private DiscussionLocationUtils() {
    }

    /**
     * 查找顶层节点，父链断开或有环时无法定位。
     */
    public static <T> Long rootId(long id, Map<Long, T> nodes, Function<T, Long> parentId) {
        Set<Long> seen = new HashSet<>();
        Long currentId = id;
        while (seen.add(currentId)) {
            T node = nodes.get(currentId);
            if (node == null) {
                return null;
            }
            Long parent = parentId.apply(node);
            if (parent == null) {
                return currentId;
            }
            currentId = parent;
        }
        return null;
    }

    /**
     * 按节点原有顺序分页，未指定页码时定位当前记录，父链断开或有环时返回空。
     */
    public static <T> ConversationPage conversationPage(long id, List<T> nodes, Function<T, Long> nodeId,
                                                        Function<T, Long> parentId,
                                                        DiscussionConversationQueryDTO query) {
        Map<Long, T> byId = new HashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();
        for (T node : nodes) {
            Long currentId = nodeId.apply(node);
            byId.put(currentId, node);
            children.computeIfAbsent(parentId.apply(node), key -> new ArrayList<>()).add(currentId);
        }

        Long root = rootId(id, byId, parentId);
        if (root == null) {
            return null;
        }

        Set<Long> selected = new HashSet<>();
        List<Long> descendants = new ArrayList<>(children.getOrDefault(root, List.of()));
        for (int index = 0; index < descendants.size(); index++) {
            long current = descendants.get(index);
            if (selected.add(current)) {
                descendants.addAll(children.getOrDefault(current, List.of()));
            }
        }

        List<Long> ordered = nodes.stream().map(nodeId).filter(selected::contains).toList();
        int pageSize = query.pageSize();
        int page = query.page() == null ? Math.max(0, ordered.indexOf(id)) / pageSize + 1 : query.page();
        int total = ordered.size();
        int from = Math.min(total, (page - 1) * pageSize);
        List<Long> replyIds = ordered.subList(from, Math.min(total, from + pageSize));
        long totalPages = (total + (long) pageSize - 1) / pageSize;
        return new ConversationPage(root, page, Integer.toString(total), Long.toString(totalPages), replyIds);
    }

    /**
     * 保存主记录id、分页统计和当前页的回复id。
     */
    public record ConversationPage(long rootId, int page, String total, String totalPages, List<Long> replyIds) {
    }

    /**
     * 按前台根节点的顺序计算固定每页10条的页码。
     */
    public static Map<Long, Integer> rootPages(List<Long> orderedRoots) {
        Map<Long, Integer> pages = new HashMap<>();
        int position = 0;
        for (long id : orderedRoots) {
            pages.put(id, position / PAGE_SIZE + 1);
            position++;
        }
        return pages;
    }
}
