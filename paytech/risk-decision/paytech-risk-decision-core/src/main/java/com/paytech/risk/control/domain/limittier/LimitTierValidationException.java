package com.paytech.risk.control.domain.limittier;

import java.io.Serial;

import com.paytech.foundation.common.error.InvalidParameterException;

/** 档位命令的字段组合不满足（如驳回未写意见）；Web 转为 422 VALIDATION_ERROR 并按 field 定位。 */
public final class LimitTierValidationException extends InvalidParameterException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    /**
     * 构造字段校验失败
     *
     * @param field 请求体字段名
     * @param message 可安全外显的中文说明
     */
    public LimitTierValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** 请求体字段名。 */
    public String field() {
        return field;
    }
}
