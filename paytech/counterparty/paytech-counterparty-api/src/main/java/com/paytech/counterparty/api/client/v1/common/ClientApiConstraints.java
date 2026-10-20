package com.paytech.counterparty.api.client.v1.common;

/** counterparty Client API V1 线上合同复用的词法约束。 */
public final class ClientApiConstraints {

    /**
     * Opaque Client-scoped consumer reference: letters, digits and {@code . _ : -} only, so e-mail addresses and
     * phone numbers with {@code @} or {@code +} are rejected at the boundary.
     */
    public static final String CONSUMER_REFERENCE = "^[A-Za-z0-9._:-]+$";

    private ClientApiConstraints() {}
}
