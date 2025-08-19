package com.hmdp.service.impl;

import cn.hutool.core.io.resource.ClassPathResource;
import com.baomidou.mybatisplus.annotation.Version;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.VoucherOrderHandler;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl
        extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    VoucherOrderHandler orderHandler;

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//
//        // 一人一单 + 并发安全（先锁用户）分布式时会失效，单机有效
//        synchronized (userId.toString().intern()) {
//            // 再次校验一人一单（防止并发绕过）
//            int count = query().eq("user_id", userId)
//                    .eq("voucher_id", voucherId).count();
//            if (count > 0) {
//                return Result.fail("一人只能购买一次");
//            }
//
//            // 校验秒杀时间
//            SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//            if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//                return Result.fail("活动尚未开始");
//            }
//            if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//                return Result.fail("活动已结束");
//            }
//
//            // 扣减库存（乐观锁，非事务）
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1")
//                    .eq("voucher_id", voucherId).gt("stock", 0)
//                    .update();
//            if (!success) {
//                return Result.fail("库存不足");
//            }
//
//            // 创建订单（事务保证一致性）
//            /*
//            * @Transactional 的“魔法”只发生在 “经过代理”的方法边界 上；同类内部的 this.xxx() 调用绕过了代理，自然也就“绕过了事务”。
//            * 外部类调用时自动生成代理调用，不会失效
//            * */
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            VoucherOrder voucherOrder = proxy.createVoucherOrder(voucherId, userId);
//            return Result.ok(voucherOrder.getId());
//        }
//    }

//public Result seckillVoucher(Long voucherId) {
//    Long userId = UserHolder.getUser().getId();
//
//        // 校验秒杀时间
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//    if (voucher == null) {
//        return Result.fail("秒杀券不存在");
//    }
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("活动尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("活动已结束");
//        }
//    // 一人一单 + 并发安全（先锁用户）分布式时会失效，单机有效
//
//    // 再次校验一人一单（防止并发绕过）
//    int count = query().eq("user_id", userId)
//            .eq("voucher_id", voucherId).count();
//    if (count > 0) {
//        return Result.fail("一人只能购买一次");
//    }
//    RedisLock redisLock=new RedisLock(redisTemplate,"order:"+userId.toString());
//        //boolean lock=redisLock.tryLock(1200);
//    //Redisson可重入锁
//    RLock Rlock=redissonClient.getLock("lock:order:"+userId.toString());
//    boolean lock=Rlock.tryLock();
//        if(!lock){
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            // 扣减库存（乐观锁，非事务）
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1")
//                    .eq("voucher_id", voucherId).gt("stock", 0)
//                    .update();
//            if (!success) {
//                return Result.fail("库存不足");
//            }
//
//            // 创建订单（事务保证一致性）
//            /*
//             * @Transactional 的“魔法”只发生在 “经过代理”的方法边界 上；同类内部的 this.xxx() 调用绕过了代理，自然也就“绕过了事务”。
//             * 外部类调用时自动生成代理调用，不会失效
//             * */
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            VoucherOrder voucherOrder = proxy.createVoucherOrder(voucherId, userId);
//            return Result.ok(voucherOrder.getId());
//        }
//       finally {
//            //redisLock.unlock();
//            Rlock.unlock();
//        }
//}
@Override
public Result seckillVoucher(Long voucherId) {
    Long userId = UserHolder.getUser().getId();

    // 校验秒杀券和时间
    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    if (voucher == null) return Result.fail("秒杀券不存在");
    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) return Result.fail("活动尚未开始");
    if (voucher.getEndTime().isBefore(LocalDateTime.now())) return Result.fail("活动已结束");

    // 生成订单ID
    long orderId = redisIdWorker.nextId("order");

    // 执行 Lua
    String stockKey = "seckill:stock:" + voucherId;
    String orderKey = "seckill:order:" + voucherId;
    String streamKey = "stream:orders";

    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
    redisScript.setLocation((Resource) new ClassPathResource("lua/seckill.lua"));
    redisScript.setResultType(Long.class);

    Long result = redisTemplate.execute(
            redisScript,
            Arrays.asList(stockKey, orderKey, streamKey),
            userId.toString(),
            voucherId.toString(),
            String.valueOf(orderId)
    );

    if (result == 1L) return Result.fail("库存不足");
    if (result == 2L) return Result.fail("不能重复下单");

    // 立即返回订单ID
    return Result.ok(orderId);
}


    @Transactional
    public VoucherOrder createVoucherOrder(VoucherOrder voucherOrder) {
        // 1. 再次校验一人一单（防止并发绕过）
        int count = query()
                .eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if (count > 0) {
            throw new RuntimeException("一人只能购买一次");
        }

        // 2. 扣减库存（乐观锁，非事务）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")   // stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId())  // where voucher_id = ?
                .gt("stock", 0)               // and stock > 0
                .update();
        if (!success) {
            throw new RuntimeException("库存不足");
        }
//        // 1. 先查询当前版本
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        int currentVersion = voucher.getVersion();
//
//// 2. 扣减库存 + 乐观锁更新
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")         // 扣减库存
//                .eq("voucher_id", voucherId)         // 条件 voucher_id
//                .eq("version", currentVersion)       // 乐观锁条件 version = 当前版本
//                .update();
//        有专门的@Version 注解
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .update();
//        synchronized(userId.toString().intern()) {
//            // 处理同一个用户的下单逻辑
//            intern() 的作用就是让相同内容的字符串引用唯一，从而可以作为 synchronized 的锁对象，保证单机环境下同一资源的互斥访问。
//        }
        // 3. 创建订单

        save(voucherOrder);

        return voucherOrder;
    }

}

