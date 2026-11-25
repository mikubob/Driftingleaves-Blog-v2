package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * 创建和修改共用资料字段，可空字段用null清空；状态不随资料修改。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MusicWriteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 标题
    @NotNull
    @TextLength(min = 1, max = 200)
    private String title;

    // 歌手
    @TextLength(min = 1, max = 200)
    private String artist;

    // 时长（秒）
    @DecimalMin("0")
    @DecimalMax("4294967295")
    @Digits(integer = 10, fraction = 0)
    private BigDecimal durationSeconds;

    // 封面图URL
    @TextLength(min = 1, max = 2048)
    private String coverUrl;

    // 音频URL
    @NotNull
    @TextLength(min = 1, max = 2048)
    private String audioUrl;

    // 歌词URL
    @TextLength(min = 1, max = 2048)
    private String lyricUrl;

    // 歌词类型
    @Pattern(regexp = "lrc|json|txt", message = "请选择LRC、JSON或TXT歌词格式")
    private String lyricType;

    // 排序权重
    @NotNull
    private Integer sortOrder;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setTitle(String value) {
        present.add("title");
        title = value == null ? null : value.strip();
    }

    public void setArtist(String value) {
        present.add("artist");
        artist = value == null ? null : value.strip();
    }

    public void setDurationSeconds(BigDecimal value) {
        present.add("durationSeconds");
        durationSeconds = value;
    }

    public void setCoverUrl(String value) {
        present.add("coverUrl");
        coverUrl = value == null ? null : value.strip();
    }

    public void setAudioUrl(String value) {
        present.add("audioUrl");
        audioUrl = value == null ? null : value.strip();
    }

    public void setLyricUrl(String value) {
        present.add("lyricUrl");
        lyricUrl = value == null ? null : value.strip();
    }

    public void setLyricType(String value) {
        present.add("lyricType");
        lyricType = value == null ? null : value.strip();
    }

    public void setSortOrder(Integer value) {
        present.add("sortOrder");
        sortOrder = value;
    }

    /**
     * 创建和替换都必须提供可空字段，防止漏传被当作清空。
     */
    @AssertTrue(message = "表单内容未完整提交，请重新打开编辑窗口后重试。")
    public boolean isComplete() {
        return present.size() == 8;
    }

    /**
     * 歌词地址与类型一起出现；时长只收整数秒。
     */
    @AssertTrue(message = "歌词地址和格式须同时填写或同时清空，时长须为整数秒。")
    public boolean isValidLyrics() {
        return (lyricUrl == null) == (lyricType == null)
                && (durationSeconds == null || durationSeconds.scale() <= 0);
    }
}
