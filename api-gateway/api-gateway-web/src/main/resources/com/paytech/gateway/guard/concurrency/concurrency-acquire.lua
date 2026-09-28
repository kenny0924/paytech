local MAX_SAFE_INTEGER = 9007199254740991
local MAX_IDENTITY_BYTES = 512
local ARGUMENTS_PER_DIMENSION = 3
local METADATA_FIELD_COUNT = 2
local LEASE_SUFFIX = ':leases'
local METADATA_SUFFIX = ':metadata'
local GUARD_V3_MARKER = ':guard:v3:'

local function fail(code)
    return redis.error_reply('CONCURRENCY_ACQUIRE_' .. code)
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

local function valid_uuid(value)
    return type(value) == 'string' and #value == 36
        and string.match(value, '^[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]%-[0-9a-f][0-9a-f][0-9a-f][0-9a-f]%-[0-9a-f][0-9a-f][0-9a-f][0-9a-f]%-[0-9a-f][0-9a-f][0-9a-f][0-9a-f]%-[0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]$')
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

local function canonical_base(value, role_suffix, expected_namespace)
    if type(value) ~= 'string' or #value <= #role_suffix
            or string.sub(value, #value - #role_suffix + 1) ~= role_suffix then
        return nil
    end
    local base_key = string.sub(value, 1, #value - #role_suffix)
    local marker_index = string.find(base_key, GUARD_V3_MARKER, 1, true)
    if not marker_index or marker_index <= 1 then
        return nil
    end
    local namespace = string.sub(base_key, 1, marker_index - 1)
    if namespace ~= expected_namespace or not valid_namespace(namespace) then
        return nil
    end

    local segment_start = marker_index + #GUARD_V3_MARKER
    local route_end = string.find(base_key, ':', segment_start, true)
    if not route_end then
        return nil
    end
    local route = string.sub(base_key, segment_start, route_end - 1)
    local policy_start = route_end + 1
    local policy_end = string.find(base_key, ':', policy_start, true)
    if not policy_end then
        return nil
    end
    local policy = string.sub(base_key, policy_start, policy_end - 1)
    local dimension_start = policy_end + 1
    local dimension_end = string.find(base_key, ':', dimension_start, true)
    if not dimension_end then
        return nil
    end
    local dimension = string.sub(base_key, dimension_start, dimension_end - 1)
    local identity = string.sub(base_key, dimension_end + 1)

    if not valid_kebab(route) or policy ~= 'concurrency' then
        return nil
    end
    if dimension == 'shared-route' then
        if identity == 'shared' then
            return base_key, 1
        end
        return nil, nil
    end
    if dimension ~= 'client-ip' and dimension ~= 'authenticated-subject' and dimension ~= 'tenant' then
        return nil, nil
    end
    if not valid_raw_identity(identity) then
        return nil, nil
    end
    if dimension == 'client-ip' then
        return base_key, 2
    end
    return base_key, dimension == 'authenticated-subject' and 3 or 4
end

local function ceil_divide(numerator, denominator)
    local remainder = numerator % denominator
    return (numerator - remainder) / denominator + (remainder == 0 and 0 or 1)
end

if #KEYS < 2 or #KEYS % 2 ~= 0 then
    return fail('INVALID_KEY_COUNT')
end
local dimension_count = #KEYS / 2
if #ARGV ~= 2 + dimension_count * ARGUMENTS_PER_DIMENSION then
    return fail('INVALID_ARGUMENT_COUNT')
end

local lease_id = ARGV[1]
if not valid_uuid(lease_id) then
    return fail('INVALID_LEASE_ID')
end
local configured_namespace = ARGV[2]
if not valid_namespace(configured_namespace) then
    return fail('INVALID_NAMESPACE')
end

-- 阶段一：先完整校验所有 Key 对与调用参数。
local seen_keys = {}
local dimensions = {}
for index = 1, dimension_count do
    local lease_key = KEYS[(index - 1) * 2 + 1]
    local metadata_key = KEYS[(index - 1) * 2 + 2]
    local base_key, dimension_code = canonical_base(lease_key, LEASE_SUFFIX, configured_namespace)
    local metadata_base, metadata_dimension_code = canonical_base(
        metadata_key, METADATA_SUFFIX, configured_namespace)
    if not base_key or not metadata_base then
        return fail('INVALID_KEY')
    end
    if metadata_base ~= base_key or metadata_dimension_code ~= dimension_code then
        return fail('INVALID_KEY_PAIR')
    end
    if seen_keys[lease_key] or seen_keys[metadata_key] then
        return fail('DUPLICATE_KEY')
    end
    seen_keys[lease_key] = true
    seen_keys[metadata_key] = true

    local argument_index = 3 + (index - 1) * ARGUMENTS_PER_DIMENSION
    local limit = parse_positive_integer(ARGV[argument_index])
    local lease_duration = parse_positive_integer(ARGV[argument_index + 1])
    local state_ttl = parse_positive_integer(ARGV[argument_index + 2])
    if not limit or not lease_duration or not state_ttl
            or lease_duration > math.floor(MAX_SAFE_INTEGER / 2)
            or state_ttl ~= lease_duration * 2 then
        return fail('INVALID_NUMBER')
    end
    dimensions[index] = {
        lease_key = lease_key,
        metadata_key = metadata_key,
        limit = limit,
        lease_duration = lease_duration,
        state_ttl = state_ttl,
        dimension_code = dimension_code,
        entries = {},
        exists = false
    }
end

-- 阶段二：在任何 mutation 与 TIME 前加载并严格校验全部现有状态。
for index = 1, #dimensions do
    local dimension = dimensions[index]
    local lease_type = redis.call('TYPE', dimension.lease_key)['ok']
    if lease_type ~= 'none' and lease_type ~= 'zset' then
        return fail('INVALID_EXISTING_LEASE_TYPE')
    end
    if lease_type == 'zset' then
        local values = redis.call('ZRANGE', dimension.lease_key, 0, -1, 'WITHSCORES')
        for value_index = 1, #values, 2 do
            local member = values[value_index]
            local score = parse_non_negative_integer(values[value_index + 1])
            if not valid_uuid(member) or not score then
                return fail('INVALID_EXISTING_LEASE')
            end
            dimension.entries[#dimension.entries + 1] = {member = member, score = score}
        end
    end

    local metadata_type = redis.call('TYPE', dimension.metadata_key)['ok']
    if metadata_type ~= 'none' and metadata_type ~= 'hash' then
        return fail('INVALID_EXISTING_METADATA_TYPE')
    end
    if metadata_type == 'hash' then
        if redis.call('HLEN', dimension.metadata_key) ~= METADATA_FIELD_COUNT then
            return fail('INVALID_EXISTING_METADATA_HASH')
        end
        local metadata = redis.call('HMGET', dimension.metadata_key, 'hw', 'l')
        local high_water = parse_non_negative_integer(metadata[1])
        local stored_limit = parse_positive_integer(metadata[2])
        if not high_water or not stored_limit then
            return fail('INVALID_EXISTING_METADATA_VALUE')
        end
        -- Snapshot 热更新复用同一 Key；旧 Limit 只用于完整性校验，当前 Limit 立即参与准入。
        dimension.exists = true
        dimension.high_water = high_water
    elseif #dimension.entries > 0 then
        return fail('MISSING_METADATA')
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
local millisecond_fraction = math.floor(microseconds / 1000)
if base_millis > MAX_SAFE_INTEGER - millisecond_fraction then
    return fail('REDIS_TIME_OVERFLOW')
end
local redis_now = base_millis + millisecond_fraction

-- 阶段三：只在 Lua 本地解析单调时间、清理结果与容量决定。
local allowed = true
local retry_after_seconds = 0
local rejecting_dimension_code = 0
for index = 1, #dimensions do
    local dimension = dimensions[index]
    dimension.effective_now = math.max(redis_now, dimension.exists and dimension.high_water or redis_now)
    if dimension.effective_now > MAX_SAFE_INTEGER - dimension.lease_duration then
        return fail('LEASE_EXPIRY_OVERFLOW')
    end

    dimension.active_entries = {}
    for entry_index = 1, #dimension.entries do
        local entry = dimension.entries[entry_index]
        if entry.score > dimension.effective_now then
            dimension.active_entries[#dimension.active_entries + 1] = entry
        end
    end
    local count = #dimension.active_entries
    if count >= dimension.limit then
        allowed = false
        local required_expiry_index = count - dimension.limit + 1
        local expiry = dimension.active_entries[required_expiry_index].score
        local wait_millis = math.min(dimension.lease_duration, expiry - dimension.effective_now)
        local wait_seconds = math.max(1, ceil_divide(wait_millis, 1000))
        if wait_seconds > retry_after_seconds then
            retry_after_seconds = wait_seconds
            rejecting_dimension_code = dimension.dimension_code
        end
    end
end

-- 阶段四：有效决定才可收敛既有状态；拒绝绝不刷新 TTL，也不创建全新 metadata。
if allowed then
    for index = 1, #dimensions do
        local dimension = dimensions[index]
        redis.call('HSET', dimension.metadata_key,
            'hw', dimension.effective_now,
            'l', dimension.limit)
        redis.call('ZREMRANGEBYSCORE', dimension.lease_key, '-inf', dimension.effective_now)
        redis.call('ZADD', dimension.lease_key,
            dimension.effective_now + dimension.lease_duration, lease_id)
        redis.call('PEXPIRE', dimension.lease_key, dimension.state_ttl)
        redis.call('PEXPIRE', dimension.metadata_key, dimension.state_ttl)
    end
    return {1, 0, 0}
end

for index = 1, #dimensions do
    local dimension = dimensions[index]
    if dimension.exists then
        redis.call('HSET', dimension.metadata_key,
            'hw', dimension.effective_now,
            'l', dimension.limit)
        redis.call('ZREMRANGEBYSCORE', dimension.lease_key, '-inf', dimension.effective_now)
    end
end
return {0, retry_after_seconds, rejecting_dimension_code}
