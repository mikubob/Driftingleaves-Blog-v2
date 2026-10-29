package com.xuan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import java.util.TimeZone;

/** 唯一后端入口；应用和 JDBC 的 DATETIME 均使用 UTC。 */
@SpringBootApplication
@MapperScan("com.xuan.mapper")
@ConfigurationPropertiesScan
public class DriftingLeavesApplication {
    public static void main(String[] args) {
        // 1. 在创建 Spring 组件前固定默认时区，避免初始化逻辑使用机器本地时区。
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        // 2. 启动唯一后端应用，由配置扫描装配实际需要的基础设施。
        SpringApplication.run(DriftingLeavesApplication.class, args);
    }
}
