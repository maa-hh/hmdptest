package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


public class RedissonConfig {

    @Bean(destroyMethod="shutdown") // 容器关闭时关闭 Redisson
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 单机模式
        config.useSingleServer()
                .setAddress("redis://192.168.0.104:6379")
                .setPassword("123456") ;// 如果有密码
        return Redisson.create(config);
    }
}

