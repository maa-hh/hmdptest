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
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();   // 1. 获取当前登录用户 ID
        LocalDateTime now = LocalDateTime.now();      // 2. 获取当前时间

        // 3. 拼接 Redis key，区分用户和月份
        String key = "user:sign:" + userId + ":" + now.getYear() + ":" + now.getMonthValue();
        int dayOfMonth = now.getDayOfMonth();         // 4. 今天是几号，比如 8月20号 → 20
        // 5. 在 Redis 的 "位图" 里，把今天的位置标记为 1，表示已签到
        redisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }



    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = "user:sign:" + userId + ":" + now.getYear() + ":" + now.getMonthValue();
        int dayOfMonth = now.getDayOfMonth();

        // 取本月截至今天的签到数据
        List<Long> result = redisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)) // 取 dayOfMonth 位
                        .valueAt(0) // 从第0位开始
        );

        if (result == null || result.isEmpty() || result.get(0) == null) {
            return Result.ok(0);
        }
        //将签到情况转为long
        long num = result.get(0);

        // 统计连续签到天数（从今天往前）
        int count = 0;
        for (int i = 0; i < dayOfMonth; i++) {
            if ((num & 1) == 0) {
                break; // 遇到 0 表示中断
            } else {
                count++;
            }
            num >>= 1; // 右移一位，继续检查前一天
        }

        return Result.ok(count);
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