package com.xuan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 日志用单独执行器，HTTP 和 MVC 继续用 Boot 的虚拟线程支持，不搬运请求线程上下文。 */
@Configuration(proxyBeanMethods = false)
public class VirtualThreadConfig {
    /**
     * 失败日志在回滚后用单独的虚拟线程写，容器退出时等待已经接收的任务结束。
     * @return 由容器 close 的正式 Java 21 执行器，不覆盖 Boot applicationTaskExecutor
     */
    @Bean(name = "auditExecutor", destroyMethod = "close", defaultCandidate = false)
    public ExecutorService auditExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("audit-", 0).factory());
    }
}
