package com.paytech.platform.support.auditsecurity.domain.audit;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import com.paytech.foundation.common.error.InvalidParameterException;

/** 已规范化的审计筛选条件；字符串采用精确匹配，空白表示不筛选。 */
public record OperationsAuditQuery(Instant occurredFrom, Instant occurredTo, String actorId,
    String actionCode, String result, String requestId, String resourceType, String resourceId) {
    private static final Set<String> RESULTS = Set.of("SUCCEEDED", "DENIED", "FAILED");

    public OperationsAuditQuery {
        if (occurredFrom != null && occurredTo != null && !occurredFrom.isBefore(occurredTo)) {
            throw new InvalidParameterException("审计开始时间必须早于结束时间");
        }
        occurredFrom = ceilToMicrosecond(occurredFrom);
        occurredTo = ceilToMicrosecond(occurredTo);
        actorId = normalize(actorId, 128, "操作者标识");
        actionCode = normalize(actionCode, 64, "动作代码");
        result = normalize(result, 64, "结果");
        requestId = normalize(requestId, 128, "请求标识");
        resourceType = normalize(resourceType, 64, "对象类型");
        resourceId = normalize(resourceId, 128, "对象标识");
        if (resourceType != null && !resourceType.matches("[A-Z][A-Z0-9_]{0,63}"))
            { throw new InvalidParameterException("审计对象类型格式不合法"); }
        if (resourceId != null && resourceType == null) { throw new InvalidParameterException("对象标识必须同时提供对象类型"); }
        if (result != null && !RESULTS.contains(result)) {
            throw new InvalidParameterException("审计结果只允许 SUCCEEDED、DENIED 或 FAILED");
        }
    }

    /** DATETIME(6) 只存在微秒刻度；两端向上取整保持 >= 下界、< 上界的匹配语义。 */
    private static Instant ceilToMicrosecond(Instant value) {
        if (value == null || value.getNano() % 1000 == 0) {
            return value;
        }
        try {
            return value.truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.MICROS);
        } catch (DateTimeException exception) {
            throw new InvalidParameterException("审计时间超出支持范围", exception);
        }
    }

    private static String normalize(String value, int maximum, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maximum) {
            throw new InvalidParameterException("审计" + label + "不能超过 " + maximum + " 个字符");
        }
        return normalized;
    }
}
