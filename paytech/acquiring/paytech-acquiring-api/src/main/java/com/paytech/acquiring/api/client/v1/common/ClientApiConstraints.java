package com.paytech.acquiring.api.client.v1.common;

/** Reusable lexical constraints for the Commerce Client API V1 wire contract. */
public final class ClientApiConstraints {

    /** Printable-ASCII, case-sensitive Client reference accepted at the external boundary. */
    public static final String CLIENT_REFERENCE = "^[!-~]+$";

    /**
     * Opaque Client-scoped consumer reference: letters, digits and {@code . _ : -} only, so e-mail addresses and
     * phone numbers with {@code @} or {@code +} are rejected at the boundary.
     */
    public static final String CONSUMER_REFERENCE = "^[A-Za-z0-9._:-]+$";

    /** Three-letter uppercase ISO 4217 currency-code shape. */
    public static final String CURRENCY = "^[A-Z]{3}$";

    /** Two-letter uppercase country-code shape. */
    public static final String COUNTRY = "^[A-Z]{2}$";

    /** Non-negative plain decimal string with up to 16 integer and 8 fractional digits. */
    public static final String DECIMAL = "^(?:0|[1-9][0-9]{0,15})(?:[.][0-9]{1,8})?$";

    /** Positive plain decimal string with up to 16 integer and 8 fractional digits. */
    public static final String POSITIVE_DECIMAL = "^(?=.*[1-9])(?:0|[1-9][0-9]{0,15})(?:[.][0-9]{1,8})?$";

    private ClientApiConstraints() {}
}
