package com.xuan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/**
 * 唯一的后端启动入口，应用和 JDBC 的 DATETIME 都用 UTC。
 */
@SpringBootApplication
@MapperScan("com.xuan.mapper")
@ConfigurationPropertiesScan
public class DriftingLeavesApplication {

    /**
     * 先固定默认时区为 UTC，再启动后端组件。
     */
    public static void main(String[] args) {
        // 1. Spring 组件创建前先固定默认时区，避免初始化时用了机器本地时区。
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // 2. 启动后端应用，按配置加载实际需要的组件。
        SpringApplication.run(DriftingLeavesApplication.class, args);
    }
}
