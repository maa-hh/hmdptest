package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient1() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://192.168.0.101:6379")
                .setPassword("123456");
        return Redisson.create(config);
    }

//    @Bean(destroyMethod = "shutdown")
//    public RedissonClient redissonClient2() {
//        Config config = new Config();
//        config.useSingleServer()
//                .setAddress("redis://192.168.0.102:6379")
//                .setPassword("123456");
//        return Redisson.create(config);
//    }
//
//    @Bean(destroyMethod = "shutdown")
//    public RedissonClient redissonClient3() {
//        Config config = new Config();
//        config.useSingleServer()
//                .setAddress("redis://192.168.0.103:6379")
//                .setPassword("123456");
//        return Redisson.create(config);
//    }
}

