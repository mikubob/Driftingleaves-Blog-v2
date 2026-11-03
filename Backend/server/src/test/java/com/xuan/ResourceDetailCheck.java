package com.xuan;

import com.xuan.config.WebJsonConfig;
import com.xuan.controller.admin.CategoryController;
import com.xuan.controller.admin.FriendLinkController;
import com.xuan.controller.admin.TagController;
import com.xuan.entity.BlogCategory;
import com.xuan.entity.BlogFriendLink;
import com.xuan.entity.BlogTag;
import com.xuan.entity.SysUser;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.AuthorizationMapper;
import com.xuan.mapper.CategoryMapper;
import com.xuan.mapper.FriendLinkMapper;
import com.xuan.mapper.TagMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.result.Result;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.impl.CategoryServiceImpl;
import com.xuan.service.impl.FriendLinkServiceImpl;
import com.xuan.service.impl.TagServiceImpl;
import com.xuan.service.impl.UserServiceImpl;
import com.xuan.session.SessionManager;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 验证分类、标签和友链的真实详情业务，数据库和会话使用隔离替身。 */
public final class ResourceDetailCheck {

    private static final long ID = 9007199254740993L;

    private ResourceDetailCheck() {
    }

    /** 显式执行详情接口自检，不启动应用或连接外部服务。 */
    public static void main(String[] args) {
        var builder = JsonMapper.builder();
        new WebJsonConfig().webJsonCustomizer().customize(builder);
        JsonMapper json = builder.build();
        var allowed = new AtomicReference<>(List.of("admin:access", "blog:category:query", "blog:tag:query", "blog:link:query"));
        AuthorizationMapper grants = (AuthorizationMapper) Proxy.newProxyInstance(
                AuthorizationMapper.class.getClassLoader(), new Class<?>[]{AuthorizationMapper.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "roleCodes" -> List.of("viewer");
                    case "grants" -> allowed.get().stream().map(code -> {
                        var grant = new AuthorizationMapper.Grant();
                        grant.setPermissionCode(code);
                        grant.setDataScope("SELF");
                        return grant;
                    }).toList();
                    default -> throw new AssertionError("不应调用：" + method.getName());
                });
        var authorization = new AuthorizationProvider(grants);
        SessionManager sessions = new SessionManager(Clock.systemUTC()) {
            /** 固定当前操作者。 */
            @Override
            public long currentUserId() {
                return 1L;
            }

            /** 返回自检使用的会话标识。 */
            @Override
            public String currentToken() {
                return "fixture-token";
            }

            /** 检查业务层复核的身份与原会话一致。 */
            @Override
            public void requireActive(String token, long userId) {
                require("fixture-token".equals(token) && userId == 1L, "会话复核参数错误");
            }
        };
        SysUser actor = new SysUser();
        actor.setUserId(1L);
        actor.setStatus(1);
        UserMapper users = readMapper(UserMapper.class, 1L, actor);
        var identities = new UserServiceImpl(users, null, grants, authorization, null, sessions,
                null, json, Clock.systemUTC(), null, null, null);
        var time = LocalDateTime.of(2026, 11, 1, 0, 0);
        BlogCategory category = new BlogCategory();
        category.setCategoryId(ID);
        category.setCategoryName("隐藏分类");
        category.setSlug("category-fixture");
        category.setSortOrder(0);
        category.setStatus(0);
        category.setCreateTime(time);
        BlogTag tag = new BlogTag();
        tag.setTagId(ID);
        tag.setTagName("标签自检");
        tag.setSlug("tag-fixture");
        tag.setCreateTime(time);
        BlogFriendLink link = new BlogFriendLink();
        link.setLinkId(ID);
        link.setLinkName("待审友链");
        link.setUrl("https://example.test");
        link.setSortOrder(0);
        link.setStatus(0);
        link.setCreateTime(time);
        var categories = new CategoryController(new CategoryServiceImpl(
                readMapper(CategoryMapper.class, ID, category), users, identities, sessions, authorization, json));
        var tags = new TagController(new TagServiceImpl(
                readMapper(TagMapper.class, ID, tag), users, identities, sessions, authorization, json));
        var links = new FriendLinkController(new FriendLinkServiceImpl(
                readMapper(FriendLinkMapper.class, ID, link), users, identities, sessions, authorization, json));
        String id = Long.toString(ID);
        String categoryRevision = checkResponse(categories.detail(id), json, "categoryId");
        checkResponse(tags.detail(id), json, "tagId");
        String linkRevision = checkResponse(links.detail(id), json, "linkId");
        require(categories.detail(id).getBody().data().getStatus() == 0, "隐藏分类也能查询");
        require(links.detail(id).getBody().data().getStatus() == 0, "待审友链也能查询");
        category.setUpdateTime(time.plusDays(1));
        require(categoryRevision.equals(categories.detail(id).getHeaders().getFirst("X-Revision")), "审计时间不改变分类版本");
        category.setCategoryName("更新后的分类");
        require(!categoryRevision.equals(categories.detail(id).getHeaders().getFirst("X-Revision")), "业务字段改变必须更新版本");
        link.setStatus(2);
        require(links.detail(id).getBody().data().getStatus() == 2, "下架友链也能查询");
        require(!linkRevision.equals(links.detail(id).getHeaders().getFirst("X-Revision")), "审核状态参与版本");

        Map<String, Consumer<String>> readers = Map.of("blog:category:query", categories::detail,
                "blog:tag:query", tags::detail, "blog:link:query", links::detail);
        for (var entry : readers.entrySet()) {
            Consumer<String> read = entry.getValue();
            expect(404, "NOT_FOUND", () -> read.accept("99"));
            for (String invalid : List.of("0", "01", "-1", "abc", " 1", "9223372036854775808")) {
                expect(400, "VALIDATION_ERROR", () -> read.accept(invalid));
            }
            allowed.set(List.of("admin:access"));
            expect(403, "FORBIDDEN", () -> read.accept(id));
            expect(403, "FORBIDDEN", () -> read.accept("99"));
            allowed.set(List.of(entry.getKey()));
            expect(403, "FORBIDDEN", () -> read.accept(id));
            allowed.set(List.of("admin:access", "blog:category:query", "blog:tag:query", "blog:link:query"));
            actor.setStatus(0);
            expect(403, "ACCOUNT_DISABLED", () -> read.accept(id));
            actor.setStatus(1);
        }
        System.out.println("ResourceDetailCheck passed: three detail APIs, status, validation, permissions, serialization and revision");
    }

