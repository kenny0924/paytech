-- KEYS[1] 幂等记录 Hash；ARGV[1] 持有者 UUID。仅删除本持有者仍处于 PENDING 的占位。
if #KEYS ~= 1 or #ARGV ~= 1 then
    return redis.error_reply('IDEMPOTENCY_RELEASE_INVALID_ARGUMENT_COUNT')
end
local current = redis.call('HMGET', KEYS[1], 'state', 'owner')
if current[1] ~= 'PENDING' or current[2] ~= ARGV[1] then
    return {'STALE'}
end
redis.call('DEL', KEYS[1])
return {'RELEASED'}
