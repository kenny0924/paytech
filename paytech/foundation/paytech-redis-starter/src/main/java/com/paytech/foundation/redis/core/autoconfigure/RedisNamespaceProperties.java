package com.paytech.foundation.redis.core.autoconfigure;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/**
 * Redis 服务级 Key Namespace 配置。
 *
 * @author Codex
 */
@ConfigurationProperties(prefix = "redis")
public class RedisNamespaceProperties {

    private static final Pattern KEY_PREFIX_PATTERN =
        Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*(?::[a-z][a-z0-9]*(?:-[a-z0-9]+)*)*");

    /** 服务级 Key 前缀；默认使用 spring.application.name。 */
    private String keyPrefix;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 解析并校验当前服务使用的 Key 前缀。
     *
     * @param applicationName
     *            当前服务的 spring.application.name
     * @return 最终 Key 前缀
     */
    public String resolveKeyPrefix(String applicationName) {
        String resolvedKeyPrefix = resolveConfiguredValue(applicationName);
        if (resolvedKeyPrefix.length() > 255 || !KEY_PREFIX_PATTERN.matcher(resolvedKeyPrefix).matches()) {
            throw configurationError("key-prefix必须使用小写kebab-case、可使用冒号分段且不超过255个字符");
        }
        return resolvedKeyPrefix;
    }

    private String resolveConfiguredValue(String applicationName) {
        if (keyPrefix != null && !keyPrefix.isBlank()) {
            return keyPrefix.trim();
        }
        if (applicationName == null || applicationName.isBlank()) {
            throw configurationError("key-prefix未配置时spring.application.name不能为空");
        }
        return applicationName.trim();
    }

    private FailException configurationError(String message) {
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID, "redis." + message);
    }
}