    /** 为只读数据库边界提供一条虚构记录，意外写入会立即失败。 */
    private static <T> T readMapper(Class<T> type, long id, Object record) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, values) -> {
                    if (method.getName().equals("selectById")) {
                        return Long.valueOf(id).equals(values[0]) ? record : null;
                    }
                    throw new AssertionError("详情不应调用：" + method.getName());
                }));
    }

    /** 检查响应中的字符串ID、UTC时间和带引号版本。 */
    private static String checkResponse(ResponseEntity<? extends Result<?>> response, JsonMapper json, String idField) {
        require(response.getStatusCode().value() == 200, "详情应返回200");
        var body = json.readTree(json.writeValueAsString(response.getBody())).get("data");
        require(body.get(idField).isString() && body.get(idField).asString().equals(Long.toString(ID)), "大ID不能丢精度");
        require(body.get("createTime").asString().equals("2026-11-01T00:00:00.000Z"), "时间应为UTC毫秒格式");
        String revision = response.getHeaders().getFirst("X-Revision");
        require(revision != null && revision.matches("\"[0-9a-f]{64}\""), "版本必须带引号");
        return revision;
    }

    /** 检查实际抛出的业务状态和错误码。 */
    private static void expect(int status, String code, Runnable action) {
        try {
            action.run();
        } catch (BusinessException failure) {
            require(failure.getStatus() == status && failure.getCode().equals(code), "错误响应不符合预期");
            return;
        }
        throw new AssertionError("应拒绝请求：" + code);
    }

    /** 条件不满足时终止自检。 */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
