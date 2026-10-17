package com.paytech.commerce.domain.checkout;

import java.io.Serial;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.foundation.common.error.ErrorException;

/** Owner 返回值通过传输解码后仍违反 Checkout 业务合同。 */
public final class CheckoutUpstreamProtocolException extends ErrorException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CheckoutUpstreamProtocolException(String diagnostic) {
        super(CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR, diagnostic);
    }

    public CheckoutUpstreamProtocolException(String diagnostic, Throwable cause) {
        super(CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR, diagnostic, cause);
    }
}
