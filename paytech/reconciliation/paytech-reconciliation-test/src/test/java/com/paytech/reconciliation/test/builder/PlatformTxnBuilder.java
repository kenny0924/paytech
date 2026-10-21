package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnSource;
import com.paytech.reconciliation.api.enums.PlatformTxnSourceRefType;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/** 平台流水（t_platform_txn）的合法模板：PAYMENT 应收 OPEN，进度金额为 0；金额与外部交易号由用例给出。 */
public final class PlatformTxnBuilder extends DataObjectBuilder<PlatformTxn, PlatformTxnBuilder> {

    private PlatformTxnBuilder(PlatformTxn txn) {
        super(txn);
    }

    /** PAYMENT 应收平台流水：清算账户、外部交易号（PAY 类型）、预期金额与预估费用。 */
    public static PlatformTxnBuilder payment(ClearingAccount account, String externalTransactionId, String amount,
        String expectedFee) {
        return base(account, ClearingDirection.RECEIVABLE, PlatformTxnSource.PAYMENT,
            PlatformTxnSourceRefType.PAYMENT_ATTEMPT, ReconciliationTestConstants.PAY_REFERENCE_TYPE,
            externalTransactionId, amount, expectedFee);
    }

    /** REFUND 应付平台流水：清算账户、外部交易号（REFUND 类型）与预期金额，预估费用为 0。 */
    public static PlatformTxnBuilder refund(ClearingAccount account, String externalTransactionId, String amount) {
        return base(account, ClearingDirection.PAYABLE, PlatformTxnSource.REFUND,
            PlatformTxnSourceRefType.CHANNEL_REFUND,
            ReconciliationTestConstants.REFUND_REFERENCE_TYPE, externalTransactionId, amount, "0");
    }

    /** 公共字段。 */
    static PlatformTxnBuilder base(ClearingAccount account, ClearingDirection direction, PlatformTxnSource source,
        PlatformTxnSourceRefType sourceRefType, String referenceType, String externalTransactionId, String amount,
        String expectedFee) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        PlatformTxn txn = new PlatformTxn();
        txn.setId(ReconciliationTestConstants.nextId());
        txn.setClearingAccountId(account.getId());
        txn.setDirection(direction);
        txn.setTxnSource(source);
        txn.setBillingFactId(ReconciliationTestConstants.nextId());
        txn.setEntityNo(account.getEntityNo());
        txn.setSourceRefType(sourceRefType);
        txn.setSourceRefId(String.valueOf(ReconciliationTestConstants.nextId()));
        txn.setChannelTransactionRefId(String.valueOf(ReconciliationTestConstants.nextId()));
        txn.setReferenceType(referenceType);
        txn.setExternalTransactionId(externalTransactionId);
        txn.setCurrency(account.getCurrency());
        txn.setExpectedAmount(new BigDecimal(amount));
        txn.setExpectedFee(new BigDecimal(expectedFee));
        txn.setMatchedAmount(BigDecimal.ZERO);
        txn.setActualFee(BigDecimal.ZERO);
        txn.setSettledAmount(BigDecimal.ZERO);
        txn.setStatus(PlatformTxnStatus.OPEN);
        txn.setBusinessTime(now);
        txn.setVersion(1L);
        txn.setCreateTime(now);
        txn.setUpdateTime(now);
        return new PlatformTxnBuilder(txn);
    }

    /** 返回自身。 */
    @Override
    protected PlatformTxnBuilder self() {
        return this;
    }
}
