package com.paytech.platform.support.api.tenantaccessmanagement.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 安全的授权解释。 */
public class Reason {
    /** 原因代码。 */
    private final String code;

    /** 可读说明。 */
    private final String message;
    /** 按明确字段合同构造对象。 */
    @JsonCreator
    public Reason(@JsonProperty(value = "code") String code,
        @JsonProperty(value = "message") String message) {
        this.code = code;
        this.message = message;
    }

    /** 原因代码。 */
    public String getCode() { return code; }

    /** 可读说明。 */
    public String getMessage() { return message; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Reason value)) return false;
        return Objects.equals(code, value.code)
            && Objects.equals(message, value.message);
    }

    @Override
    public int hashCode() { return Objects.hash(code, message); }
}
