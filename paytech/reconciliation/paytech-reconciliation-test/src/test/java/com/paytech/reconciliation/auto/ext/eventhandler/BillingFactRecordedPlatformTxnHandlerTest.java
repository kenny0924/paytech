package com.paytech.reconciliation.auto.ext.eventhandler;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnSource;
import com.paytech.reconciliation.api.enums.PlatformTxnSourceRefType;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.BillingFactBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 平台流水登记（AC-04，technique D-06、D-07）：BillingFactRecorded 经真实本地 Broker 进入正式消费链路
 * <li>PAYMENT → 应收、REFUND → 应付，预估费用取 CHANNEL_COST 行合计；同一事实重复投递只登记一次；其他类型确认忽略。</li>
 * <li>缺必需引用、清算账户停用：重投到上限后进 DLQ，不登记也不丢；更正事实直接进 DLQ。</li>
 *
 * @see com.paytech.reconciliation.ext.mq.BillingFactRecordedPlatformTxnHandler#consume(DomainEvent)
 */
class BillingFactRecordedPlatformTxnHandlerTest extends AbstractReconciliationFlowTest {

    /** PAYMENT 生成应收平台流水，冻结渠道交易引用，预估费用为 CHANNEL_COST 行合计。 */
    @Test
    void test_paymentRecordedAsReceivable() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        BillingFactPayload fact = BillingFactBuilder.payment(account.getId(), "TXN-P1", "100.00").channelCost("0.30")
            .channelCost("0.20").build();

        PlatformTxn txn = recorded(fact);

        String channelRefId = fact.getRefs().stream().filter(ref -> FactRefType.CHANNEL_TRANSACTION_REF.getCode()
            .equals(ref.getRefType())).findFirst().orElseThrow().getRefId();
        assertAll(() -> assertEquals(ClearingDirection.RECEIVABLE, txn.getDirection()),
            () -> assertEquals(PlatformTxnSource.PAYMENT, txn.getTxnSource()),
            () -> assertEquals(PlatformTxnSourceRefType.PAYMENT_ATTEMPT, txn.getSourceRefType()),
            () -> assertEquals(account.getId(), txn.getClearingAccountId()),
            () -> assertEquals(channelRefId, txn.getChannelTransactionRefId()),
            () -> assertEquals(ReconciliationTestConstants.PAY_REFERENCE_TYPE, txn.getReferenceType()),
            () -> assertEquals("TXN-P1", txn.getExternalTransactionId()),
            () -> assertEquals(0, new BigDecimal("100.00").compareTo(txn.getExpectedAmount())),
            () -> assertEquals(0, new BigDecimal("0.50").compareTo(txn.getExpectedFee())),
            () -> assertEquals(PlatformTxnStatus.OPEN, txn.getStatus()),
            () -> assertEquals(fact.getBusinessTime(), txn.getBusinessTime()));
    }

    /** REFUND 生成应付平台流水，没有渠道成本行时预估费用为 0。 */
    @Test
    void test_refundRecordedAsPayable() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());

        PlatformTxn txn = recorded(BillingFactBuilder.refund(account.getId(), "TXN-R1", "30.00").build());

        assertAll(() -> assertEquals(ClearingDirection.PAYABLE, txn.getDirection()),
            () -> assertEquals(PlatformTxnSource.REFUND, txn.getTxnSource()),
            () -> assertEquals(PlatformTxnSourceRefType.CHANNEL_REFUND, txn.getSourceRefType()),
            () -> assertEquals(ReconciliationTestConstants.REFUND_REFERENCE_TYPE, txn.getReferenceType()),
            () -> assertEquals(0, BigDecimal.ZERO.compareTo(txn.getExpectedFee())));
    }

    /** 同一事件重投（消费登记去重）与同一事实的另一事件（billing_fact_id 业务幂等）都只登记一次。 */
    @Test
    void test_redeliveredFactRecordedOnce() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        BillingFactPayload fact = BillingFactBuilder.payment(account.getId(), "TXN-DUP", "10.00").build();
        PlatformTxn first = recorded(fact);

        billing.publish(fact);
        await().atMost(ASYNC_WAIT).until(() -> billing.pending() == 0);
        fact.setFactVersion(2L);
        consumed(fact);

        assertEquals(1, platformTxns.countPage(account.getId(), null, null, null, null, null, null));
        assertEquals(first.getId(), platformTxns.findByBillingFactId(fact.getFactId()).getId());
    }

    /** 非 PAYMENT / REFUND 类型或未批准的事实确认忽略。 */
    @Test
    void test_otherFactsIgnored() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        BillingFactPayload manual = BillingFactBuilder.of(BillingFactType.MANUAL_ADJUSTMENT, "5.00").build();
        BillingFactPayload draft = BillingFactBuilder.payment(account.getId(), "TXN-DRAFT", "5.00").build();
        draft.setStatus(BillingFactStatus.DRAFT.getCode());

        consumed(manual);
        consumed(draft);

        assertNull(platformTxns.findByBillingFactId(manual.getFactId()));
        assertNull(platformTxns.findByBillingFactId(draft.getFactId()));
        assertEquals(0, billing.deadLetters());
    }

    /** 缺 CLEARING_ACCOUNT 引用：可重试，到投递上限后进 DLQ，不登记、不消费登记。 */
    @Test
    void test_missingClearingAccountRefDeadLettered() {
        BillingFactPayload fact = BillingFactBuilder.payment(ReconciliationTestConstants.nextId(), "TXN-NOCA", "1.00")
            .withoutRef(FactRefType.CLEARING_ACCOUNT).build();

        assertDeadLettered(fact);
    }

    /** 缺 CHANNEL_TRANSACTION_REF 引用：同上。 */
    @Test
    void test_missingChannelRefDeadLettered() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        BillingFactPayload fact = BillingFactBuilder.payment(account.getId(), "TXN-NOREF", "1.00")
            .withoutRef(FactRefType.CHANNEL_TRANSACTION_REF).build();

        assertDeadLettered(fact);
    }

    /** 清算账户已停用：可重试，最终进 DLQ。 */
    @Test
    void test_inactiveClearingAccountDeadLettered() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template()
            .with(item -> item.setStatus(ClearingAccountStatus.INACTIVE)).build());

        assertDeadLettered(BillingFactBuilder.payment(account.getId(), "TXN-INACTIVE", "1.00").build());
    }

    /** 带更正关系的事实（D-07）直接拒收进 DLQ。 */
    @Test
    void test_supersedingFactRejected() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());

        assertDeadLettered(BillingFactBuilder.payment(account.getId(), "TXN-SUP", "1.00")
            .supersedes(ReconciliationTestConstants.nextId()).build());
    }

    /** 发布后等待进入 DLQ，且没有平台流水与消费登记。 */
    void assertDeadLettered(BillingFactPayload fact) {
        String eventId = billing.publish(fact);

        await().atMost(ASYNC_WAIT).until(() -> billing.deadLetters() == 1);
        assertAll(() -> assertNull(platformTxns.findByBillingFactId(fact.getFactId())),
            () -> assertEquals(0, testMapper.countConsumedEvent(eventId)));
    }
}
