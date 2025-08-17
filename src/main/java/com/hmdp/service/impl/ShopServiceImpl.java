package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Result queryId(Long id) {
        String key = "cache:shop:" + id;
        // 1. 尝试从缓存获取
        String s = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(s)) {
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return Result.ok(shop);
        }
        // 2. 缓存为空，加互斥锁
        String lockKey = "lock:shop:" + id;
        boolean isLock = false;
        try {
            isLock = tryLock(lockKey); // 锁过期时间 10 秒
            if (!isLock) {
                // 没拿到锁，等待 50ms 再重试
                Thread.sleep(50);
                return queryId(id); // 递归重试
            }
            // 3. 拿到锁后，查询数据库
            Shop shop = getById(id);
            if (shop == null) {
                // 数据库不存在，防止缓存穿透
                redisTemplate.opsForValue().set(key, "", 2, TimeUnit.MINUTES);
                return Result.fail("查询失败");
            }
            // 4. 数据库存在，写入缓存
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30, TimeUnit.MINUTES);
            return Result.ok(shop);

        } catch (InterruptedException e) {
            e.printStackTrace();
            return Result.fail("查询失败");
        } finally {
            // 5. 释放锁
            if (isLock) {
                unlock(lockKey);
            }
        }
    }
    // 获取分布式锁
    private boolean tryLock(String lockKey) {
        // setIfAbsent 返回 Boolean，需要防止 null
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS));
    }
    // 释放锁
    private void unlock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
    public Result queryId2(Long id) {
        String key = "cache:shop:" + id;
        String lockKey = "lock:shop:" + id;
        // 1. 从缓存获取
        String json = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            RedisData<Shop> redisData = JSONUtil.toBean(json, RedisData.class);
            Shop shop = redisData.getData();

            // 2. 判断逻辑过期
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                // 未过期，直接返回
                return Result.ok(shop);
            }

            // 3. 已过期，异步刷新缓存
            if (redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                // 异步刷新
                CompletableFuture.runAsync(() -> {
                    boolean isLock = tryLock(lockKey);
                    if (isLock) {
                        try {
                            refreshShopCache(id, key);
                        } finally {
                            unlock(lockKey);
                        }
                    }
                });
            }

            // 4. 先返回旧数据
            return Result.ok(shop);
        }

        // 5. 缓存不存在，直接加载数据库并写入缓存
        Shop shop = getById(id);
        if (shop == null) {
            // 防止缓存穿透
            redisTemplate.opsForValue().set(key, "", 2, TimeUnit.MINUTES);
            return Result.fail("查询失败");
        }
        setShopCache(shop, key, 30); // 30 分钟逻辑过期
        return Result.ok(shop);
    }
    private void setShopCache(Shop shop, String key, long expireMinutes) {
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expireMinutes));
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    private void refreshShopCache(Long id, String key) {
        Shop shop = getById(id);
        if (shop != null) {
            setShopCache(shop, key, 30);
        }
    }
    @Override
    @Transactional//可加可不加，多条数据库语句就加事务
    public Result updateShop(Shop shop) {
        Long id=shop.getId();
        String key="cache:shop"+id;
        if(id==null){
            return Result.fail("id不能为空");
        }
        updateById(shop);
        redisTemplate.delete(key);
        return Result.ok("成功更新");
    }
}
