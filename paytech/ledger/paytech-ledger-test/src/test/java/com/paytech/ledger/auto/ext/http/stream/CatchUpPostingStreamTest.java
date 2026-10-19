package com.paytech.ledger.auto.ext.http.stream;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.adjustment;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.ledger.api.request.CatchUpPostingStreamRequestBody;
import com.paytech.ledger.api.response.CatchUpPostingStreamResponseBody;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.AccountPostingBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;
import com.paytech.ledger.test.mocker.external.AccountPostingMocker;
import com.paytech.ledger.test.mocker.external.BillingFactMocker;
import com.paytech.ledger.test.probe.AccountPostedPublisher;

/**
 * 过账流追平命令：从已消费序号 + 1 经 account-api 分页读取批次，逐批次按消费逻辑过账并推进进度
 * <li>覆盖：首条不是序号 1 的批次进入死信后追平补齐；maxBatches 分段续跑；已追平时零处理；account / billing 不可用 503 且已处理批次保留；
 * 请求字段错误。</li>
 *
 * @see com.paytech.ledger.api.PostingStreamCommandApi#catchUpPostingStream(String, String, CatchUpPostingStreamRequestBody)
 */
class CatchUpPostingStreamTest extends AbstractTransactionPostingTest {
    private static final String PATH = "/posting-streams/v1/catch-up";

    private static final String CLIENT = String.valueOf(CLIENT_ID);

    /** 序号 2 先到而进入死信（序号 1 是桶移动）：追平读取 1、2，桶移动只推进，事实批次过账，进度到 2；死信之后重放按 STALE 跳过。 */
    @Test
    void test_catchUpFillsStreamAfterDeadLetter() {
        AccountPostedPayload movement = AccountPostingBuilder.movement(ENTITY_NO, CURRENCY, 1, CLIENT, "5").build();
        BillingFactPayload fact = clientCharge("8");
        AccountPostedPayload posting = clientCredit(fact, 2, "8");
        billingFacts.mockFact(fact);
        accountEvents.publish(posting);
        await().atMost(CONSUME_WAIT).until(() -> accountEvents.deadLetters() == 1);
        accountPostings.mockPostings(movement, posting);

        CatchUpPostingStreamResponseBody response = catchUp(null).assertSuccess(200)
            .as(CatchUpPostingStreamResponseBody.class);

        assertAll(() -> assertEquals("2", response.getProcessedCount()),
            () -> assertEquals("2", response.getLastSequence()),
            () -> assertTrue(response.isCaughtUp()),
            () -> assertEquals(2, consumedSequence(posting)),
            () -> assertEquals(1, postedViews(posting).size()),
            () -> assertEquals(1, testMapper.countAllInstructions()),
            () -> assertEquals(1, billingFacts.factCalls(fact.getFactId())));
        String replayId = accountEvents.publish(posting);
        await().atMost(CONSUME_WAIT).until(() -> accountEvents.pending() == 0);
        assertAll(() -> assertEquals(0, testMapper.countConsumedEvent(replayId), "重放的旧批次按 STALE 跳过、不登记"),
            () -> assertEquals(1, testMapper.countAllInstructions()));
    }

