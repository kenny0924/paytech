package com.paytech.billing.integration;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.paytech.foundation.common.error.ErrorException;

/**
 * 上游内部 HTTP 响应字段（十进制字符串 ID 与金额）到 billing 领域值的解析
 * <li>上游 DTO 以字符串承载 ID 与金额；格式不合法或必填字段缺失视为上游响应无效，抛出 ErrorException（消费方退避重试），消息只含
 * 上游与字段名，不回显值。</li>
 */
public final class UpstreamResponseFields {
    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]{0,17})(\\.[0-9]{1,10})?");

    /** 纯静态解析。 */
    private UpstreamResponseFields() {
    }

    /** 必填正十进制 ID。 */
    public static long requiredId(String upstream, String field, String value) {
        if (value == null || !DECIMAL_ID.matcher(value).matches()) {
            throw invalid(upstream, field);
        }
        return Long.parseLong(value);
    }

    /** 必填普通十进制数（金额、费率），不接受科学计数法。 */
    public static BigDecimal requiredDecimal(String upstream, String field, String value) {
        if (value == null || !DECIMAL.matcher(value).matches()) {
            throw invalid(upstream, field);
        }
        return new BigDecimal(value);
    }

    /** 可空普通十进制数；为空返回空。 */
    public static BigDecimal optionalDecimal(String upstream, String field, String value) {
        return value == null ? null : requiredDecimal(upstream, field, value);
    }

    /** 必填字段非空。 */
    public static <T> T required(String upstream, String field, T value) {
        if (value == null) {
            throw invalid(upstream, field);
        }
        return value;
    }

    /** 上游响应无效。 */
    static ErrorException invalid(String upstream, String field) {
        return new ErrorException(upstream + " 响应无效: " + field);
    }
}
