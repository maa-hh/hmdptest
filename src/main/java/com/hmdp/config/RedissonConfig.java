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
                .setAddress("redis://127.0.0.1:6379");
                //.setPassword("yourPassword") // 如果有密码
        return Redisson.create(config);
    }
}

