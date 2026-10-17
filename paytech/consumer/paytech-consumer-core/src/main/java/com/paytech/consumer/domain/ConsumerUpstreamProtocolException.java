package com.paytech.consumer.domain;

import java.io.Serial;

import com.paytech.consumer.api.enums.ConsumerErrorCode;
import com.paytech.foundation.common.error.ErrorException;

/** Owner 返回值通过传输解码后仍违反 consumer 依赖的业务合同。 */
public final class ConsumerUpstreamProtocolException extends ErrorException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConsumerUpstreamProtocolException(String diagnostic) {
        super(ConsumerErrorCode.UPSTREAM_PROTOCOL_ERROR, diagnostic);
    }

    public ConsumerUpstreamProtocolException(String diagnostic, Throwable cause) {
        super(ConsumerErrorCode.UPSTREAM_PROTOCOL_ERROR, diagnostic, cause);
    }
}
