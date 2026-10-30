package com.xuan.dto;

import com.xuan.validation.TextLength;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

import lombok.Getter;
import lombok.AccessLevel;
import java.util.HashSet;
import java.util.Set;

/** 创建和PUT均须显式提供全部字段，可空字段用null清空。 */
@Getter
public class MusicWriteDTO {
    @NotNull @TextLength(min = 1, max = 200)
    private String title;
    @TextLength(min = 1, max = 200)
    private String artist;
    @DecimalMin("0") @DecimalMax("4294967295") @Digits(integer = 10, fraction = 0)
    private BigDecimal durationSeconds;
    @TextLength(min = 1, max = 2048)
    private String coverUrl;
    @NotNull @TextLength(min = 1, max = 2048)
    private String audioUrl;
    @TextLength(min = 1, max = 2048)
    private String lyricUrl;
    @Pattern(regexp = "lrc|json|txt")
    private String lyricType;
    @NotNull
    private Integer sortOrder;
    @NotNull @Min(0) @Max(1)
    private Integer status;
    @Getter(AccessLevel.NONE)
    private final Set<String> present = new HashSet<>();

    public void setTitle(String value) { present.add("title"); title = value == null ? null : value.strip(); }
    public void setArtist(String value) { present.add("artist"); artist = value == null ? null : value.strip(); }
    public void setDurationSeconds(BigDecimal value) { present.add("durationSeconds"); durationSeconds = value; }
    public void setCoverUrl(String value) { present.add("coverUrl"); coverUrl = value == null ? null : value.strip(); }
    public void setAudioUrl(String value) { present.add("audioUrl"); audioUrl = value == null ? null : value.strip(); }
    public void setLyricUrl(String value) { present.add("lyricUrl"); lyricUrl = value == null ? null : value.strip(); }
    public void setLyricType(String value) { present.add("lyricType"); lyricType = value == null ? null : value.strip(); }
    public void setSortOrder(Integer value) { present.add("sortOrder"); sortOrder = value; }
    public void setStatus(Integer value) { present.add("status"); status = value; }
    /** 创建和替换都必须提供可空字段，防止漏传被当作清空。 */
    @AssertTrue(message = "必须提供全部可写字段，可空字段可为null")
    public boolean isComplete() { return present.size() == 9; }
    /** 歌词地址与类型一起出现；时长只收整数秒。 */
    @AssertTrue(message = "歌词组合或时长不合法")
    public boolean isValidLyrics() {
        return (lyricUrl == null) == (lyricType == null)
                && (durationSeconds == null || durationSeconds.scale() <= 0);
    }
}
