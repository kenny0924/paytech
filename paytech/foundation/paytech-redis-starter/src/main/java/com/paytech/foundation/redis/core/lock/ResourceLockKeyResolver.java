package com.paytech.foundation.redis.core.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import com.paytech.foundation.common.error.InvalidParameterException;

/** 对已编译绑定求值，并在任何 Redis 访问前完成全部 Key 校验。 */
public final class ResourceLockKeyResolver {

    private static final Pattern SAFE_STRING = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}");
    private static final String INVALID_MESSAGE = "资源锁参数不符合安全格式";

    public List<ResolvedResourceLock> resolve(ResourceLockMethodMetadata metadata, Object[] arguments) {
        var resolved = new ArrayList<ResolvedResourceLock>(metadata.bindings().size());
        Object[] invocationArguments = arguments == null ? new Object[0] : arguments;
        for (ResourceLockBinding binding : metadata.bindings()) {
            resolved.add(resolve(binding, invocationArguments));
        }
        var ordered = new TreeMap<String, ResolvedResourceLock>();
        for (ResolvedResourceLock lock : resolved) {
            ordered.putIfAbsent(lock.logicalKey(), lock);
        }
        return List.copyOf(ordered.values());
    }

    private ResolvedResourceLock resolve(ResourceLockBinding binding, Object[] arguments) {
        if (binding.expression().isEmpty()) {
            return new ResolvedResourceLock(binding.prefix(), binding.prefix());
        }
        Object value;
        try {
            value = binding.accessor().read(arguments);
        } catch (Throwable failure) {
            throw new InvalidParameterException(INVALID_MESSAGE, failure);
        }
        String canonical = canonical(value);
        return new ResolvedResourceLock(binding.prefix(), binding.prefix() + ":" + canonical);
    }

    private String canonical(Object value) {
        if (value instanceof String text) {
            if (!SAFE_STRING.matcher(text).matches()) {
                throw new InvalidParameterException(INVALID_MESSAGE);
            }
            return text;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return String.valueOf(value);
        }
        if (value instanceof UUID uuid) {
            return uuid.toString().toLowerCase(java.util.Locale.ROOT);
        }
        throw new InvalidParameterException(INVALID_MESSAGE);
    }
}
