package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public List<ShopType> getList() {
        String key = "cache:ShopType";

        // 1. 先从 Redis 获取缓存
        String cacheData = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cacheData)) {
            // 缓存存在，直接返回
            return JSONUtil.toList(cacheData, ShopType.class);
        }

        // 2. 缓存不存在，从数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 3. 将查询结果写入 Redis
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        redisTemplate.expire(key,30, TimeUnit.MINUTES);
        // 4. 返回结果
        return typeList;
    }

}
