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
import java.time.Clock;
import java.util.List;

/** 注册实际使用的分页、密码和时钟能力，不启用定时、缓存或异步。 */
@Configuration
public class InfrastructureConfig {
    /** 复用官方 Redis DAO 的键包装扩展，隔离本站会话命名空间。 */
    @Bean
    @Primary
    public SaTokenDaoForRedisTemplate sessionDao(RedisConnectionFactory factory, AuthProperties properties) {
        // 1. 复用官方 DAO，仅扩展保留 TTL 和隔离键前缀的行为。
        SaTokenDaoForRedisTemplate dao = new SaTokenDaoForRedisTemplate() {
            /** 本机 Redis 5 无 KEEPTTL；原子保留 TTL，且不复活已过期会话。 */
            @Override
            public void setStringAndKeepTTL(String finalKey, String value) {
                // 在同次 Lua 执行中取得原 TTL 并恢复到新值，已过期键不能被更新复活。
                stringRedisTemplate.execute(new DefaultRedisScript<>("""
                        local ttl = redis.call('PTTL', KEYS[1])
                        if ttl == -2 then return 0 end
                        redis.call('SET', KEYS[1], ARGV[1])
                        if ttl >= 0 then redis.call('PEXPIRE', KEYS[1], ttl) end
                        return 1
                        """, Long.class), List.of(finalKey), value);
            }

            @Override
            public String wrapKey(String key) {
                return properties.getRedisPrefix() + "session:" + key;
            }
        };
        // 2. 使用现有连接工厂初始化 DAO，不额外创建一套 Redis 客户端。
        dao.init(factory);
        return dao;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder(AuthProperties properties) {
        return new BCryptPasswordEncoder(properties.getBcryptStrength());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
