package com.paytech.foundation.id;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * ID 生成组件的稳定错误码。
 *
 * @author Codex
 */
public enum IdErrorCode implements CodeMessageEnum {

    GENERATION_ERROR("ID_GENERATION_ERROR", "ID生成失败");

    private final String code;
    private final String message;

    IdErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
