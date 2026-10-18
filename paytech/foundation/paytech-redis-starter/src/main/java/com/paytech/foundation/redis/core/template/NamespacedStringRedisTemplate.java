package com.paytech.foundation.redis.core.template;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.query.SortQuery;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;
import com.paytech.foundation.redis.core.serialization.RedisKeyPrefixSerializer;

/**
 * 自动隔离服务 Key 空间的 StringRedisTemplate。
 *
 * @author Codex
 */
public final class NamespacedStringRedisTemplate extends StringRedisTemplate {

    private final String mappedPrefix;

    public NamespacedStringRedisTemplate(RedisConnectionFactory connectionFactory, String keyPrefix) {
        this.mappedPrefix = keyPrefix + ":";
        setConnectionFactory(connectionFactory);
        setKeySerializer(new RedisKeyPrefixSerializer(keyPrefix));
        afterPropertiesSet();
    }

    @Override
    public Cursor<String> scan(ScanOptions options) {
        return super.scan(addNamespace(options));
    }

    @Override
    public String randomKey() {
        throw unsupportedOperation("服务级Key隔离模式不支持randomKey，请按业务Namespace使用scan");
    }

    @Override
    public List<String> sort(SortQuery<String> query) {
        validateSortQuery(query);
        return super.sort(query);
    }

    @Override
    public <T> List<T> sort(SortQuery<String> query, RedisSerializer<T> resultSerializer) {
        validateSortQuery(query);
        return super.sort(query, resultSerializer);
    }

    @Override
    public <T> List<T> sort(SortQuery<String> query, BulkMapper<T, String> bulkMapper) {
        validateSortQuery(query);
        return super.sort(query, bulkMapper);
    }

    @Override
    public <T, S> List<T> sort(SortQuery<String> query, BulkMapper<T, S> bulkMapper,
        RedisSerializer<S> resultSerializer) {
        validateSortQuery(query);
        return super.sort(query, bulkMapper, resultSerializer);
    }

    @Override
    public Long sort(SortQuery<String> query, String storeKey) {
        validateSortQuery(query);
        return super.sort(query, storeKey);
    }

    private ScanOptions addNamespace(ScanOptions options) {
        if (options == null) {
            throw new FailException(RedisErrorCode.CONFIGURATION_INVALID, "ScanOptions不能为空");
        }
        ScanOptions.ScanOptionsBuilder builder = ScanOptions.scanOptions();
        if (options.getCount() != null) {
            builder.count(options.getCount());
        }
        applyPattern(builder, options);
        applyDataType(builder, options);
        return builder.build();
    }

    private void applyPattern(ScanOptions.ScanOptionsBuilder builder, ScanOptions options) {
        if (options.getBytePattern() != null) {
            builder.match(prefixBytes(options.getBytePattern()));
            return;
        }
        String pattern = options.getPattern() == null ? "*" : options.getPattern();
        builder.match(mappedPrefix + pattern);
    }

    private void applyDataType(ScanOptions.ScanOptionsBuilder builder, ScanOptions options) {
        if (!(options instanceof KeyScanOptions keyScanOptions) || keyScanOptions.getType() == null) {
            return;
        }
        builder.type(DataType.fromCode(keyScanOptions.getType()));
    }

    private byte[] prefixBytes(byte[] pattern) {
        byte[] prefix = mappedPrefix.getBytes(StandardCharsets.UTF_8);
        byte[] namespacedPattern = new byte[prefix.length + pattern.length];
        System.arraycopy(prefix, 0, namespacedPattern, 0, prefix.length);
        System.arraycopy(pattern, 0, namespacedPattern, prefix.length, pattern.length);
        return namespacedPattern;
    }

    private void validateSortQuery(SortQuery<String> query) {
        if (query == null) {
            throw new FailException(RedisErrorCode.CONFIGURATION_INVALID, "SortQuery不能为空");
        }
        boolean hasExternalByPattern = query.getBy() != null && !"nosort".equalsIgnoreCase(query.getBy());
        boolean hasExternalGetPattern = query.getGetPattern() != null && !query.getGetPattern().isEmpty();
        if (hasExternalByPattern || hasExternalGetPattern) {
            throw unsupportedOperation("服务级Key隔离模式不支持SORT BY/GET跨Key模式");
        }
    }

    private FailException unsupportedOperation(String message) {
        return new FailException(RedisErrorCode.OPERATION_UNSUPPORTED, message);
    }
}
