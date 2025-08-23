package com.hmdp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.User;
import org.junit.jupiter.api.Test;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Autowired
    private RedissonClient redissonClient1;

    @Autowired
    private RedissonClient redissonClient2;

    @Autowired
    private RedissonClient redissonClient3;
    @Test
    public void testRedLock() {
        // 分别从三个客户端获取锁
        RLock lock1 = redissonClient1.getLock("myLock1");
        RLock lock2 = redissonClient2.getLock("myLock2");
        RLock lock3 = redissonClient3.getLock("myLock3");

        // 组装成 Lock
        RLock lock=redissonClient.getMultiLock(lock1,lock2,lock3);

        try {
            boolean isLock = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (isLock) {
                System.out.println("✅ 成功获取 RedLock 分布式锁");
                Thread.sleep(5000); // 模拟业务逻辑
            } else {
                System.out.println("❌ 获取 Lock 分布式锁失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
            System.out.println("🔓 锁已释放");
        }
    }
    @Test
    //基于算法进行数量统计，有误差，UV统计
    public void testHypreLogLog(){
        String [] content=new String[1000];
        int index=0;
        for(int i=0;i<1000_000;i++){
            content[index++]="user"+index;
            if(index==1000){
                index=0;
                redis.opsForHyperLogLog().add("hyper",content);
            }
        }
        Long size=redis.opsForHyperLogLog().size("hyper");
        System.out.println(size);
    }
    @Test
    public  void testCaffine() throws InterruptedException {
        Cache<String, String> cache = Caffeine.newBuilder()
                .maximumSize(12)
                .expireAfterWrite(Duration.ofSeconds(20))
                .build();
        cache.put("gf","123");
        Thread.sleep(20);
        String ans=cache.getIfPresent("gf");
        cache.get("gf",(key)->{return "elsemethod";});
    }
        private static final int BIG_KEY_THRESHOLD = 1000;
        private static final int TOP_N = 10;

        private final ExecutorService executor = Executors.newFixedThreadPool(4);


    @Test
    public void scanBigKeys() {
        Map<String, Long> keySizes = new HashMap<>();
        final ScanOptions scanOptions = ScanOptions.scanOptions().count(100).build();

        redis.execute((RedisCallback<Void>) connection -> {
            Cursor<byte[]> cursor = connection.scan(scanOptions);
            while (cursor.hasNext()) {
                String key = new String(cursor.next());
                long size = getKeySize(connection, key);
                keySizes.put(key, size);

                if (size >= BIG_KEY_THRESHOLD) {
                    final String delKey = key;
                    final long delSize = size;
                    executor.submit(() -> asyncDelete(delKey, delSize));
                }
            }
            return null;
        });

        printTopNKeys(keySizes);
        executor.shutdown();
    }

    // 获取 key 的大小
    private long getKeySize(RedisConnection connection, String key) {
        DataType type = connection.type(key.getBytes());
        switch (type) {
            case STRING:
                return connection.strLen(key.getBytes());
            case LIST:
                return connection.lLen(key.getBytes());
            case SET:
                return connection.sCard(key.getBytes());
            case ZSET:
                return connection.zCard(key.getBytes());
            case HASH:
                return connection.hLen(key.getBytes());
            default:
                return 0;
        }
    }

    // 异步删除
    private void asyncDelete(String key, long size) {
        redis.delete(key);
        System.out.println("Deleted big key asynchronously: " + key + ", size=" + size);
    }

    // 打印 top N
    private void printTopNKeys(Map<String, Long> keySizes) {
        int topN = 10;
        keySizes.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .forEach(e -> System.out.println("Key=" + e.getKey() + ", Size=" + e.getValue()));
    }
    @Test
    public  void pipeline(){
            Pipeline pipeline=new Pipeline();
            for(int i=0;i<1000;i++){
                pipeline.set("key"+i,i+"");
                pipeline.sync();
            }
        }
    @Test
    public void testpipelineSlot(){
        Pipeline pipeline=new Pipeline();
        Map<String,String> map=new HashMap<>();
        Map<Integer,List<Map.Entry<String,String>>> map1=map.entrySet().stream().collect(Collectors.groupingBy(
                enrty->ClusterSlotHashUtil.calculateSlot(enrty.getKey())
        ));
        for(List<Map.Entry<String,String>> m:map1.values()){
            for(int i=0;i<m.size();i++){
                pipeline.set(m.get(i).getKey(),m.get(i).getValue());
            }
            pipeline.sync();
        }
    }
}
