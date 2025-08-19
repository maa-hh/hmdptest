package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    FollowServiceImpl followService;
    @Override
    public Result queryBlogById(Long id){
        if(id<0||id==null) return Result.fail("不存在该博客");
        Blog blog=query().eq("id",id).one();
        if(blog==null) return Result.fail("不存在该博客");
        queryBlogUser(blog);
        isBlogLiked(blog);
        return  Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId= UserHolder.getUser().getId();
        String key="blog:like:"+id;
//        boolean isMember=redisTemplate.opsForSet().isMember(key,userId.toString());
        Double score=redisTemplate.opsForZSet().score(key,userId.toString());
        if(score==null){
            boolean success=update().setSql("liked=liked+1").eq("id",id).update();
            if(success)
            redisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
        }
        else{
            boolean success=update().setSql("liked=liked-1").eq("id",id).update();
            if(success)
                redisTemplate.opsForZSet().remove(key,userId.toString());
        }
        return Result.ok();
    }
    @Override
    public void queryBlogUser(Blog blog){
        Long userid=blog.getUserId();
        User user=userService.getById(userid);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    @Override
    public void isBlogLiked(Blog blog){
        Long userId= UserHolder.getUser().getId();
        if(userId==null){
            return;
        }
        String key="blog:like:"+blog.getId();
        Double score=redisTemplate.opsForZSet().score(key,userId.toString());
//        blog.setLiked(BooleanUtil.isTrue(isMember));
        blog.setIsLike(BooleanUtil.isTrue(score!=null));
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBlogUser(blog);
           isBlogLiked(blog);
        });
        return  Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key="blog:like:"+id;
        Set<String> top5=redisTemplate.opsForZSet().range(key,0,4);
        if(top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids=top5.stream().map(a->Long.valueOf(a)).collect(Collectors.toList());
        String idStr= StrUtil.join(",",ids);
        List<UserDTO> userDTOS=userService.query().in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list()
        .stream().
                map(user-> BeanUtil.copyProperties(user,UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean success=save(blog);
        if(!success){
            return Result.fail("新增笔记失败");
        }
        List<Follow> list=followService.query().eq("follow_user_id",user.getId()).list();
        for(Follow l:list){
            Long userId=l.getUserId();
            String key="feed:"+userId;
            redisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 保存探店博文
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "feed:" + userId;

        // 2. 从 Redis 的 ZSet 查询当前时间戳(max) 之前的推文（分页查询）
        // rangeByScoreWithScores(key, min, max, offset, count)
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                redisTemplate.opsForZSet()
                        .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 3. 解析数据：blogId & 时间戳
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 本次查询到的最小时间戳
        int os = 1;       // 偏移量（处理同分数情况）
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 4. 按顺序查数据库中的 Blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();

        // 5. 封装 Blog 详情（比如作者信息、是否点赞）
        for (Blog blog : blogs) {
            // 查询作者
            User user = userService.getById(blog.getUserId());
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            // 是否点赞（略，可以根据需求实现）
        }

        // 6. 封装返回，带上下一次查询需要的 minTime 和 os
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }


}
