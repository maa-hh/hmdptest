package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    StringRedisTemplate redisTemplate;
    @Override
    public Result follow(Long followId, Boolean isFollow) {
        Long userId= UserHolder.getUser().getId();
        String key="follow:"+userId;
        if(isFollow){
            Follow follow=new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            boolean success=save(follow);
            if(success){
                redisTemplate.opsForSet().add(key,followId.toString());
            }
        }
        else{
            boolean success=remove(new QueryWrapper<Follow>().eq("follow_user_id",followId));
            if(success)
                redisTemplate.opsForSet().remove(key,followId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserid) {
        Long userId= UserHolder.getUser().getId();
        int count=query().eq("user_id",userId).eq("follow_user_id",followUserid).count();
        return Result.ok(count>0);
    }

    @Override
    public Result commonFollow(Long id) {
        Long userId= UserHolder.getUser().getId();
        String key1="follow:"+id;
        String key2="follow:"+userId;
        Set<String> inter=redisTemplate.opsForSet().intersect(key1,key2);
        if(inter==null||inter.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=inter.stream().map(a->Long.valueOf(a)).collect(Collectors.toList());;
        List<UserDTO> userDTOS=ids.stream().map(u-> BeanUtil.copyProperties(u,UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
