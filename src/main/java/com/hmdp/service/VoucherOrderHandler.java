package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.*;

@Component
@Slf4j
public class VoucherOrderHandler implements InitializingBean {

    /** 阻塞队列：存放待处理的订单任务 */
    private final BlockingQueue<VoucherOrder> orderTasks = new LinkedBlockingQueue<>(1024*1024);

    /** 单线程池：串行消费队列任务 */
    private final ExecutorService orderExecutor = Executors.newSingleThreadExecutor();

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public void afterPropertiesSet() {
        // 容器启动时，提交一个任务：不断消费订单队列
        orderExecutor.submit(()->handleOrders() );
//        orderExecutor.submit(new Runnable() {
//            @Override
//            public void run() {
//                handleOrders();
//            }
//        });
    }

    /** 循环消费队列任务 */
    private void handleOrders() {
        while (true) {
            try {
                VoucherOrder task = orderTasks.take(); // 阻塞获取任务
                handleVoucherOrder(task);
            } catch (Exception e) {
                log.error("处理订单任务异常", e);
            }
        }
    }

    // submitOrder 方法
    public void submitOrder(VoucherOrder task) {
        boolean success = orderTasks.offer(task);
        if (!success) log.error("订单队列已满，拒绝任务：userId={}, voucherId={}", task.getUserId(),task.getVoucherId());
    }

    // 异步处理方法
    private void handleVoucherOrder(VoucherOrder task) {
        RLock lock = redissonClient.getLock("lock:order:" + task.getUserId());
        boolean isLock = false;
        try {
            isLock = lock.tryLock(500, 3000, TimeUnit.MILLISECONDS);
            if (!isLock) {
                log.warn("用户{}获取锁失败，跳过", task.getUserId());
                return;
            }
            voucherOrderService.createVoucherOrder(task);
        } catch (Exception e) {
            log.error("执行订单创建失败", e);
        } finally {
            if (isLock) lock.unlock();
        }
    }
}
