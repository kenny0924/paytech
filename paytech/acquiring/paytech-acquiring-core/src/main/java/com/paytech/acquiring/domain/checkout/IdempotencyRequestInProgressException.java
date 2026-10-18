package com.paytech.acquiring.domain.checkout;

import java.io.Serial;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.api.RetryAfterProvider;

/** Root 建立前当前幂等命令仍在处理，需要使用原 Key 稍后重试。 */
public final class IdempotencyRequestInProgressException extends FailException implements RetryAfterProvider {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdempotencyRequestInProgressException() {
        super(CommerceCheckoutErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    public IdempotencyRequestInProgressException(Throwable cause) {
        super(CommerceCheckoutErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, null, cause);
    }

    @Override
    public long retryAfterSeconds() {
        return 3L;
    }
}
