package com.hmdp.service;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁超时时间（秒）
     * @return 是否成功获取锁
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}

