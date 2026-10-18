-- KEYS[1] 幂等记录 Hash；ARGV[1] 持有者 UUID，ARGV[2] 状态码，ARGV[3] 响应头 JSON，ARGV[4] gzip 响应体，ARGV[5] 保留毫秒数。
if #KEYS ~= 1 or #ARGV ~= 5 then
    return redis.error_reply('IDEMPOTENCY_COMPLETE_INVALID_ARGUMENT_COUNT')
end
local retention_millis = tonumber(ARGV[5])
if not retention_millis or retention_millis <= 0 then
    return redis.error_reply('IDEMPOTENCY_COMPLETE_INVALID_RETENTION')
end

local current = redis.call('HMGET', KEYS[1], 'state', 'owner', 'acceptedAt')
if current[1] ~= 'PENDING' or current[2] ~= ARGV[1] then
    return {'STALE'}
end
local accepted_at = tonumber(current[3])
if not accepted_at then
    return redis.error_reply('IDEMPOTENCY_COMPLETE_INVALID_ACCEPTED_AT')
end
local now = redis.call('TIME')
local completed_at = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
redis.call('HSET', KEYS[1], 'state', 'COMPLETED', 'status', ARGV[2], 'headers', ARGV[3], 'body', ARGV[4],
    'completedAt', string.format('%.0f', completed_at))
redis.call('PEXPIREAT', KEYS[1], string.format('%.0f', accepted_at + retention_millis))
return {'COMPLETED'}
