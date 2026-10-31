package com.xuan;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.xuan.handle.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLSyntaxErrorException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 不连接数据库，检查基础服务诊断可用且不会记录异常中的秘密。 */
class GlobalExceptionHandlerTest {

    @Test
    void infrastructureFailuresKeepPublicResponseAndOnlyLogSafeDiagnostics() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            var handler = new GlobalExceptionHandler();
            String sensitiveMessage = "private-password-and-query-parameter";
            var sql = new BadSqlGrammarException(sensitiveMessage, "SELECT " + sensitiveMessage,
                    new SQLSyntaxErrorException(sensitiveMessage, "42S02", 1146));
            var redis = new DataAccessResourceFailureException(sensitiveMessage,
                    new IllegalStateException(sensitiveMessage));
            var transaction = new CannotCreateTransactionException(sensitiveMessage, sql);

            for (Exception failure : List.of(sql, redis, transaction)) {
                var response = handler.unavailable(failure);
                assertThat(response.getStatusCode().value()).isEqualTo(503);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
                assertThat(response.getBody().msg()).isEqualTo("基础服务暂不可用");
                assertThat(response.getBody().data()).isNull();
            }

            assertThat(appender.list).hasSize(3);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("42S02", "1146");
            assertThat(appender.list.get(1).getFormattedMessage()).contains("IllegalStateException");
            assertThat(appender.list.get(2).getFormattedMessage()).contains("42S02", "1146");
            for (ILoggingEvent event : appender.list) {
                assertThat(event.getFormattedMessage()).doesNotContain(sensitiveMessage, "SELECT");
                assertThat(event.getThrowableProxy()).isNull();
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
