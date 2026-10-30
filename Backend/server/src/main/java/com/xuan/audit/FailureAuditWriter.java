package com.xuan.audit;

import com.xuan.entity.SysOperationLog;
import com.xuan.mapper.OperationLogMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 原事务回滚后，用单独的虚拟线程和新事务写失败日志，不带入请求、Sa-Token 或原业务事务。
 */
@Component
public class FailureAuditWriter {

    private static final System.Logger LOGGER = System.getLogger(FailureAuditWriter.class.getName());
    private final ExecutorService executor;
    private final OperationLogMapper logs;
    private final TransactionTemplate transaction;

    /** 明确使用审计执行器，新事务只收已经去掉敏感信息的操作记录。 */
    public FailureAuditWriter(@Qualifier("auditExecutor") ExecutorService executor,
            OperationLogMapper logs, PlatformTransactionManager manager) {
        this.executor = executor;
        this.logs = logs;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 原事务结束后才能提交日志任务；业务错误已经返回，日志再出错就另报固定消息。
     *
     * @param log 不含请求对象、凭据、原始异常或其他可变业务对象的审计记录
     * @return 可观察的写入完成结果；失败仍以固定消息报告，不改变原业务错误
     */
    public CompletableFuture<Void> write(SysOperationLog log) {
        try {
            // shortcut: 失败审计在进程内排队，要求跨宕机可靠投递时改用持久化任务。
            return CompletableFuture.runAsync(() -> {
                try {
                    transaction.executeWithoutResult(status -> logs.insertLog(log));
                } catch (RuntimeException exception) {
                    LOGGER.log(System.Logger.Level.ERROR, "失败审计写入失败，未保存该记录");
                    throw exception;
                }
            }, executor);
        } catch (RejectedExecutionException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "审计执行器已关闭，未接受失败记录");
            return CompletableFuture.failedFuture(exception);
        }
    }
}
