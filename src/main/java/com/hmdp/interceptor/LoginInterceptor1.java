package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor1 implements HandlerInterceptor {
    StringRedisTemplate redisTemplate;
    public LoginInterceptor1(StringRedisTemplate redisTemplate){
        this.redisTemplate=redisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token=request.getHeader("authorization");
        //Object user=session.getAttribute("user");
        if(StrUtil.isBlank(token)){
            //response.setStatus(401); // 未授权
            return true;
        }
        Map<Object,Object> user=redisTemplate.opsForHash().entries("login:"+token);
        // 从 ThreadLocal 中尝试获取用户
        if (user == null) {
            // 如果用户为空，说明未登录
            response.setStatus(401); // 未授权
            return false;
        }
        UserDTO u= BeanUtil.fillBeanWithMap(user,new UserDTO(),false);
        redisTemplate.expire("login:"+token,30, TimeUnit.MINUTES);
        UserHolder.saveUser(u);
        return true; // 已登录，放行
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束，清理 ThreadLocal
        UserHolder.removeUser();
    }
}
