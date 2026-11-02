package com.xuan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 配置失败日志线程池，HTTP 请求继续使用 Boot 虚拟线程，不传递请求线程信息。
 */
@Configuration(proxyBeanMethods = false)
public class VirtualThreadConfig {

    /**
     * 创建失败日志线程池，回滚后用虚拟线程写日志，关闭时等待已接收的任务完成。
     *
     * @return 由容器 close 的正式 Java 21 执行器，不覆盖 Boot applicationTaskExecutor
     */
    @Bean(name = "auditExecutor", destroyMethod = "close", defaultCandidate = false)
    public ExecutorService auditExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("audit-", 0).factory());
    }
}
