package com.paytech.billing.test.builder;

import static com.paytech.billing.test.builder.PaymentFactConstants.CLEARING_ACCOUNT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ENTITY_NO;
import static com.paytech.billing.test.builder.PaymentFactConstants.USD;

import com.paytech.reconciliation.api.clearing.response.ClearingAccountResolutionResponseBody;

/** reconciliation 清算账户解析响应：template() 为 AE001 的 USD 清算账户。 */
public final class ClearingAccountResponseBuilder {
    private final ClearingAccountResolutionResponseBody body = new ClearingAccountResolutionResponseBody();

    /** 只能从模板开始。 */
    private ClearingAccountResponseBuilder() {
    }

    /** 默认清算账户。 */
    public static ClearingAccountResponseBuilder template() {
        ClearingAccountResponseBuilder builder = new ClearingAccountResponseBuilder();
        builder.body.setClearingAccountId(Long.toString(CLEARING_ACCOUNT_ID));
        builder.body.setClearingAccountCode("NI-AE001-USD");
        builder.body.setChannelCode("NI");
        builder.body.setEntityNo(ENTITY_NO);
        builder.body.setCurrency(USD);
        return builder;
    }

    /** 清算币种。 */
    public ClearingAccountResponseBuilder currency(String currency) {
        body.setCurrency(currency);
        return this;
    }

    /** 构建。 */
    public ClearingAccountResolutionResponseBody build() {
        return body;
    }
}
