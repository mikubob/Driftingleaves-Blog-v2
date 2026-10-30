package com.xuan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 独立审计执行器；HTTP/MVC 仍使用 Boot 的虚拟线程支持，不搬运线程上下文。 */
@Configuration(proxyBeanMethods = false)
public class VirtualThreadConfig {
    /**
     * 为回滚后的失败审计创建独立虚拟线程；容器关闭时等待已接受任务结束。
     * @return 由容器 close 的正式 Java 21 执行器，不覆盖 Boot applicationTaskExecutor
     */
    @Bean(name = "auditExecutor", destroyMethod = "close", defaultCandidate = false)
    public ExecutorService auditExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("audit-", 0).factory());
    }
}
