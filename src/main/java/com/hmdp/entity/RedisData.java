package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData<T> {
    private T data;          // 实际数据
    private LocalDateTime expireTime; // 逻辑过期时间

    // 构造方法 + getter/setter
}

