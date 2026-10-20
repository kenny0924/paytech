package com.paytech.commercial.ext.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 运营写入合同的 Wire 值转换：十进制字符串 ID、带偏移 ISO 8601 时间、稳定 Code 与十进制字符串；只做协议解析，不做业务校验。 */
final class WireValues {
    /** 纯静态工具。 */
    private WireValues() {
    }

    /**
     * 规范正十进制 ID；空返回 null
     *
     * @param field 字段名
     * @param value Wire 值
     * @return ID
     */
    static Long id(String field, String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException(field + " 必须是规范正十进制 ID");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new InvalidParameterException(field + " 超出 BIGINT 范围");
        }
    }

    /** 带时区偏移的 ISO 8601 时间；空返回 null。 */
    static Instant time(String field, String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException invalid) {
            throw new InvalidParameterException(field + " 必须是带时区偏移的 ISO 8601 时间");
        }
    }

    /** 十进制字符串；空返回 null。 */
    static BigDecimal decimal(String field, String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException invalid) {
            throw new InvalidParameterException(field + " 必须是十进制字符串");
        }
    }

    /** 稳定 Code 到枚举；空返回 null，未知 Code 失败关闭。 */
    static <E extends Enum<E> & CodeEnum> E code(Class<E> type, String field, String value) {
        if (value == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(value)) {
                return constant;
            }
        }
        throw new InvalidParameterException(field + " 不是允许的代码: " + value);
    }

    /** ID 到十进制字符串；空返回 null。 */
    static String text(Long value) {
        return value == null ? null : value.toString();
    }

    /** UTC ISO 8601 时间；空返回 null。 */
    static String text(Instant value) {
        return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value);
    }

    /** 十进制去尾零字符串；空返回 null。 */
    static String text(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** 枚举稳定 Code；空返回 null。 */
    static String text(CodeEnum value) {
        return value == null ? null : value.getCode();
    }

    /**
     * 可重复查询参数的稳定 Code 集合；空返回空集合，未知 Code 失败关闭
     *
     * @param type 枚举类型
     * @param field 参数名
     * @param values Wire 值，可空
     * @return 去重后保持请求顺序的枚举
     */
    static <E extends Enum<E> & CodeEnum> List<E> codes(Class<E> type, String field, List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> code(type, field, value)).distinct().toList();
    }

    /**
     * 按格式校验的查询文本；空或空白返回 null
     *
     * @param field 参数名
     * @param value Wire 值
     * @param regex 完整匹配的格式
     * @return 原值
     */
    static String matching(String field, String value, String regex) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.matches(regex)) {
            throw new InvalidParameterException(field + " 格式不正确");
        }
        return value;
    }
}
