package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;

public class CaffeineCache {
    @Bean
    public Cache<String,Object> itemCache(){
        return Caffeine.newBuilder()
                .initialCapacity(1000)
                .maximumSize(10_000)
                .build();
    }
}
