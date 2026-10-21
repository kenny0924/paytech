package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/** 清算账户（t_clearing_account）与其 MID 归属行的合法模板：ACTIVE、USD、AE001、渠道 NI，三项配置取 DDL 默认值。 */
public final class ClearingAccountBuilder extends DataObjectBuilder<ClearingAccount, ClearingAccountBuilder> {

    private ClearingAccountBuilder(ClearingAccount account) {
        super(account);
    }

    /** 最小合法清算账户；未配置到账银行账户。 */
    public static ClearingAccountBuilder template() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long id = ReconciliationTestConstants.nextId();
        ClearingAccount account = new ClearingAccount();
        account.setId(id);
        account.setClearingAccountCode("CA-" + id);
        account.setChannelCode(ReconciliationTestConstants.CHANNEL_CODE);
        account.setEntityNo(ReconciliationTestConstants.ENTITY_NO);
        account.setCurrency(ReconciliationTestConstants.CURRENCY);
        account.setUnmatchedWaitDays(3);
        account.setCostToleranceAmount(BigDecimal.ZERO);
        account.setCashGraceDays(2);
        account.setStatus(ClearingAccountStatus.ACTIVE);
        account.setVersion(1L);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return new ClearingAccountBuilder(account);
    }

    /** 到账银行账户。 */
    public ClearingAccountBuilder settlementCashAccount(long cashAccountId) {
        target.setSettlementCashAccountId(cashAccountId);
        return this;
    }

    /** 清算账户所含的一条 MID 归属行（币种取清算账户币种）。 */
    public static ClearingAccountMid mid(ClearingAccount account, long midId) {
        ClearingAccountMid mid = new ClearingAccountMid();
        mid.setId(ReconciliationTestConstants.nextId());
        mid.setClearingAccountId(account.getId());
        mid.setMidId(midId);
        mid.setCurrency(account.getCurrency());
        mid.setCreateTime(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return mid;
    }

    /** 返回自身。 */
    @Override
    protected ClearingAccountBuilder self() {
        return this;
    }
}
