package com.paytech.ledger.auto.domainservice.posting;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientFee;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientPrincipal;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.api.request.CatchUpPostingStreamRequestBody;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.mocker.external.CurrencySpecMocker;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/**
 * 过账读取 Platform 币种规则快照（需求 platform-support 币种主数据维护与读取缓存，AC-05、Q2）
 * <li>过账事务内的精度校验只读进程内快照：快照中没有账簿币种时整笔回滚、不残留，处理期间不向 Platform 发起任何调用，只请求刷新；
 * 这是可重试的同步滞后（ErrorException），消费按失败重投而不进死信，追平命令返回 500；下一个检查周期回源后自动恢复。</li>
 * <li>INACTIVE 只拦新用途，已有账簿照常过账；DRAFT 从未生效，过账被拒。</li>
 *
 * @see com.paytech.ledger.ext.mq.AccountPostedPostingHandler#consume(com.paytech.foundation.messaging.api.DomainEvent)
 * @see com.paytech.ledger.api.PostingStreamCommandApi#catchUpPostingStream(String, String, CatchUpPostingStreamRequestBody)
 * @see com.paytech.ledger.app.ext.job.CurrencySnapshotRefreshScheduler#refreshIfDue()
 */
class CurrencyRulePostingTest extends AbstractTransactionPostingTest {
    /** 追平命令路径。 */
    private static final String CATCH_UP_PATH = "/posting-streams/v1/catch-up";

    /** 快照中没有账簿币种时的失败原因。 */
    private static final String CURRENCY_NOT_SYNCED = "币种规则不存在或尚未同步: " + CURRENCY;

