package com.hmdp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.User;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void testRedisLock() {
        // 获取分布式锁
        RLock lock = redissonClient.getLock("myTestLock");
        boolean locked = false;
        try {
            // 尝试加锁：等待最多2秒，锁自动释放时间5秒
            locked = lock.tryLock(2, 5, TimeUnit.SECONDS);
            if (locked) {
                System.out.println("成功获取锁，开始执行任务...");
                // 模拟业务处理
                Thread.sleep(3000);
                System.out.println("任务执行完成");
            } else {
                System.out.println("获取锁失败，可能其他线程正在执行任务");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (locked) {
                lock.unlock();
                System.out.println("锁已释放");
            }
        }
    }
    @Test
    public void redis() throws JsonProcessingException {
        // 1️⃣ 创建对象
        User user = new User();
        user.setId(1L);
        user.setNickName("Xiaoming");

        // 2️⃣ 转 JSON 字符串
        String userJson = mapper.writeValueAsString(user);

        // 3️⃣ 写入 Redis（DB 0）
        redis.opsForValue().set("hong", userJson);

        // 4️⃣ 读取 Redis
        String value = redis.opsForValue().get("hong");
        User userFromRedis = mapper.readValue(value, User.class);

        // 5️⃣ 输出
        System.out.println("读取 Redis 的对象：" + userFromRedis);

        // 6️⃣ 检查 key 是否存在
        Boolean hasKey = redis.hasKey("xiaoming");
        System.out.println("Redis 是否存在 key 'xiaoming': " + hasKey);
     }
    @Test
    public void hash() throws JsonProcessingException {
        User user = new User();
        user.setId(1L);
        user.setNickName("Xiaoming");

// 1. 存入 Hash
        Map<String, String> userMap = new HashMap<>();
        userMap.put("id", user.getId().toString());
        userMap.put("name", user.getNickName().toString());
        redis.opsForHash().putAll("user:1", userMap);

// 2. 读取 Hash
        Map<Object, Object> map = redis.opsForHash().entries("user:1");
        User userFromHash = new User();
        userFromHash.setId(Long.valueOf(map.get("id").toString()));
        userFromHash.setNickName(map.get("name").toString());

    }
}
