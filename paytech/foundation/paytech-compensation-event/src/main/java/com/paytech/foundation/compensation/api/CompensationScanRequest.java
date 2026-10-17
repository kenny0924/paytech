package com.paytech.foundation.compensation.api;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 供宿主调度器或运维唤醒使用的有界扫描请求。
 *
 * @param batchSize
 *            数据库最多返回的候选记录数
 * @param shardIndex
 *            从零开始的稳定分片索引
 * @param shardCount
 *            正数分片总数
 * @param eventTypes
 *            可选事件类型白名单；为空时包含所有已持久化类型
 * @author Codex
 */
public record CompensationScanRequest(int batchSize, int shardIndex, int shardCount, Set<String> eventTypes) {

    public CompensationScanRequest {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be positive");
        }
        if (shardIndex < 0 || shardIndex >= shardCount) {
            throw new IllegalArgumentException("shardIndex must be between zero and shardCount - 1");
        }
        eventTypes = normalizeEventTypes(eventTypes);
    }

    private static Set<String> normalizeEventTypes(Set<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedTypes = new LinkedHashSet<>();
        for (String eventType : eventTypes) {
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventTypes must not contain a blank value");
            }
            String normalizedType = eventType.trim();
            if (normalizedType.length() > 64) {
                throw new IllegalArgumentException("eventType exceeds 64 characters");
            }
            normalizedTypes.add(normalizedType);
        }
        return Set.copyOf(normalizedTypes);
    }
}
