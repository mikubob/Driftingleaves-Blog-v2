package com.xuan.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 缺少参数才补默认值，显式传入的值留给原有校验处理。 */
class QueryDefaultsTest {

    @Test
    void missingPaginationTrashAndLimitKeepExistingDefaults() {
        PageQueryDTO page = new PageQueryDTO(null, null);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(10);
        AdminArticleQueryDTO article = new AdminArticleQueryDTO(null, null, null, null, null, null,
                null, null, null);
        assertThat(article.page()).isEqualTo(1);
        assertThat(article.pageSize()).isEqualTo(10);
        assertThat(article.trash()).isZero();
        assertThat(new StatisticsQueryDTO(null, null, null, null).limit()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 100})
    void explicitValuesAreNotSilentlyReplaced(int value) {
        PageQueryDTO page = new PageQueryDTO(value, value);
        assertThat(page.page()).isEqualTo(value);
        assertThat(page.pageSize()).isEqualTo(value);
        assertThat(new StatisticsQueryDTO(null, null, null, value).limit()).isEqualTo(value);
    }
}
