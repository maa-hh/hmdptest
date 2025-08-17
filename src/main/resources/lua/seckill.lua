-- KEYS[1] = stockKey
-- KEYS[2] = orderKey
-- KEYS[3] = streamKey
-- ARGV[1] = userId
-- ARGV[2] = voucherId
-- ARGV[3] = orderId

-- 检查库存
if tonumber(redis.call('get', KEYS[1])) <= 0 then
    return 1
end

-- 检查是否重复下单
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2
end

-- 扣库存
redis.call('decr', KEYS[1])

-- 加入已下单用户集合
redis.call('sadd', KEYS[2], ARGV[1])

-- 写入 Stream
redis.call('xadd', KEYS[3], '*',
    'userId', ARGV[1],
    'voucherId', ARGV[2],
    'orderId', ARGV[3]
)

return 0
