package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
    @Override
    public Result queryBlogById(Long id){
        if(id<0||id==null) return Result.fail("不存在该博客");
        Blog blog=query().eq("id",id).one();
        if(blog==null) return Result.fail("不存在该博客");
        Long userid=blog.getUserId();
        User user=userService.getById(userid);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        return  Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        return null;
    }
}
