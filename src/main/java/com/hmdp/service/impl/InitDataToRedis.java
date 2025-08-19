package com.hmdp.service.impl;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class InitDataToRedis implements InitializingBean {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void afterPropertiesSet() {
        // 项目启动时执行
        redisTemplate.opsForValue().set("system:version", "1.0");
        redisTemplate.opsForHash().put("config", "theme", "dark");
        System.out.println("基础数据已加载到 Redis");
    }
}

