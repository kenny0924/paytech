package com.paytech.operations.console.bff.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** 补充标准 Size 按 UTF-16 计数无法表达的码点合同。 */
public final class DictionaryTextValidator implements ConstraintValidator<DictionaryText, String> {
    private DictionaryText rule;
    /** 保存当前字段的码点、空值和多行约束。 */
    @Override public void initialize(DictionaryText rule) { this.rule = rule; }
    /** 按 Unicode 码点验证字典文本，拒绝孤立代理项及未允许的控制字符。 */
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return rule.nullable();
        if (value.codePointCount(0, value.length()) > rule.max()) return false;
        if (!rule.nullable() && value.isBlank()) return false;
        if (rule.trimmed() && !value.strip().equals(value)) return false;
        return value.codePoints().noneMatch(c -> Character.isSurrogate((char)c) && c <= 0xffff
            || Character.isISOControl(c) && !(rule.multiline() && (c == '\n' || c == '\r' || c == '\t')));
    }
}
