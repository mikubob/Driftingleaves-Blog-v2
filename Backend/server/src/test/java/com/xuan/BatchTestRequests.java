package com.xuan;

import tools.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 原有业务用例仍以资源位置描述动作，统一在测试请求边界生成新协议，返回真实批量响应。 */
final class BatchTestRequests {
    private static final Pattern RESOURCE = Pattern.compile("(/admin/(?:articles|categories|tags|comments|messages|links|files|home/social-links|cv/experiences|cv/skills|music|rss-subscriptions|visitors|page-views|operation-logs|roles|permissions|menus))/([^/?]+)(?:/(status|review|restore|permanent|block|unblock))?(\\?.*)?");
    private static final Set<String> NO_REVISION = Set.of("comments", "messages", "files", "page-views", "operation-logs");

    record Prepared(String method, String path, String revision, Object body) {
    }

    /** 只转换本次合并的管理动作，不改查询、编辑、本人接口或已经使用新协议的用例。 */
    static Prepared prepare(String method, String path, String revision, Object body, JsonMapper json) {
        var match = RESOURCE.matcher(path);
        if (!match.matches() || match.group(2).startsWith("batch-")) {
            return new Prepared(method, path, revision, body);
        }
        String resource = match.group(1);
        String id = match.group(2);
        String operation = match.group(3);
        String action = null;
        String nextMethod = "POST";
        if (method.equals("DELETE") && operation == null) {
            action = resource.endsWith("/articles") ? "batch-recycle" : "batch-delete";
        } else if (resource.endsWith("/articles") && method.equals("DELETE") && "permanent".equals(operation)) {
            action = "batch-permanent-delete";
        } else if (resource.endsWith("/articles") && method.equals("POST") && "restore".equals(operation)) {
            action = "batch-restore";
        } else if (method.equals("PUT") && operation != null && !operation.equals("restore") && !operation.equals("permanent")) {
            action = "batch-" + operation;
            nextMethod = "PUT";
        }
        if (action == null) {
            return new Prepared(method, path, revision, body);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean idsOnly = action.equals("batch-delete") && NO_REVISION.contains(resource.substring(resource.lastIndexOf('/') + 1));
        if (idsOnly) {
            payload.put("ids", List.of(id));
        } else {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("revision", revision);
            payload.put("items", List.of(item));
        }
        boolean noOriginalBody = method.equals("DELETE") || "restore".equals(operation) || "unblock".equals(operation);
        if (body != null && noOriginalBody) {
            payload.put("unexpectedBody", body);
        }
        if (body instanceof Map<?, ?> fields) {
            fields.forEach((key, value) -> payload.put(key.toString(), value));
        }
        String query = match.group(4) == null ? "" : match.group(4);
        return new Prepared(nextMethod, resource + "/" + action + query, null, payload);
    }
}
