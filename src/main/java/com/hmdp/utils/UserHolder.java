package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public class UserHolder {
    // 线程局部变量，每个线程有独立的一份 UserDTO
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    // 保存用户到当前线程
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    // 从当前线程获取用户
    public static UserDTO getUser(){
        return tl.get();
    }

    // 移除用户，避免内存泄漏
    public static void removeUser(){
        tl.remove();
    }
}

