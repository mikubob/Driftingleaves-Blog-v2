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
 * 音乐附件工具，整理封面、音频和歌词地址，只从本站文件路径提取引用。
 */
@Component
@RequiredArgsConstructor
public class MusicAttachmentPolicy {

    private static final Pattern MUSIC_FILE = Pattern.compile(
            "/api/v1/blog/music/files/([1-9][0-9]{0,18})/content");

    private final FileUrlPolicy urls;

    /** 整理音乐文件地址，只保存本站读取路径或外部 HTTP(S) 地址，不公开私有存储直链。 */
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

    /** 从音乐文件路径获取文件id，外部地址不算本站文件引用。 */
    public Long fileId(String value) {
        if (value == null) {
            return null;
        }
        Matcher match = MUSIC_FILE.matcher(value);
        return match.matches() ? IdUtils.parse(match.group(1)) : null;
    }

    /** 生成音乐文件读取地址，匿名读取时仍要检查曲目是否展示及是否引用该文件。 */
    public String fileUrl(long id) {
        return "/api/v1/blog/music/files/" + id + "/content";
    }

    /** 返回附件地址错误，不带原 URL、存储路径或私密文件的存在信息。 */
    private BusinessException invalid() {
        return new BusinessException(400, "VALIDATION_ERROR", "附件引用或封面URL不符合本站上下文约束");
    }
}
