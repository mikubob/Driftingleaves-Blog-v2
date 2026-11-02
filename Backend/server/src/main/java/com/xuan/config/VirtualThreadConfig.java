package com.xuan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 配置失败日志和在线连接执行器，HTTP 请求继续使用 Boot 虚拟线程。
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

    /** 创建在线连接执行器，执行票据、身份查询和消息发送，不继承请求线程信息。 */
    @Bean(name = "onlineExecutor", destroyMethod = "close", defaultCandidate = false)
    public ExecutorService onlineExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .name("online-", 0).inheritInheritableThreadLocals(false).factory());
    }
}
