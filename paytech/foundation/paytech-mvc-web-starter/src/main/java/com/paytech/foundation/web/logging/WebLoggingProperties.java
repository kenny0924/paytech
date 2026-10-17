package com.paytech.foundation.web.logging;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 按应用内路径划定禁止缓存和记录内容的日志边界。
 *
 * @param metadataOnlyPathPrefixes 不含 Servlet Context Path 的固定路径前缀，空列表保留普通端点完整日志
 * @author Codex
 */
@ConfigurationProperties(prefix = "web.logging", ignoreUnknownFields = false)
public record WebLoggingProperties(List<String> metadataOnlyPathPrefixes) {

    private static final Pattern CANONICAL_PATH = Pattern.compile("/[A-Za-z0-9/._~-]*");

    public WebLoggingProperties {
        metadataOnlyPathPrefixes = metadataOnlyPathPrefixes == null ? List.of() : metadataOnlyPathPrefixes;
        for (String prefix : metadataOnlyPathPrefixes) {
            if (!isCanonicalPath(prefix) || (prefix.length() > 1 && prefix.endsWith("/"))) {
                throw new IllegalArgumentException("web.logging.metadata-only-path-prefixes 必须是无尾斜线的应用内固定绝对路径");
            }
        }
        metadataOnlyPathPrefixes = List.copyOf(metadataOnlyPathPrefixes);
    }

    static boolean isCanonicalPath(String path) {
        if (path == null || !CANONICAL_PATH.matcher(path).matches() || path.contains("//")) {
            return false;
        }
        return Arrays.stream(path.split("/"))
            .noneMatch(segment -> segment.equals(".") || segment.equals(".."));
    }
}
