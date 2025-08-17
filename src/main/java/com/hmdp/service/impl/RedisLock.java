package com.hmdp.service.impl;

import com.hmdp.service.ILock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock {

    private StringRedisTemplate redisTemplate;
    private String name; // 锁的名字
    private String key;  // Redis key
    private Long value; // 锁标识，保证只释放自己的锁

    public RedisLock(StringRedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
        this.key = "lock:" + name;
        //this.value = Thread.currentThread().getId(); // 唯一标识
        this.value = UUID.randomUUID().getMostSignificantBits();
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value.toString(), timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "   return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public void unlock() {
        // 使用加锁时的唯一值 value
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
    }

}

