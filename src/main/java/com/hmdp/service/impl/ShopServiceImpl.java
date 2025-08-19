package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. 判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不带坐标 → 走数据库普通分页
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 2. 如果带了坐标 → 使用 Redis GEO 来做“附近商铺查询”
        String key = "shop:geo:" + typeId;
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // GEO 查询：按坐标搜索，带上距离
        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                redisTemplate.opsForGeo().radius(
                        key,
                        new Circle(new Point(x, y), new Distance(5000)), // 圆心 & 半径
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()   // 返回距离
                                .sortAscending()     // 按距离排序
                                .limit(end)          // 取前 end 条
                );
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (from >= list.size()) {
            // 没有更多数据了
            return Result.ok(Collections.emptyList());
        }

        // 3. 解析 GEO 查询结果，得到商铺 id 和距离
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>();
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, result.getDistance());
        });

        // 4. 按顺序从数据库查询商铺信息
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();

        // 5. 把距离信息补充到 Shop 里（需要在 Shop 类加一个 transient 字段 distance）
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shops);
    }

}
