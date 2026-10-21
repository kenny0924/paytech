package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/** 预期银行流水（t_expected_bank_movement）的合法模板：渠道账单对平生成的 CHANNEL_SETTLEMENT，OPEN，预期日取结算日。 */
public final class ExpectedBankMovementBuilder extends DataObjectBuilder<ExpectedBankMovement,
    ExpectedBankMovementBuilder> {

    private ExpectedBankMovementBuilder(ExpectedBankMovement movement) {
        super(movement);
    }

    /** 渠道账单净额的预期到账（CREDIT）或扣款（DEBIT）。 */
    public static ExpectedBankMovementBuilder channelSettlement(ChannelStatement statement, CashAccount account,
        SettlementDirection direction, String amount) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ExpectedBankMovement movement = new ExpectedBankMovement();
        movement.setId(ReconciliationTestConstants.nextId());
        movement.setMovementType(ExpectedMovementType.CHANNEL_SETTLEMENT);
        movement.setSourceId(statement.getId());
        movement.setCashAccountId(account.getId());
        movement.setEntityNo(account.getEntityId());
        movement.setDirection(direction);
        movement.setCurrency(statement.getCurrency());
        movement.setExpectedAmount(new BigDecimal(amount));
        movement.setExpectedDate(statement.getSettlementDate());
        movement.setConfirmedAmount(BigDecimal.ZERO);
        movement.setClassifiedAmount(BigDecimal.ZERO);
        movement.setStatus(ExpectedMovementStatus.OPEN);
        movement.setVersion(1L);
        movement.setCreateTime(now);
        movement.setUpdateTime(now);
        return new ExpectedBankMovementBuilder(movement);
    }

    /** 返回自身。 */
    @Override
    protected ExpectedBankMovementBuilder self() {
        return this;
    }
}
