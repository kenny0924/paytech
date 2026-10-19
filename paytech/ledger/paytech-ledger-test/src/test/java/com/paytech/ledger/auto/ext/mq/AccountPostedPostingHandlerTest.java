package com.paytech.ledger.auto.ext.mq;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.adjustment;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.AccountPostingBuilder;

/**
 * AccountPosted 消费入口：过账流顺序、只推进进度的批次、事实版本不一致、期间顺延与等待
 * <li>经真实本机 RabbitMQ 投递（account.events → ledger.account.p0..p3 → 幂等拦截器 → Handler）：桶移动批次只推进进度；缺口（3 先于 2）
 * 由拦截器延后重投后补齐；首条不是序号 1 的批次不过账、反复重投后进入死信。</li>
 * <li>直接调用 Handler：billing 返回的事实版本与批次不一致、没有可用 OPEN 期间时以并发冲突等待重投，不过账；事实所属期间已 CLOSED 时顺延到其后
 * 最早的 OPEN 期间，过账日期取该期间开始日。</li>
 *
 * @see com.paytech.ledger.ext.mq.AccountPostedPostingHandler#consume(com.paytech.foundation.messaging.api.DomainEvent)
 */
class AccountPostedPostingHandlerTest extends AbstractTransactionPostingTest {
    private static final String CLIENT = String.valueOf(CLIENT_ID);

    /** 桶移动批次：不读事实、不过账，只推进进度到该序号。 */
    @Test
    void test_balanceMovementOnlyAdvances() {
        AccountPostedPayload movement = AccountPostingBuilder.movement(ENTITY_NO, CURRENCY, 1, CLIENT, "5").build();

        List<?> views = publishAndAwait(movement);

        assertAll(() -> assertTrue(views.isEmpty()),
            () -> assertEquals(1, consumedSequence(movement)),
            () -> assertEquals(0, testMapper.countAllInstructions()),
            () -> assertTrue(billingFacts.calls().isEmpty(), "桶移动批次不读 billing"));
    }

    /** 缺口：序号 1 已消费后 3 先于 2 到达，3 被延后重投，2 过账后 3 再过账，进度到 3。 */
    @Test
    void test_gapRedeliveredUntilFilled() {
        BillingFactPayload first = clientCharge("1");
        BillingFactPayload second = clientCharge("2");
        BillingFactPayload third = clientCharge("3");
        AccountPostedPayload firstPosting = clientCredit(first, 1, "1");
        postBatch(first, firstPosting);
        billingFacts.mockFact(second);
        billingFacts.mockFact(third);
        AccountPostedPayload thirdPosting = clientCredit(third, 3, "3");
        AccountPostedPayload secondPosting = clientCredit(second, 2, "2");

        accountEvents.publish(thirdPosting);
        accountEvents.publish(secondPosting);

        await().atMost(CONSUME_WAIT).until(() -> consumedSequence(thirdPosting) == 3);
        assertAll(() -> assertEquals(1, postedViews(secondPosting).size()),
            () -> assertEquals(1, postedViews(thirdPosting).size()),
            () -> assertEquals(0, accountEvents.deadLetters()));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 3);
    }

    /** 首条序号不是 1：该流没有消费进度时等待重投，不过账、不推进；超过投递上限进入死信（恢复走追平）。 */
    @Test
    void test_firstSequenceNotOneWaitsThenDeadLetters() {
        BillingFactPayload fact = clientCharge("4");
        billingFacts.mockFact(fact);
        AccountPostedPayload posting = clientCredit(fact, 2, "4");

        accountEvents.publish(posting);

        await().atMost(CONSUME_WAIT).until(() -> accountEvents.deadLetters() == 1);
        assertAll(() -> assertEquals(0, consumedSequence(posting)),
            () -> assertEquals(0, testMapper.countAllInstructions()),
            () -> assertEquals(0, billingFacts.factCalls(fact.getFactId()), "等待首条时不读事实"));
    }

    /** billing 返回的事实版本与批次不一致：并发冲突等待重投，不过账。 */
    @Test
    void test_factVersionMismatchWaits() {
        BillingFactPayload fact = clientCharge("5");
        billingFacts.mockFact(fact);
        AccountPostedPayload posting = AccountPostingBuilder.fact(fact, 1).factVersion(2L).entry(
            StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, EntrySide.CREDIT, "5", 1)
            .build();

        ConcurrentRequestException conflict = assertThrows(ConcurrentRequestException.class,
            () -> consumeDirectly(posting));

        assertTrue(conflict.getMessage().contains("计费事实版本与入账批次不一致"), conflict.getMessage());
        assertEquals(0, testMapper.countAllInstructions());
    }

    /** 事实所属期间已 CLOSED：顺延到其后最早的 OPEN 期间，过账日期为该期间开始日。 */
    @Test
    void test_closedPeriodCarriesForward() {
        LocalDate nextStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        long nextPeriod = ledgerMocker.mockPeriod(oeLedger.getFiscalCalendarId(), nextStart.getYear(),
            nextStart.getMonthValue(), nextStart, nextStart.withDayOfMonth(nextStart.lengthOfMonth()), PeriodStatus.OPEN);
        testMapper.updatePeriodStatus(oeLedger.getOpenPeriodId(), PeriodStatus.CLOSED.getCode());
        BillingFactPayload fact = clientCharge("6");
        AccountPostedPayload posting = clientCredit(fact, 1, "6");

        postBatch(fact, posting);

        AccountingInstruction instruction = instruction(posting, AutoInstructionType.ECONOMIC_ADJUSTMENT);
        JournalEntry journal = assertPostingInstruction(instruction, fact, posting, oeLedger.getLedgerBookId(),
            ADJUSTMENT_VERSION_ID, nextStart);
        assertEquals(nextPeriod, journal.getAccountingPeriodId());
    }

    /** 事实所属期间已 CLOSED 且其后没有 OPEN 期间：并发冲突等待期间开放，不过账。 */
    @Test
    void test_noOpenPeriodWaits() {
        testMapper.updatePeriodStatus(oeLedger.getOpenPeriodId(), PeriodStatus.CLOSED.getCode());
        BillingFactPayload fact = clientCharge("7");
        billingFacts.mockFact(fact);
        AccountPostedPayload posting = clientCredit(fact, 1, "7");

        ConcurrentRequestException conflict = assertThrows(ConcurrentRequestException.class,
            () -> consumeDirectly(posting));

        assertTrue(conflict.getMessage().contains("没有可用的 OPEN 会计期间"), conflict.getMessage());
        assertAll(() -> assertEquals(0, testMapper.countAllInstructions()),
            () -> assertEquals(0, testMapper.countAllAccounts(), "回滚后不残留自动创建的账户"));
    }

    /** 一行 OE 向 Client 支付本金的人工调整。 */
    BillingFactPayload clientCharge(String amount) {
        return fact(payment("0").factType(BillingFactType.MANUAL_ADJUSTMENT), adjustment(FactComponentType.CLIENT_PRINCIPAL,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT, FactLineDirection.CHARGE, amount));
    }

    /** 该事实的批次：一条 Client 应付贷方流水。 */
    static AccountPostedPayload clientCredit(BillingFactPayload fact, long sequence, String amount) {
        return AccountPostingBuilder.fact(fact, sequence).entry(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT,
            CLIENT, BalanceBucket.PENDING, EntrySide.CREDIT, amount, 1).build();
    }
}
