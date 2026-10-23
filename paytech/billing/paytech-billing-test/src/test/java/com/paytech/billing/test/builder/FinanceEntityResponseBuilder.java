package com.paytech.billing.test.builder;

import static com.paytech.billing.test.builder.PaymentFactConstants.FINANCE_ENTITY_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PLATFORM_SERVICE_PARTY_ID;

import java.time.Instant;

import com.paytech.ledger.api.response.FinanceEntityResponseBody;

/** ledger Finance Entity 响应：template() 为平台签约 Party 对应的 ACTIVE PLATFORM_HQ。 */
public final class FinanceEntityResponseBuilder {
    private final FinanceEntityResponseBody body = new FinanceEntityResponseBody();

    /** 只能从模板开始。 */
    private FinanceEntityResponseBuilder() {
    }

    /** 默认 Finance Entity。 */
    public static FinanceEntityResponseBuilder template() {
        FinanceEntityResponseBuilder builder = new FinanceEntityResponseBuilder();
        builder.body.setFinanceEntityId(Long.toString(FINANCE_ENTITY_ID));
        builder.body.setEntityCode("PLATFORM_HQ");
        builder.body.setLegalPartyId(Long.toString(PLATFORM_SERVICE_PARTY_ID));
        builder.body.setDisplayName("Platform HQ");
        builder.body.setStatus("ACTIVE");
        builder.body.setCreateTime(Instant.parse("2026-10-01T00:00:00Z"));
        return builder;
    }

    /** 构建。 */
    public FinanceEntityResponseBody build() {
        return body;
    }
}
