package com.paytech.foundation.id;

import java.io.Serial;

import com.paytech.foundation.common.error.ErrorException;

/**
 * 无法在保证唯一性的前提下生成 ID 时抛出的结果未知异常。
 *
 * @author Codex
 */
public class IdGenerationException extends ErrorException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdGenerationException(String message) {
        super(IdErrorCode.GENERATION_ERROR, message);
    }
}
