package com.xuan.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xuan.annotation.OperationLog;
import com.xuan.dto.ConfigWriteDTO;
import com.xuan.entity.BlogSiteConfig;
import com.xuan.exception.BusinessException;
import com.xuan.mapper.SiteConfigMapper;
import com.xuan.mapper.UserMapper;
import com.xuan.security.AuthorizationProvider;
import com.xuan.service.ISiteConfigService;
import com.xuan.service.IUserService;
import com.xuan.session.SessionManager;
import com.xuan.utils.TokenUtils;
import com.xuan.vo.ConfigProfileVO;
import com.xuan.vo.ConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 配置行和评论创建共用锁，不加缓存，保存后下一个评论直接读取新策略。 */
@Service
@RequiredArgsConstructor
public class SiteConfigServiceImpl extends ServiceImpl<SiteConfigMapper, BlogSiteConfig> implements ISiteConfigService {
    private static final Set<String> KEYS = Set.of("site", "comment", "message");
    private final SiteConfigMapper configs;
    private final UserMapper users;
    private final IUserService identities;
    private final SessionManager sessions;
    private final AuthorizationProvider authorization;
    private final JsonMapper json;

    /** SQL 同时限制 site 与公开标记，不会因为错误公开 comment 而暴露审核策略。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ConfigVO> publicConfigs() {
        return configs.configs(true).stream().map(this::view).toList();
    }

    /** 后台只能列出三个白名单键，SELF 对这个全站资源同样有效。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ConfigVO> listConfigs() {
        requireAdmin("query");
        return configs.configs(false).stream().map(this::view).toList();
    }

    /** 先确认权限再确认键，版本和对象在同一快照里读取。 */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConfigProfileVO detail(String key) {
        requireAdmin("query");
        requireKey(key);
        return profile(requireConfig(configs.selectById(key)));
    }

    /** 先锁操作者、再锁配置；评论新增也要此行锁，开关变更不会越过正在提交的评论。 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @OperationLog(type = "update", target = "blog:config", permission = "blog:config:update")
    public ConfigProfileVO update(String key, ConfigWriteDTO request, String revision) {
        users.lockById(sessions.currentUserId());
        requireAdmin("update");
        requireKey(key);
        BlogSiteConfig row = configs.lockByKey(key);
        // 等待期间权限和原 Token 可能失效，拿到配置锁后重新检查。
        requireAdmin("update");
        row = requireConfig(row);
        checkRevision(row, revision);
        Map<String, Object> value = policy(key, request.getConfigValue(), request.getIsPublic());
        row.setConfigValue(json.writeValueAsString(value));
        row.setDescription(request.getDescription());
        row.setIsPublic(request.getIsPublic());
        configs.updateConfig(row);
        return profile(configs.selectById(key));
    }

    /** 配置权限彼此独立，不借 query 的权限放开 update。 */
    private void requireAdmin(String operation) {
        long actor = identities.requireCurrent();
        sessions.requireActive(sessions.currentToken(), actor);
        authorization.requireScope(actor, "admin:access");
        authorization.requireScope(actor, "blog:config:" + operation);
    }

    /** 不查任意数据库配置键，未知键统一404。 */
    private void requireKey(String key) {
        if (!KEYS.contains(key)) {
            throw new BusinessException(404, "NOT_FOUND", "配置不存在");
        }
    }

    /** 初始化配置缺失不能通过 PUT 偷偷新建。 */
    private BlogSiteConfig requireConfig(BlogSiteConfig row) {
        if (row == null) {
            throw new BusinessException(404, "NOT_FOUND", "配置不存在");
        }
        return row;
    }

    /** 固定顺序重建对象，MySQL JSON 属性重排也不会让业务版本无故变化。 */
    private Map<String, Object> policy(String key, Map<String, Object> value, int isPublic) {
        if (json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8).length > 16 * 1024) {
            throw invalid();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if ("site".equals(key)) {
            if (!value.keySet().equals(Set.of("title", "description", "icp", "publicSecurityRecord", "startDate"))) {
                throw invalid();
            }
            result.put("title", text(value.get("title"), 1, 100));
            result.put("description", text(value.get("description"), 0, 500));
            result.put("icp", text(value.get("icp"), 0, 100));
            result.put("publicSecurityRecord", text(value.get("publicSecurityRecord"), 0, 100));
            Object date = value.get("startDate");
            if (date != null) {
                String parsed = text(date, 10, 10);
                try {
                    if (!parsed.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || LocalDate.parse(parsed).getYear() < 1) {
                        throw invalid();
                    }
                } catch (DateTimeParseException exception) {
                    throw invalid();
                }
                date = parsed;
            }
            result.put("startDate", date);
        } else {
            if (isPublic != 0 || !value.keySet().equals(Set.of("enabled", "reviewRequired"))
                    || !(value.get("enabled") instanceof Boolean) || !(value.get("reviewRequired") instanceof Boolean)) {
                throw invalid();
            }
            result.put("enabled", value.get("enabled"));
            result.put("reviewRequired", value.get("reviewRequired"));
        }
        return result;
    }

    /** 内层文本同样按 Unicode 字符校验，不让数字或 null 冒充字符串。 */
    private String text(Object value, int min, int max) {
        if (!(value instanceof String text)) {
            throw invalid();
        }
        String clean = text.strip();
        int length = clean.codePointCount(0, clean.length());
        if (length < min || length > max) {
            throw invalid();
        }
        return clean;
    }

    /** 数据库配置损坏时明确503，不能返回空对象或默认开启；错误里不带原始配置。 */
    private ConfigVO view(BlogSiteConfig row) {
        try {
            Map<String, Object> raw = json.readValue(row.getConfigValue(), new TypeReference<Map<String, Object>>() { });
            if (raw == null) {
                throw invalid();
            }
            return new ConfigVO(row.getConfigKey(), policy(row.getConfigKey(), raw, row.getIsPublic()),
                    row.getDescription(), row.getIsPublic(), row.getUpdateTime());
        } catch (JacksonException | BusinessException exception) {
            throw new BusinessException(503, "SERVICE_UNAVAILABLE", "站点配置不可用");
        }
    }

    /** 只把持久化业务资料计入版本，不计数据库更新时间或 JSON 属性顺序。 */
    private ConfigProfileVO profile(BlogSiteConfig row) {
        ConfigVO value = view(row);
        String digest = json.writeValueAsString(Arrays.asList(value.configKey(), value.configValue(),
                value.description(), value.isPublic()));
        return new ConfigProfileVO(value, "\"" + TokenUtils.digest(digest) + "\"");
    }

    /** 权限和目标检查后再查版本，同目标更新也不能绕过旧版本。 */
    private void checkRevision(BlogSiteConfig row, String revision) {
        if (revision == null) {
            throw new BusinessException(428, "PRECONDITION_REQUIRED", "缺少If-Revision");
        }
        if (!revision.matches("\"[0-9a-f]{64}\"")) {
            throw new BusinessException(400, "VALIDATION_ERROR", "If-Revision格式错误");
        }
        if (!revision.equals(profile(row).revision())) {
            throw new BusinessException(412, "REVISION_CONFLICT", "配置已修改，请重新获取");
        }
    }

    /** 配置字段错误只返回通用提示，不回显可能误填的敏感值。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "配置须符合白名单字段、类型和长度要求");
    }
}
