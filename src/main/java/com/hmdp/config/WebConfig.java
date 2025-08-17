package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor1;
import com.hmdp.interceptor.LoginInterceptor2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebConfig implements WebMvcConfigurer {
    @Autowired
    StringRedisTemplate redisTemplate ;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor1(redisTemplate))
                .addPathPatterns("/**").order(0); // 所有请求先经过 LoginInterceptor1
        registry.addInterceptor(new LoginInterceptor2( redisTemplate))
                .excludePathPatterns(
                        "/user/login",      // 登录接口
                        "/user/code",
                        "/shop/**",   // 注册接口
                        "/error"       // 错误页
                ) .order(1);
    }
}
