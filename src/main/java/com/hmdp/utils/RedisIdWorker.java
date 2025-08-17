package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1704067200L;
    // 假设基准时间是 2024-01-01 00:00:00 UTC（秒级时间戳）

    private static final int COUNT_BITS = 32; // 序列号部分占 32 位

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 生成全局唯一ID
     * @param keyPrefix Redis key 前缀（区分不同业务）
     * @return 全局唯一ID 订单号
     */
    public  long nextId(String keyPrefix) {
        // 1. 生成时间戳（秒级）
        long nowSecond = LocalDate.now().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 生成当天的日期字符串（用来作为 Redis key 的一部分）
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        // 3. 调用 Redis 的自增，获取当天的序列号
        Long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 4. 拼接时间戳和序列号
        return (timeStamp << COUNT_BITS) | count;
    }
}

