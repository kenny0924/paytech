package com.paytech.counterparty.domain;

import java.io.Serial;

import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.foundation.common.error.ErrorException;

/** Owner 返回值通过传输解码后仍违反 counterparty 依赖的业务合同。 */
public final class CounterpartyUpstreamProtocolException extends ErrorException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CounterpartyUpstreamProtocolException(String diagnostic) {
        super(CounterpartyErrorCode.UPSTREAM_PROTOCOL_ERROR, diagnostic);
    }

    public CounterpartyUpstreamProtocolException(String diagnostic, Throwable cause) {
        super(CounterpartyErrorCode.UPSTREAM_PROTOCOL_ERROR, diagnostic, cause);
    }
}
