package com.xuan;

import com.xuan.cache.PublicCacheKeys;
import com.xuan.dto.ArticleQueryDTO;
import com.xuan.dto.CommentPublicQueryDTO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicCacheKeysTest {
    @Test
    void normalizedQueriesKeepEveryFilterAndCaseInTheKey() {
        var keys = new PublicCacheKeys(Clock.systemUTC());
        var defaultQuery = new ArticleQueryDTO(null, null, null, null, null, null, null);
        assertThat(keys.key("cachedPublicArticles", new Object[]{defaultQuery})).isEqualTo(keys.key("cachedPublicArticles",
                new Object[]{new ArticleQueryDTO(1, 10, null, null, null, null, null)}));
        List<ArticleQueryDTO> queries = List.of(defaultQuery,
                new ArticleQueryDTO(2, 10, null, null, null, null, null),
                new ArticleQueryDTO(1, 20, null, null, null, null, null),
                new ArticleQueryDTO(1, 10, "Leaf", null, null, null, null),
                new ArticleQueryDTO(1, 10, "leaf", null, null, null, null),
                new ArticleQueryDTO(1, 10, null, "1", null, null, null),
                new ArticleQueryDTO(1, 10, null, null, "1", null, null),
                new ArticleQueryDTO(1, 10, null, null, null, "1", null),
                new ArticleQueryDTO(1, 10, null, null, null, null, "2026-10"));
        assertThat(queries.stream().map(q -> keys.key("cachedPublicArticles", new Object[]{q})).distinct()).hasSize(queries.size());
        assertThat(keys.key("publicDetail", new Object[]{"Leaf"})).isNotEqualTo(keys.key("publicDetail", new Object[]{"leaf"}));
        assertThat(keys.key("cachedPublicComments", new Object[]{1L, new CommentPublicQueryDTO(1, 10, "2")}))
                .isNotEqualTo(keys.key("cachedPublicComments", new Object[]{1L, new CommentPublicQueryDTO(1, 10, "3")}));
        assertThat(keys.key("sitemap", new Object[]{null})).isNotEqualTo(keys.key("sitemap", new Object[]{1}));
    }
    @Test
    void statisticsAndRankingsChangeKeysAtTheirUtcCalendarBoundaries() {
        var before = new PublicCacheKeys(Clock.fixed(Instant.parse("2026-10-31T23:59:59Z"), ZoneOffset.UTC));
        var after = new PublicCacheKeys(Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
        assertThat(before.key("publicStatistics", new Object[]{})).isNotEqualTo(after.key("publicStatistics", new Object[]{}));
        assertThat(before.key("rankings", new Object[]{"month", "like", 5})).isNotEqualTo(after.key("rankings", new Object[]{"month", "like", 5}));
        assertThat(before.key("publicItems", new Object[]{})).isEqualTo(after.key("publicItems", new Object[]{}));
    }
}
