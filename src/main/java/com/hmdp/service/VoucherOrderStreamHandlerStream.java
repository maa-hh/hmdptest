package com.hmdp.service;
import com.hmdp.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
@Component
@Slf4j
public class VoucherOrderStreamHandlerStream implements InitializingBean {
    @Autowired
    RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate redisTemplate;
    @Resource
    private IVoucherOrderService voucherOrderService;

    private static final String STREAM_KEY = "stream:orders";
    private static final String GROUP_NAME = "orderGroup";
    private static final String CONSUMER_NAME = "consumer1";

    @Override
    public void afterPropertiesSet() {
        initConsumerGroup();
        Executors.newSingleThreadExecutor().submit(this::handleStream);
    }

    private void initConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, GROUP_NAME);
        } catch (Exception e) {
            log.warn("Stream Group 可能已存在: {}", e.getMessage());
        }
    }

    private void handleStream() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> messages =
                        redisTemplate.opsForStream().read(
                                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                        );

                if (messages == null || messages.isEmpty()) continue;

                for (MapRecord<String, Object, Object> msg : messages) {
                    handleMessage(msg);
                    // 确认消息
                    redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, msg.getId());
                }

            } catch (Exception e) {
                log.error("Stream 消费异常", e);
            }
        }
    }

    private void handleMessage(MapRecord<String, Object, Object> msg) {
        Long userId = Long.valueOf((String) msg.getValue().get("userId"));
        Long voucherId = Long.valueOf((String) msg.getValue().get("voucherId"));
        Long orderId = Long.valueOf((String) msg.getValue().get("orderId"));

        // Redis 分布式锁（防止同一用户并发下单）
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = false;
        try {
            isLock = lock.tryLock(500, 3000, TimeUnit.MILLISECONDS);
            if (!isLock) {
                log.warn("用户{}获取锁失败", userId);
                return;
            }
            // 保存订单
            VoucherOrder order = new VoucherOrder();
            order.setId(orderId);
            order.setUserId(userId);
            order.setVoucherId(voucherId);
            voucherOrderService.save(order);
        } catch (Exception e) {
            log.error("创建订单失败", e);
        } finally {
            if (isLock) lock.unlock();
        }
    }
}

