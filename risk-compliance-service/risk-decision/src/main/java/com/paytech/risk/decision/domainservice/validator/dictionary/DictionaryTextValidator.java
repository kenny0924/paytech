package com.paytech.risk.decision.domainservice.validator.dictionary;

import com.paytech.foundation.common.error.InvalidParameterException;

/** 字典当前事实与输入内容共用的纯文本规则，不访问持久化或外部服务。 */
public final class DictionaryTextValidator {

    /** 纯校验方法不持有实例状态。 */
    private DictionaryTextValidator() {}

    /** 校验永久字典编码的格式与长度。 */
    public static void requireCode(String code) {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new InvalidParameterException("字典编码格式无效");
        }
    }

    /** 验证 Unicode 码点长度、首尾空白和控制字符，保留各字段的多行及空串约束。 */
    public static void requireText(String value, int max, boolean multiline, boolean emptyAllowed) {
        if (value == null || !emptyAllowed && value.isBlank() || value.codePointCount(0, value.length()) > max
            || !multiline && !value.strip().equals(value)
            || value.codePoints().anyMatch(c -> (c <= 0xffff && Character.isSurrogate((char)c))
                || Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\r' || c == '\t')))) {
            throw new InvalidParameterException("字典文本格式或长度无效");
        }
    }
}