    /** maxBatches 限制本次处理数，返回未追平；再次调用从已消费序号继续。 */
    @Test
    void test_maxBatchesThenResume() {
        BillingFactPayload first = clientCharge("1");
        BillingFactPayload second = clientCharge("2");
        BillingFactPayload third = clientCharge("3");
        billingFacts.mockFact(first);
        billingFacts.mockFact(second);
        billingFacts.mockFact(third);
        AccountPostedPayload thirdPosting = clientCredit(third, 3, "3");
        accountPostings.mockPostings(clientCredit(first, 1, "1"), clientCredit(second, 2, "2"), thirdPosting);

        CatchUpPostingStreamResponseBody partial = catchUp(2).assertSuccess(200)
            .as(CatchUpPostingStreamResponseBody.class);
        CatchUpPostingStreamResponseBody rest = catchUp(2).assertSuccess(200)
            .as(CatchUpPostingStreamResponseBody.class);

        assertAll(() -> assertEquals("2", partial.getProcessedCount()), () -> assertEquals("2", partial.getLastSequence()),
            () -> assertEquals(false, partial.isCaughtUp()),
            () -> assertEquals("1", rest.getProcessedCount()), () -> assertEquals("3", rest.getLastSequence()),
            () -> assertTrue(rest.isCaughtUp()),
            () -> assertEquals(3, consumedSequence(thirdPosting)),
            () -> assertTrue(accountPostings.calls().contains("postings:" + AccountPostedPublisher.aggregateId(thirdPosting)
                + "@3/2"), "第二次从序号 3 读取: " + accountPostings.calls()));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 3);
    }

    /** 已追平：从已消费序号 + 1 读取到空页，零处理。 */
    @Test
    void test_alreadyCaughtUp() {
        BillingFactPayload fact = clientCharge("1");
        AccountPostedPayload posting = clientCredit(fact, 1, "1");
        postBatch(fact, posting);
        accountPostings.mockPostings(posting);

        catchUp(null).assertSuccess(200).assertText("processedCount", "0").assertText("lastSequence", "1");

        assertEquals(1, testMapper.countAllInstructions());
    }

    /** account 不可用：503，不推进进度。 */
    @Test
    void test_accountUnavailable() {
        accountPostings.mockPostingsFailure(streamId(), AccountPostingMocker.unavailable());

        catchUp(null).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());

        assertEquals(0, testMapper.countAllConsumedEvents());
    }

    /** billing 不可用：已处理的桶移动批次保留，事实批次 503 停止。 */
    @Test
    void test_billingUnavailableKeepsProcessedBatches() {
        AccountPostedPayload movement = AccountPostingBuilder.movement(ENTITY_NO, CURRENCY, 1, CLIENT, "5").build();
        BillingFactPayload fact = clientCharge("8");
        AccountPostedPayload posting = clientCredit(fact, 2, "8");
        accountPostings.mockPostings(movement, posting);
        billingFacts.mockFactFailure(fact.getFactId(), BillingFactMocker.unavailable());

        catchUp(null).assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());

        assertAll(() -> assertEquals(1, consumedSequence(movement), "已处理的桶移动批次保留"),
            () -> assertEquals(0, testMapper.countAllInstructions()));
    }

    /** 流身份或批次数不符合请求体约束：422 字段错误，不访问 account。 */
    @Test
    void test_invalidParameter() {
        CatchUpPostingStreamRequestBody body = body(null);
        body.setEntityType(OwnerType.CLIENT.getCode());
        postAsOperator(PATH, body).assertProblem(422, "VALIDATION_ERROR").assertFieldError("entityType");
        postAsOperator(PATH, body(10001)).assertProblem(422, "VALIDATION_ERROR").assertFieldError("maxBatches");
        assertTrue(accountPostings.calls().isEmpty());
    }

    /** 发送追平命令。 */
    LedgerResponseChecker catchUp(Integer maxBatches) {
        return postAsOperator(PATH, body(maxBatches));
    }

    /** AE001 / USD / 分片 0 的追平请求。 */
    static CatchUpPostingStreamRequestBody body(Integer maxBatches) {
        CatchUpPostingStreamRequestBody body = new CatchUpPostingStreamRequestBody();
        body.setEntityType(AccountEntityType.OPERATING_ENTITY.getCode());
        body.setEntityId(ENTITY_NO);
        body.setCurrency(CURRENCY);
        body.setShardNo(0);
        body.setMaxBatches(maxBatches);
        return body;
    }

    /** 测试过账流的聚合 ID。 */
    static String streamId() {
        return AccountPostedPayload.aggregateId(AccountEntityType.OPERATING_ENTITY, ENTITY_NO, CURRENCY, 0);
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
