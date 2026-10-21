package com.paytech.reconciliation.test.builder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/** 平台银行账户（t_cash_account）的合法模板：经营主体 AE001、USD、渠道结算用途、已复核 ACTIVE，账号只有合成 Vault 令牌与末四位。 */
public final class CashAccountBuilder extends DataObjectBuilder<CashAccount, CashAccountBuilder> {

    private CashAccountBuilder(CashAccount account) {
        super(account);
    }

    /** 最小合法 ACTIVE 银行账户。 */
    public static CashAccountBuilder template() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long id = ReconciliationTestConstants.nextId();
        CashAccount account = new CashAccount();
        account.setId(id);
        account.setEntityType(ReconEntityType.OPERATING_ENTITY);
        account.setEntityId(ReconciliationTestConstants.ENTITY_NO);
        account.setAccountPurpose(AccountPurpose.CHANNEL_SETTLEMENT);
        account.setBankCode("TESTBANK");
        account.setAccountName("Settlement " + id);
        account.setCurrency(ReconciliationTestConstants.CURRENCY);
        // 合成 Vault 令牌：与 V1 Token 同为 43 位 Base64URL 字符。
        account.setAccountVaultToken("t" + String.format("%042d", id));
        account.setAccountLast4("1234");
        account.setStatus(CashAccountStatus.ACTIVE);
        account.setContentHash("0".repeat(64));
        account.setMakerId(ReconciliationTestConstants.MAKER_ID);
        account.setCheckerId(ReconciliationTestConstants.CHECKER_ID);
        account.setVersion(1L);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return new CashAccountBuilder(account);
    }

    /** 返回自身。 */
    @Override
    protected CashAccountBuilder self() {
        return this;
    }
}
