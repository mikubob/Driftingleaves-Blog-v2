package com.xuan.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 根据讨论树和前台列表顺序计算跳转位置。 */
public final class DiscussionLocationUtils {

    private static final int PAGE_SIZE = 10;

    private DiscussionLocationUtils() {
    }

    /** 查找顶层节点，父链断开或有环时无法定位。 */
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

    /** 按前台根节点的顺序计算固定每页10条的页码。 */
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
