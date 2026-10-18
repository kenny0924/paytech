-- KEYS[1] 幂等记录 Hash；ARGV[1] 请求摘要，ARGV[2] 持有者 UUID，ARGV[3] 待定租约毫秒数。
if #KEYS ~= 1 or #ARGV ~= 3 then
    return redis.error_reply('IDEMPOTENCY_CLAIM_INVALID_ARGUMENT_COUNT')
end
local lease_millis = tonumber(ARGV[3])
if not lease_millis or lease_millis <= 0 then
    return redis.error_reply('IDEMPOTENCY_CLAIM_INVALID_LEASE')
end

local state = redis.call('HGET', KEYS[1], 'state')
if not state then
    local now = redis.call('TIME')
    local accepted_at = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
    redis.call('HSET', KEYS[1], 'state', 'PENDING', 'digest', ARGV[1], 'owner', ARGV[2],
        'acceptedAt', string.format('%.0f', accepted_at))
    redis.call('PEXPIRE', KEYS[1], string.format('%.0f', lease_millis))
    return {'CLAIMED'}
end
if redis.call('HGET', KEYS[1], 'digest') ~= ARGV[1] then
    return {'KEY_REUSED'}
end
if state == 'PENDING' then
    return {'IN_PROGRESS'}
end
if state == 'COMPLETED' then
    local record = redis.call('HMGET', KEYS[1], 'status', 'headers', 'body')
    if not record[1] or not record[2] or not record[3] then
        return redis.error_reply('IDEMPOTENCY_CLAIM_INCOMPLETE_RECORD')
    end
    return {'COMPLETED', record[1], record[2], record[3]}
end
return redis.error_reply('IDEMPOTENCY_CLAIM_INVALID_STATE')
