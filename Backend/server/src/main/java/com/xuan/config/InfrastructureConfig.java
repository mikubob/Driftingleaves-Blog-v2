package com.xuan.config;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.xuan.properties.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.util.List;

/**
 * 配置分页、密码加密、UTC 时钟和事务执行顺序。
 */
@Configuration
@EnableTransactionManagement(order = 100)
public class InfrastructureConfig {

    /** 配置 Redis 会话存储，复用官方 DAO 并添加本站前缀，避免与其他应用混用。 */
    @Bean
    @Primary
    public SaTokenDaoForRedisTemplate sessionDao(RedisConnectionFactory factory, AuthProperties properties) {
        // 1. 沿用官方 DAO，只补上保留 TTL 和本站键前缀的行为。
        SaTokenDaoForRedisTemplate dao = new SaTokenDaoForRedisTemplate() {
            /** 更新会话并保留过期时间，Redis 5 不支持 KEEPTTL，改用脚本，已过期的会话不再写入。 */
            @Override
            public void setStringAndKeepTTL(String finalKey, String value) {
                // 一次 Lua 里读旧 TTL 并设置新值，键已经过期就不能更新复活。
                stringRedisTemplate.execute(new DefaultRedisScript<>("""
                        local ttl = redis.call('PTTL', KEYS[1])
                        if ttl == -2 then return 0 end
                        redis.call('SET', KEYS[1], ARGV[1])
                        if ttl >= 0 then redis.call('PEXPIRE', KEYS[1], ttl) end
                        return 1
                        """, Long.class), List.of(finalKey), value);
            }

            /**
             * 给会话 Redis 键添加本站前缀。
             */
            @Override
            public String wrapKey(String key) {
                return properties.getRedisPrefix() + "session:" + key;
            }
        };

        // 2. 用现有连接工厂初始化 DAO，不再创建另一套 Redis 客户端。
        dao.init(factory);
        return dao;
    }

    /**
     * 配置 MySQL 分页拦截器。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 配置 BCrypt 密码编码器，加密强度从配置读取。
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder(AuthProperties properties) {
        return new BCryptPasswordEncoder(properties.getBcryptStrength());
    }

    /**
     * 配置 UTC 时钟，避免受机器时区影响。
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
