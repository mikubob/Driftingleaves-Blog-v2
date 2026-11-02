package com.xuan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 在临时 MySQL 中导入两次演示脚本，检查约束、关联与已有数据的保留。 */
@EnabledIfSystemProperty(named = "test.mysql.bin", matches = ".+")
class DemoDataIntegrationTest {

    @Test
    void importsDemoDataTwiceWithoutOverwritingExistingRecords() throws Exception {
        try (var services = new IsolatedServices();
             var connection = DriverManager.getConnection(services.jdbcUrl, "root", "")) {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(services.jdbcUrl, "root", ""));
            String originalSite = jdbc.queryForObject(
                    "SELECT config_value FROM blog_site_config WHERE config_key='site'", String.class);
            String originalPassword = jdbc.queryForObject(
                    "SELECT password_hash FROM sys_user WHERE username='yyzytx5201314'", String.class);
            jdbc.update("INSERT INTO blog_category(category_name,slug) VALUES('已有分类','keep-existing')");
            jdbc.update("INSERT INTO blog_message(user_id,content) SELECT user_id,'已有留言正文' FROM sys_user " +
                    "WHERE username='yyzytx5201314'");

            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../../sql/demo_data.sql"));
            Map<String, Integer> counts = rowCounts(jdbc);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'v2_demo_%'",
                    Integer.class)).isEqualTo(8);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article WHERE slug LIKE 'v2-demo-article-%'",
                    Integer.class)).isEqualTo(24);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_content", Integer.class)).isEqualTo(24);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_tag", Integer.class)).isEqualTo(48);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_article_like", Integer.class)).isEqualTo(48);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment", Integer.class)).isEqualTo(36);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_message WHERE content LIKE '[V2 演示 M%'",
                    Integer.class)).isEqualTo(32);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_page_view", Integer.class)).isEqualTo(240);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT visitor_id) FROM blog_page_view", Integer.class))
                    .isEqualTo(18);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT site_code) FROM blog_page_view", Integer.class))
                    .isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log", Integer.class)).isEqualTo(12);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_message c JOIN blog_message p " +
                    "ON p.message_id=c.parent_id WHERE p.is_secret=1 AND c.is_secret=0", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_comment c JOIN blog_comment p " +
                    "ON p.comment_id=c.parent_id WHERE p.article_id<>c.article_id OR p.create_time>c.create_time",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_rss_subscription WHERE status=1",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'v2_demo_%' " +
                    "AND password_hash IS NOT NULL", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u " +
                    "ON u.user_id=ur.user_id JOIN sys_role r ON r.role_id=ur.role_id " +
                    "WHERE u.username LIKE 'v2_demo_%' AND r.role_code='admin'", Integer.class)).isZero();

            // 用户修改展示内容和审核结果后，再导入也不能把它们恢复成初始样例。
            jdbc.update("UPDATE blog_article SET title='用户编辑后的标题' WHERE slug='v2-demo-article-01'");
            jdbc.update("UPDATE blog_comment SET status=2 WHERE content LIKE '[V2 演示 C01]%'");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../../sql/demo_data.sql"));
            assertThat(rowCounts(jdbc)).isEqualTo(counts);
            assertThat(jdbc.queryForObject("SELECT title FROM blog_article WHERE slug='v2-demo-article-01'",
                    String.class)).isEqualTo("用户编辑后的标题");
            assertThat(jdbc.queryForObject("SELECT status FROM blog_comment WHERE content LIKE '[V2 演示 C01]%'",
                    Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_message WHERE content='已有留言正文'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT category_name FROM blog_category WHERE slug='keep-existing'",
                    String.class)).isEqualTo("已有分类");
            assertThat(jdbc.queryForObject("SELECT config_value FROM blog_site_config WHERE config_key='site'",
                    String.class)).isEqualTo(originalSite);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE username='yyzytx5201314'",
                    String.class)).isEqualTo(originalPassword);
        }
    }

    /** 比较所有业务表的行数，重复执行不能在关联表或统计明细里悄悄增加记录。 */
    private Map<String, Integer> rowCounts(JdbcTemplate jdbc) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema=DATABASE() AND table_type='BASE TABLE'", String.class)) {
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        return counts;
    }
}
