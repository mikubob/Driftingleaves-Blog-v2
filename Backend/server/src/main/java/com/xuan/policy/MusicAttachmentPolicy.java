package com.xuan.policy;

import com.xuan.exception.BusinessException;
import com.xuan.policy.FileUrlPolicy;
import com.xuan.policy.UrlPolicy;
import com.xuan.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规范音乐封面、音频和歌词地址，只从受管路径提取文件引用。
 */
@Component
@RequiredArgsConstructor
public class MusicAttachmentPolicy {

    private static final Pattern MUSIC_FILE = Pattern.compile(
            "/api/v1/blog/music/files/([1-9][0-9]{0,18})/content");

    private final FileUrlPolicy urls;

    /** 音乐只保存规范的受管读取路径或外部 HTTP(S) 地址，不把私有存储直链公开。 */
    public String normalizeUrl(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String path = uri.getRawPath();
            boolean local = uri.getHost() == null || urls.siteOrigin(uri);
            Matcher match = MUSIC_FILE.matcher(path == null ? "" : path);
            if (local && match.matches()
                    && (value.equals(path) || UrlPolicy.valid(value))
                    && uri.getRawQuery() == null && uri.getRawFragment() == null) {
                IdUtils.parse(match.group(1));
                return path;
            }
            if (urls.attachmentPath(value) == null && UrlPolicy.valid(value)) {
                return value;
            }
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        throw invalid();
    }

    /** 只解析已规范化的音乐文件路径，外部地址不形成本站文件引用。 */
    public Long fileId(String value) {
        if (value == null) {
            return null;
        }
        Matcher match = MUSIC_FILE.matcher(value);
        return match.matches() ? IdUtils.parse(match.group(1)) : null;
    }

    /** 上传后即可组成音乐地址，匿名读取仍由当前展示状态和真实引用决定。 */
    public String fileUrl(long id) {
        return "/api/v1/blog/music/files/" + id + "/content";
    }

    /** 输入有问题只报固定错误，不透露 URL、存储键或私密文件是否存在。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "附件引用或封面URL不符合本站上下文约束");
    }
}
