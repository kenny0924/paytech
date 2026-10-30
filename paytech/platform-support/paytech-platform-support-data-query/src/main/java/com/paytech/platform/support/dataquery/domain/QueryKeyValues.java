package com.paytech.platform.support.dataquery.domain;

import java.util.regex.Pattern;

import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;

/** 键值规范化（B4）：INTEGER 去前导零并校验 0..9223372036854775807；STRING 原样，不 trim、不改大小写。 */
public final class QueryKeyValues {

    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    private static final String MAX_LONG = Long.toString(Long.MAX_VALUE);

    private QueryKeyValues() {
    }

    /**
     * 规范化一个键值。
     *
     * @param type 值类型
     * @param value 原值
     * @return 规范值；不合法（INTEGER 非数字或越界、空串）时为 null
     */
    public static String normalize(QueryKeyValueType type, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (type == QueryKeyValueType.STRING) {
            return value;
        }
        if (!DIGITS.matcher(value).matches()) {
            return null;
        }
        String digits = value.replaceFirst("^0+(?=.)", "");
        boolean inRange = digits.length() < MAX_LONG.length()
            || digits.length() == MAX_LONG.length() && digits.compareTo(MAX_LONG) <= 0;
        return inRange ? digits : null;
    }
}
