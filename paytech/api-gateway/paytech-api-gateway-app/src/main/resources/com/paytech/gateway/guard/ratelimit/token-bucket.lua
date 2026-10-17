local MAX_SAFE_INTEGER = 9007199254740991
local MINIMUM_TTL_MILLIS = 60000
local MAX_IDENTITY_BYTES = 512
local ARGUMENTS_PER_BUCKET = 4
local HASH_FIELD_COUNT = 3
local GUARD_V3_MARKER = ':guard:v3:'

local function fail(code)
    return redis.error_reply('TOKEN_BUCKET_' .. code)
end

local function parse_positive_integer(value)
    if type(value) ~= 'string' or not string.match(value, '^%d+$') then
        return nil
    end
    local number = tonumber(value)
    if not number or number <= 0 or number > MAX_SAFE_INTEGER or number ~= math.floor(number) then
        return nil
    end
    return number
end

local function parse_non_negative_integer(value)
    if type(value) ~= 'string' or not string.match(value, '^%d+$') then
        return nil
    end
    local number = tonumber(value)
    if not number or number < 0 or number > MAX_SAFE_INTEGER or number ~= math.floor(number) then
        return nil
    end
    return number
end

local function split_colon(value)
    local segments = {}
    local start_index = 1
    while true do
        local separator = string.find(value, ':', start_index, true)
        if not separator then
            segments[#segments + 1] = string.sub(value, start_index)
            return segments
        end
        segments[#segments + 1] = string.sub(value, start_index, separator - 1)
        start_index = separator + 1
    end
end

local function valid_kebab(value)
    return type(value) == 'string' and #value > 0
        and string.match(value, '^[a-z0-9%-]+$')
        and string.sub(value, 1, 1) ~= '-'
        and string.sub(value, -1) ~= '-'
        and not string.find(value, '--', 1, true)
end

local function valid_namespace(value)
    if type(value) ~= 'string' or #value == 0 or #value > 128 then
        return false
    end
    local segments = split_colon(value)
    for index = 1, #segments do
        if not valid_kebab(segments[index]) then
            return false
        end
        if index > 1 and segments[index - 1] == 'guard'
                and string.match(segments[index], '^v%d+$') then
            return false
        end
    end
    return true
end

local function continuation(byte)
    return byte and byte >= 128 and byte <= 191
end

local function java_whitespace(codepoint)
    return codepoint == 32 or codepoint == 5760
        or (codepoint >= 8192 and codepoint <= 8198)
        or (codepoint >= 8200 and codepoint <= 8202)
        or codepoint == 8232 or codepoint == 8233
        or codepoint == 8287 or codepoint == 12288
end

local function valid_raw_identity(value)
    if type(value) ~= 'string' or #value == 0 or #value > MAX_IDENTITY_BYTES then
        return false
    end
    local index = 1
    local has_non_whitespace = false
    while index <= #value do
        local first = string.byte(value, index)
        local codepoint
        if first <= 127 then
            codepoint = first
            index = index + 1
        elseif first >= 194 and first <= 223 then
            local second = string.byte(value, index + 1)
            if not continuation(second) then
                return false
            end
            codepoint = (first - 192) * 64 + second - 128
            index = index + 2
        elseif first >= 224 and first <= 239 then
            local second = string.byte(value, index + 1)
            local third = string.byte(value, index + 2)
            local second_valid = continuation(second)
                and (first ~= 224 or second >= 160)
                and (first ~= 237 or second <= 159)
            if not second_valid or not continuation(third) then
                return false
            end
            codepoint = (first - 224) * 4096 + (second - 128) * 64 + third - 128
            index = index + 3
        elseif first >= 240 and first <= 244 then
            local second = string.byte(value, index + 1)
            local third = string.byte(value, index + 2)
            local fourth = string.byte(value, index + 3)
            local second_valid = continuation(second)
                and (first ~= 240 or second >= 144)
                and (first ~= 244 or second <= 143)
            if not second_valid or not continuation(third) or not continuation(fourth) then
                return false
            end
            codepoint = (first - 240) * 262144 + (second - 128) * 4096
                + (third - 128) * 64 + fourth - 128
            index = index + 4
        else
            return false
        end
        if codepoint <= 31 or (codepoint >= 127 and codepoint <= 159) then
            return false
        end
        if not java_whitespace(codepoint) then
            has_non_whitespace = true
        end
    end
    return has_non_whitespace
end

local function rate_dimension_code(value, expected_namespace)
    if type(value) ~= 'string' then
        return false
    end
    local marker_index = string.find(value, GUARD_V3_MARKER, 1, true)
    if not marker_index or marker_index <= 1 then
        return false
    end
    local namespace = string.sub(value, 1, marker_index - 1)
    if namespace ~= expected_namespace or not valid_namespace(namespace) then
        return false
    end

    local segment_start = marker_index + #GUARD_V3_MARKER
    local route_end = string.find(value, ':', segment_start, true)
    if not route_end then
        return false
    end
    local route = string.sub(value, segment_start, route_end - 1)
    local policy_start = route_end + 1
    local policy_end = string.find(value, ':', policy_start, true)
    if not policy_end then
        return false
    end
    local policy = string.sub(value, policy_start, policy_end - 1)
    local dimension_start = policy_end + 1
    local dimension_end = string.find(value, ':', dimension_start, true)
    if not dimension_end then
        return false
    end
    local dimension = string.sub(value, dimension_start, dimension_end - 1)
    local identity = string.sub(value, dimension_end + 1)

    if not valid_kebab(route) or policy ~= 'rate' then
        return false
    end
    if dimension == 'shared-route' then
        return identity == 'shared' and 1 or nil
    end
    if not valid_raw_identity(identity) then
        return nil
    end
    if dimension == 'client-ip' then
        return 2
    end
    if dimension == 'authenticated-subject' then
        return 3
    end
    return dimension == 'tenant' and 4 or nil
end

local function ceil_divide(numerator, denominator)
    local remainder = numerator % denominator
    local quotient = (numerator - remainder) / denominator
    return quotient + (remainder == 0 and 0 or 1)
end

local function floor_divide(numerator, denominator)
    local remainder = numerator % denominator
    return (numerator - remainder) / denominator
end

local function derive_policy(capacity, refill, period)
    if capacity > math.floor(MAX_SAFE_INTEGER / period) then
        return nil, nil, 'CAPACITY_PERIOD_OVERFLOW'
    end
    local empty_to_full = ceil_divide(capacity * period, refill)
    if empty_to_full > math.floor(MAX_SAFE_INTEGER / refill) then
        return nil, nil, 'REFILL_PRODUCT_OVERFLOW'
    end
    local maximum_numerator = empty_to_full * refill
    if maximum_numerator > MAX_SAFE_INTEGER - (period - 1) then
        return nil, nil, 'REFILL_NUMERATOR_OVERFLOW'
    end
    if empty_to_full > math.floor(MAX_SAFE_INTEGER / 2) then
        return nil, nil, 'TTL_OVERFLOW'
    end
    return empty_to_full, math.max(MINIMUM_TTL_MILLIS, empty_to_full * 2), nil
end

if #KEYS == 0 or #ARGV ~= 1 + #KEYS * ARGUMENTS_PER_BUCKET then
    return fail('INVALID_ARGUMENT_COUNT')
end
local configured_namespace = ARGV[1]
if not valid_namespace(configured_namespace) then
    return fail('INVALID_NAMESPACE')
end

-- 阶段一：读取 Bucket 前先完整校验调用参数。
local seen_keys = {}
local buckets = {}
for index = 1, #KEYS do
    local key = KEYS[index]
    local dimension_code = rate_dimension_code(key, configured_namespace)
    if not dimension_code then
        return fail('INVALID_KEY')
    end
    if seen_keys[key] then
        return fail('DUPLICATE_KEY')
    end
    seen_keys[key] = true

    local argument_index = 1 + (index - 1) * ARGUMENTS_PER_BUCKET
    local capacity = parse_positive_integer(ARGV[argument_index + 1])
    local refill = parse_positive_integer(ARGV[argument_index + 2])
    local period = parse_positive_integer(ARGV[argument_index + 3])
    local requested = parse_positive_integer(ARGV[argument_index + 4])
    if not capacity or not refill or not period or not requested or requested > capacity then
        return fail('INVALID_NUMBER')
    end
    local empty_to_full, ttl, policy_error = derive_policy(capacity, refill, period)
    if policy_error then
        return fail(policy_error)
    end
    buckets[index] = {
        key = key,
        capacity = capacity,
        refill = refill,
        period = period,
        empty_to_full = empty_to_full,
        ttl = ttl,
        requested = requested,
        dimension_code = dimension_code,
        exists = false
    }
end

-- 阶段二：先加载并校验全部精确 v3 Hash，再进行任何本地计算。
for index = 1, #buckets do
    local bucket = buckets[index]
    local key_type = redis.call('TYPE', bucket.key)['ok']
    if key_type == 'hash' then
        if redis.call('HLEN', bucket.key) ~= HASH_FIELD_COUNT then
            return fail('INVALID_EXISTING_HASH')
        end
        local values = redis.call('HMGET', bucket.key, 't', 'lr', 'rr')
        local tokens = parse_non_negative_integer(values[1])
        local last_refill_millis = parse_non_negative_integer(values[2])
        local refill_remainder = parse_non_negative_integer(values[3])
        if not tokens or not last_refill_millis or not refill_remainder then
            return fail('INVALID_EXISTING_VALUE')
        end
        bucket.exists = true
        -- Snapshot 热更新不换 Redis Key：降容时立即保守截断，升容时不凭空补发 Token。
        bucket.tokens = math.min(tokens, bucket.capacity)
        bucket.last_refill_millis = last_refill_millis
        -- Refill Period 变短时旧余数没有可安全换算的策略元数据，清零而不是令请求 503。
        bucket.refill_remainder = refill_remainder < bucket.period and refill_remainder or 0
    elseif key_type ~= 'none' then
        return fail('INVALID_EXISTING_TYPE')
    end
end

local redis_time = redis.call('TIME')
local seconds = parse_non_negative_integer(redis_time[1])
local microseconds = parse_non_negative_integer(redis_time[2])
if not seconds or not microseconds or microseconds >= 1000000
        or seconds > math.floor(MAX_SAFE_INTEGER / 1000) then
    return fail('INVALID_REDIS_TIME')
end
local base_millis = seconds * 1000
local millisecond_fraction = floor_divide(microseconds, 1000)
if base_millis > MAX_SAFE_INTEGER - millisecond_fraction then
    return fail('REDIS_TIME_OVERFLOW')
end
local now_millis = base_millis + millisecond_fraction

local function refill_existing(bucket, effective_now)
    local elapsed = effective_now - bucket.last_refill_millis
    if elapsed > bucket.empty_to_full then
        elapsed = bucket.empty_to_full
    end
    local numerator = elapsed * bucket.refill + bucket.refill_remainder
    if numerator > MAX_SAFE_INTEGER then
        return 'RUNTIME_NUMERATOR_OVERFLOW'
    end
    local added = floor_divide(numerator, bucket.period)
    local remainder = numerator % bucket.period
    local capacity_remaining = bucket.capacity - bucket.tokens
    if added >= capacity_remaining then
        bucket.tokens = bucket.capacity
        bucket.refill_remainder = 0
    else
        bucket.tokens = bucket.tokens + added
        bucket.refill_remainder = remainder
    end
    bucket.last_refill_millis = effective_now
    return nil
end

-- 阶段二（续）：用当前 Snapshot 参数保守收敛运行中状态。
for index = 1, #buckets do
    local bucket = buckets[index]
    if bucket.exists then
        local effective_now = math.max(now_millis, bucket.last_refill_millis)
        local refill_error = refill_existing(bucket, effective_now)
        if refill_error then
            return fail(refill_error)
        end
    else
        bucket.tokens = bucket.capacity
        bucket.last_refill_millis = now_millis
        bucket.refill_remainder = 0
    end
end

-- 阶段三：决定全部 Bucket，尚不扣减或写入。
local allowed = true
local retry_after_seconds = 0
local rejecting_dimension_code = 0
for index = 1, #buckets do
    local bucket = buckets[index]
    if bucket.tokens < bucket.requested then
        allowed = false
        local missing = bucket.requested - bucket.tokens
        local required_numerator = missing * bucket.period - bucket.refill_remainder
        local wait_millis = ceil_divide(required_numerator, bucket.refill)
        local wait_seconds = math.max(1, ceil_divide(wait_millis, 1000))
        if wait_seconds > retry_after_seconds then
            retry_after_seconds = wait_seconds
            rejecting_dimension_code = bucket.dimension_code
        end
    end
end

if allowed then
    for index = 1, #buckets do
        local bucket = buckets[index]
        bucket.tokens = bucket.tokens - bucket.requested
    end
end

-- 阶段四：持久化全部有效状态，仅在全部允许时扣减。
for index = 1, #buckets do
    local bucket = buckets[index]
    redis.call('HSET', bucket.key,
        't', bucket.tokens,
        'lr', bucket.last_refill_millis,
        'rr', bucket.refill_remainder)
    redis.call('PEXPIRE', bucket.key, bucket.ttl)
end

if allowed then
    return {1, 0, 0}
end
return {0, retry_after_seconds, rejecting_dimension_code}
