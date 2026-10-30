package com.paytech.payout.test.builder;

import com.paytech.account.api.response.ClientBalanceResponseBody;
import com.paytech.account.api.response.PartnerBalanceResponseBody;
import com.paytech.foundation.money.Money;
import com.paytech.payout.test.support.PayoutTestConstants;

/**
 * account 余额列表元素 Builder（提供方 DTO）：template() 为 AE001 / USD，可用 100.00、其余为零、保证金 50.00
 * <li>build() 产出 Client 元素；buildPartner() 产出 Local Partner 元素（无保证金字段）。</li>
 */
public final class AccountPositionBuilder {
    private final ClientBalanceResponseBody body = new ClientBalanceResponseBody();

    private AccountPositionBuilder() {
    }

    /** AE001 / USD：PENDING 20.00、AVAILABLE 100.00、LOCKED 0、保证金 50.00、追偿 0。 */
    public static AccountPositionBuilder template() {
        AccountPositionBuilder builder = new AccountPositionBuilder();
        builder.body.setClientId(String.valueOf(PayoutTestConstants.CLIENT_ID));
        builder.body.setEntityNo(PayoutTestConstants.ENTITY_NO);
        builder.body.setCurrency("USD");
        builder.body.setPendingAmount(Money.of("USD", "20.00"));
        builder.body.setAvailableAmount(Money.of("USD", "100.00"));
        builder.body.setLockedAmount(Money.of("USD", "0"));
        builder.body.setSecurityDepositAmount(Money.of("USD", "50.00"));
        builder.body.setRecoveryReceivableAmount(Money.of("USD", "0"));
        return builder;
    }

    /** OE 与币种（金额币种同步切换）。 */
    public AccountPositionBuilder position(String entityNo, String currency) {
        body.setEntityNo(entityNo);
        body.setCurrency(currency);
        body.setPendingAmount(Money.of(currency, body.getPendingAmount().getAmount()));
        body.setAvailableAmount(Money.of(currency, body.getAvailableAmount().getAmount()));
        body.setLockedAmount(Money.of(currency, body.getLockedAmount().getAmount()));
        body.setSecurityDepositAmount(Money.of(currency, body.getSecurityDepositAmount().getAmount()));
        body.setRecoveryReceivableAmount(Money.of(currency, body.getRecoveryReceivableAmount().getAmount()));
        return this;
    }

    /** 可用余额。 */
    public AccountPositionBuilder available(String amount) {
        body.setAvailableAmount(Money.of(body.getCurrency(), amount));
        return this;
    }

    /** 追偿应收。 */
    public AccountPositionBuilder recovery(String amount) {
        body.setRecoveryReceivableAmount(Money.of(body.getCurrency(), amount));
        return this;
    }

    /** 构造 Client 元素。 */
    public ClientBalanceResponseBody build() {
        return body;
    }

    /** 构造 Local Partner 元素：同一金额，不含保证金。 */
    public PartnerBalanceResponseBody buildPartner() {
        PartnerBalanceResponseBody partner = new PartnerBalanceResponseBody();
        partner.setPartnerId(String.valueOf(PayoutTestConstants.PARTNER_ID));
        partner.setEntityNo(body.getEntityNo());
        partner.setCurrency(body.getCurrency());
        partner.setPendingAmount(body.getPendingAmount());
        partner.setAvailableAmount(body.getAvailableAmount());
        partner.setLockedAmount(body.getLockedAmount());
        partner.setRecoveryReceivableAmount(body.getRecoveryReceivableAmount());
        return partner;
    }
}
