package com.paytech.foundation.jsoncanonicalization.api;

import java.io.Serial;

/** 表示输入不在已批准 JSON Object Profile 内，或底层规范化失败。 */
public final class JsonCanonicalizationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SAFE_MESSAGE = "JSON canonicalization failed";

    public JsonCanonicalizationException() {
        super(SAFE_MESSAGE);
    }

    public JsonCanonicalizationException(Throwable cause) {
        super(SAFE_MESSAGE, cause);
    }
}