    /** 标准无 Partner 支付：gross 100 = 本金 97 + 手续费 3。 */
    private BillingFactPayload standardPayment() {
        return fact(payment("100"), clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));
    }

    /**
     * AC-05：Platform 删除账簿币种后刷新，快照中不再有 USD。经真实 Broker 投递的批次在过账事务内失败并回滚，按可重试失败重投（不进死信），
     * 期间无任何残留、Platform 没有收到调用，快照被标记待刷新。Platform 恢复 USD 后下一个检查周期回源一次，重投的批次随即自动过账。
     */
    @Test
    void test_missingCurrencyStallsStreamUntilRefresh() {
        refreshCurrencies(CurrencySpecMocker.active("AED", 2));
        BillingFactPayload economics = standardPayment();
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();
        billingFacts.mockFact(economics);
        long platformCalls = currencySpecs.listCalls();

        accountEvents.publish(posting);
        await().atMost(CONSUME_WAIT).until(currencySnapshot::isRefreshRequested);

        assertAll(() -> assertEquals(0, testMapper.countAllInstructions(), "不应残留指令"),
            () -> assertEquals(0, testMapper.countAllJournalEntries(), "不应残留 Journal"),
            () -> assertEquals(0, testMapper.countAllAccounts(), "不应残留自动创建的账户"),
            () -> assertEquals(0, consumedSequence(posting), "失败不推进消费进度"),
            () -> assertEquals(0, accountEvents.deadLetters(), "快照未同步不是业务拒绝，不进死信"),
            () -> assertEquals(platformCalls, currencySpecs.listCalls(), "过账期间不应回源 Platform"));

        currencySpecs.mockCurrencies(CurrencySpecMocker.usd(), CurrencySpecMocker.active("AED", 2));
        currencySnapshotRefresher.refreshIfDue();
        await().atMost(CONSUME_WAIT).until(() -> consumedSequence(posting) == 1);

        assertAll(() -> assertEquals(platformCalls + 1, currencySpecs.listCalls(), "刷新请求触发一次回源"),
            () -> assertFalse(currencySnapshot.isRefreshRequested(), "回源成功后清除刷新请求"),
            () -> assertEquals(0, accountEvents.deadLetters()),
            () -> assertEquals(1, postedViews(posting).size()));
        assertPostingInstruction(instruction(posting, AutoInstructionType.PAYMENT_SUCCEEDED), economics, posting,
            oeLedger.getLedgerBookId(), INITIAL_VERSION_ID, businessDate(economics));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 1);
    }

    /** 追平命令遇到快照中没有的币种：500 且不推进进度、不残留、不回源；回源后再次追平即过账。 */
    @Test
    void test_catchUpWithMissingCurrencyRetriedAfterRefresh() {
        refreshCurrencies(CurrencySpecMocker.active("AED", 2));
        BillingFactPayload economics = standardPayment();
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();
        billingFacts.mockFact(economics);
        accountPostings.mockPostings(posting);
        long platformCalls = currencySpecs.listCalls();

        postAsOperator(CATCH_UP_PATH, catchUpBody()).assertProblem(500, CommonErrorCode.INTERNAL_ERROR.getCode());

        assertAll(() -> assertEquals(0, consumedSequence(posting)),
            () -> assertEquals(0, testMapper.countAllInstructions()),
            () -> assertEquals(platformCalls, currencySpecs.listCalls()),
            () -> assertTrue(currencySnapshot.isRefreshRequested()));

        currencySpecs.mockCurrencies(CurrencySpecMocker.usd());
        currencySnapshotRefresher.refreshIfDue();
        postAsOperator(CATCH_UP_PATH, catchUpBody()).assertSuccess(200).assertText("processedCount", "1");

        assertAll(() -> assertEquals(1, consumedSequence(posting)),
            () -> assertEquals(1, postedViews(posting).size()));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 1);
    }

    /** Q2：Platform 停用 USD 后，已有 USD 账簿的批次照常过账，过账期间不回源。 */
    @Test
    void test_inactiveCurrencyStillPosts() {
        refreshCurrencies(CurrencySpecMocker.currency(CURRENCY, 2, CurrencySpecStatus.INACTIVE));
        BillingFactPayload economics = standardPayment();
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();
        long platformCalls = currencySpecs.listCalls();

        postBatch(economics, posting);

        assertPostingInstruction(instruction(posting, AutoInstructionType.PAYMENT_SUCCEEDED), economics, posting,
            oeLedger.getLedgerBookId(), INITIAL_VERSION_ID, businessDate(economics));
        assertAll(() -> assertEquals(platformCalls, currencySpecs.listCalls()),
            () -> assertFalse(currencySnapshot.isRefreshRequested()));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 1);
    }

    /** DRAFT 币种从未生效：过账被拒且整笔回滚；快照中有该币种，不请求刷新。 */
    @Test
    void test_draftCurrencyRejected() {
        refreshCurrencies(CurrencySpecMocker.currency(CURRENCY, 2, CurrencySpecStatus.DRAFT));
        BillingFactPayload economics = standardPayment();
        billingFacts.mockFact(economics);

        DomainEventRejectedException rejected = assertThrows(DomainEventRejectedException.class,
            () -> consumeDirectly(paymentPosting(economics, 1, "97", null).build()));

        String message = assertInstanceOf(BizCheckFailException.class, rejected.getCause()).getMessage();
        assertAll(() -> assertTrue(message.contains("币种尚未启用: " + CURRENCY), message),
            () -> assertEquals(0, testMapper.countAllInstructions(), "不应残留指令"),
            () -> assertEquals(0, testMapper.countAllJournalEntries(), "不应残留 Journal"),
            () -> assertFalse(currencySnapshot.isRefreshRequested()));
    }

    /** 直接调用同一 Handler：查不到币种抛出指明币种的可重试系统异常，而不是进入死信的业务拒绝。 */
    @Test
    void test_missingCurrencyIsRetryableFailure() {
        refreshCurrencies(CurrencySpecMocker.active("AED", 2));
        BillingFactPayload economics = standardPayment();
        billingFacts.mockFact(economics);

        ErrorException failure = assertThrows(ErrorException.class,
            () -> consumeDirectly(paymentPosting(economics, 1, "97", null).build()));

        assertAll(() -> assertTrue(failure.getMessage().contains(CURRENCY_NOT_SYNCED), failure.getMessage()),
            () -> assertEquals(0, testMapper.countAllInstructions()));
    }

    /** AE001 / USD / 分片 0 的追平请求，不限批次数。 */
    static CatchUpPostingStreamRequestBody catchUpBody() {
        CatchUpPostingStreamRequestBody body = new CatchUpPostingStreamRequestBody();
        body.setEntityType(AccountEntityType.OPERATING_ENTITY.getCode());
        body.setEntityId(ENTITY_NO);
        body.setCurrency(CURRENCY);
        body.setShardNo(0);
        return body;
    }
}
