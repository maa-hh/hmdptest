package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    StringRedisTemplate redisTemplate;

    /*@Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号无效");
        }
        // 生成 6 位随机数字（不足补 0）
        //String code = String.format("%06d", random.nextInt(1000000));
        String code=RandomUtil.randomString(6);
        // 可以存到 session 里
        session.setAttribute("code", code);

        // 返回成功并附带验证码（实际开发中这里一般发短信，不会直接返回）
        return Result.ok(code);
    }*/
    @Override
    public Result sendCode(String phone) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号无效");
        }
        // 生成 6 位随机数字（不足补 0）
        //String code = String.format("%06d", random.nextInt(1000000));
        String code = RandomUtil.randomString(6);
        // 可以存到 session 里
        redisTemplate.opsForValue().set("login:code:"+phone, code, 2, TimeUnit.MINUTES);
        log.debug("验证码发送成功:"+code);
        // 返回成功并附带验证码（实际开发中这里一般发短信，不会直接返回）
        return Result.ok(code);
    }
    @Override
    public Result login(LoginFormDTO loginForm){
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号有误");
        }
        String code=loginForm.getCode();
        //Object s=session.getAttribute("code");
        Object s=redisTemplate.opsForValue().get("login:code:"+phone);
        if(s==null||s.toString().equals(code)==false){
            return Result.fail("输入有误");
        }
        User user=query().eq("phone",phone).one();
        if(user==null){
            user=createUserWithPhone(phone);
        }
        String token= UUID.randomUUID().toString();
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        UserDTO userDTO=BeanUtil.copyProperties(user, UserDTO.class);
        Map<String,Object> m = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setFieldValueEditor((fieldName, fieldValue) -> {
                    return fieldValue == null ? "null" : fieldValue.toString();
                }));
        redisTemplate.opsForHash().putAll("login:"+token,m);
        redisTemplate.expire("login:"+token, 30, TimeUnit.MINUTES);
        UserHolder.saveUser(userDTO);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 保存到数据库
        boolean success = save(user); // 调用ServiceImpl的save方法
        if(!success){
            log.error("用户保存失败: {}", user);
            return null;
        }

        log.info("用户保存成功，ID: {}", user.getId());
        return user;
    }


}