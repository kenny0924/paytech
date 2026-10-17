package com.paytech.acquiring.domain.checkout;

import java.io.Serial;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.web.api.RetryAfterProvider;

/** Checkout 已开始处理，但尚未取得可安全重放的最终回执。 */
public final class CheckoutCreationInProgressException extends ServiceUnavailableException
    implements RetryAfterProvider {

    @Serial
    private static final long serialVersionUID = 1L;

    public CheckoutCreationInProgressException() {
        super(CommerceCheckoutErrorCode.CHECKOUT_CREATION_IN_PROGRESS);
    }

    public CheckoutCreationInProgressException(Throwable cause) {
        super(CommerceCheckoutErrorCode.CHECKOUT_CREATION_IN_PROGRESS, cause);
    }

    @Override
    public long retryAfterSeconds() {
        return 3L;
    }
}
