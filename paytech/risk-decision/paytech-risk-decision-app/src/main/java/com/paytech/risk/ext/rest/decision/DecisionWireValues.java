package com.paytech.risk.ext.rest.decision;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** 决策配置 HTTP 转换共用的 ID、时间、Code 输出与必填检查。 */
final class DecisionWireValues {
    /** UTC 六位微秒时间格式。 */
    static final DateTimeFormatter MICROSECOND_UTC = DateTimeFormatter
        .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
        .withZone(ZoneOffset.UTC);

    /** 仅提供共用转换方法。 */
    private DecisionWireValues() {
    }

    /** 正 BIGINT 按十进制字符串输出；非正值说明内部数据损坏。 */
    static String positiveId(long value) {
        if (value <= 0) {
            throw new ErrorException("内部资源标识无效");
        }
        return Long.toString(value);
    }

    /** 正整数输出；非正值说明内部数据损坏。 */
    static Integer positiveInt(int value) {
        if (value <= 0) {
            throw new ErrorException("内部正整数无效");
        }
        return value;
    }

    /** 可空 ID 输出。 */
    static String nullableId(Long value) {
        return value == null ? null : positiveId(value);
    }

    /** UTC 六位微秒时间输出；超出微秒精度说明内部数据损坏。 */
    static String time(Instant value) {
        if (value == null || value.getNano() % 1_000 != 0) {
            throw new ErrorException("内部 UTC 时间无效");
        }
        return MICROSECOND_UTC.format(value);
    }

    /** 可空时间输出。 */
    static String nullableTime(Instant value) {
        return value == null ? null : time(value);
    }

    /** 枚举按稳定 Code 输出，不使用 name()。 */
    static String code(com.paytech.foundation.common.enums.CodeEnum value) {
        if (value == null) {
            throw new ErrorException("内部 Code 无效");
        }
        return value.getCode();
    }

    /** 可空枚举按 Code 输出。 */
    static String nullableCode(com.paytech.foundation.common.enums.CodeEnum value) {
        return value == null ? null : code(value);
    }

    /** Owner 结果必填字段缺失时视为内部错误。 */
    static <T> T require(T value, String name) {
        if (value == null) {
            throw new ErrorException(name + " 无效");
        }
        return value;
    }

    /** 构造指向具体参数的参数错误。 */
    static InvalidParameterException invalid(String name) {
        return new InvalidParameterException(name + " 格式无效");
    }
}
