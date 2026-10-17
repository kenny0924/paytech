package com.paytech.platform.support.api.operations.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class AuditAttribute {
    /** 属性原始序号。 */
    private final int number;

    /**
     * 属性名称。
     * <li>可与其他条目重复。</li>
     */
    private final String key;

    /**
     * 按分类允许展示的原文或脱敏值。
     * <li>无安全展示值时为空。</li>
     */
    private final String value;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public AuditAttribute(@JsonProperty(value = "number", required = true) int number,
        @JsonProperty(value = "key") String key,
        @JsonProperty(value = "value") String value) {
        this.number = number;
        this.key = key;
        this.value = value;
    }

    /** 属性原始序号。 */
    public int getNumber() {
        return number;
    }

    /** 属性名称，可与其他条目重复。 */
    public String getKey() {
        return key;
    }

    /** 按分类允许展示的原文或脱敏值；无安全展示值时为空。 */
    public String getValue() {
        return value;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof AuditAttribute other && Objects.equals(number, other.number)
            && Objects.equals(key, other.key)
            && Objects.equals(value, other.value);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(number, key, value);
    }
}
