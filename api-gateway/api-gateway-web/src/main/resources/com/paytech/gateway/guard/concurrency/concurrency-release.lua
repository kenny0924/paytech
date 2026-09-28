local MAX_IDENTITY_BYTES = 512
local GUARD_V3_MARKER = ':guard:v3:'
local LEASE_SUFFIX = ':leases'

local function fail(code)
    return redis.error_reply('CONCURRENCY_RELEASE_' .. code)
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

local function valid_lease_key(value, expected_namespace)
    if type(value) ~= 'string' or #value <= #LEASE_SUFFIX
            or string.sub(value, #value - #LEASE_SUFFIX + 1) ~= LEASE_SUFFIX then
        return false
    end
    local base_key = string.sub(value, 1, #value - #LEASE_SUFFIX)
    local marker_index = string.find(base_key, GUARD_V3_MARKER, 1, true)
    if not marker_index or marker_index <= 1 then
        return false
    end
    local namespace = string.sub(base_key, 1, marker_index - 1)
    if namespace ~= expected_namespace or not valid_namespace(namespace) then
        return false
    end

    local segment_start = marker_index + #GUARD_V3_MARKER
    local route_end = string.find(base_key, ':', segment_start, true)
    if not route_end then
        return false
    end
    local route = string.sub(base_key, segment_start, route_end - 1)
    local policy_start = route_end + 1
    local policy_end = string.find(base_key, ':', policy_start, true)
    if not policy_end then
        return false
    end
    local policy = string.sub(base_key, policy_start, policy_end - 1)
    local dimension_start = policy_end + 1
    local dimension_end = string.find(base_key, ':', dimension_start, true)
    if not dimension_end then
        return false
    end
    local dimension = string.sub(base_key, dimension_start, dimension_end - 1)
    local identity = string.sub(base_key, dimension_end + 1)

    if not valid_kebab(route) or policy ~= 'concurrency' then
        return false
    end
    if dimension == 'shared-route' then
        return identity == 'shared'
    end
    return (dimension == 'client-ip' or dimension == 'authenticated-subject' or dimension == 'tenant')
        and valid_raw_identity(identity)
end

if #KEYS == 0 or #ARGV ~= 2 then
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

local seen_keys = {}
for index = 1, #KEYS do
    local key = KEYS[index]
    if not valid_lease_key(key, configured_namespace) then
        return fail('INVALID_KEY')
    end
    if seen_keys[key] then
        return fail('DUPLICATE_KEY')
    end
    seen_keys[key] = true
    local key_type = redis.call('TYPE', key)['ok']
    if key_type ~= 'none' and key_type ~= 'zset' then
        return fail('INVALID_EXISTING_TYPE')
    end
end

local removed = 0
for index = 1, #KEYS do
    removed = removed + redis.call('ZREM', KEYS[index], lease_id)
end
return {removed}
