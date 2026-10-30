package com.paytech.ledger.auto.ext.http.progress;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.ledger.api.PostingProgressQueryApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.enums.PostingProgressStatus;
import com.paytech.ledger.api.response.PostingProgressResponseBody;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.external.AccountPostingMocker;
import com.paytech.ledger.test.mocker.external.AccountPostingMocker.StreamProgress;
import com.paytech.ledger.test.mocker.external.BillingFactMocker;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 账簿入账进度查询：两段判定与关账步骤 3 同口径、billing / account 不可用只影响对应段、Finance Entity 账簿含 OE 流、账簿不存在。
 *
 * @see PostingProgressQueryApi#getPostingProgress(String)
 */
class GetPostingProgressTest extends MockTestBase {
    private static final String PATH = "/posting-progress/v1/ledger-books/";

    private static final String OE = LedgerEntityType.OPERATING_ENTITY.getCode();

    private static final String FE = LedgerEntityType.FINANCE_ENTITY.getCode();

    private static final String ENTITY_NO = "AE001";

    /** account 最后入账时间。 */
    private static final Instant LAST_POSTED = Instant.parse("2026-10-30T09:12:03.120000Z");

    @Autowired
    private AccountingTestMapper testMapper;

    private long bookId;

    private String streamId;

    @BeforeEach
    void setUpClassMocks() {
        bookId = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, ENTITY_NO, USD, LedgerMocker.STANDARD_CHART_ID);
        streamId = AccountPostedPayload.aggregateId(AccountEntityType.OPERATING_ENTITY, ENTITY_NO, USD, 0);
    }

    /** 两段都已追平：事实数与最大 factId 相同、已消费序号等于 account 最大序号；account 最后入账时间原样带出。 */
    @Test
    void test_consistent() {
        billingFacts.mockProgress(OE, ENTITY_NO, USD, 0, 2, 9002L);
        accountPostings.mockProgress(OE, ENTITY_NO, USD, 2, 9002L,
            new StreamProgress(OE, ENTITY_NO, USD, 0, 5, LAST_POSTED));
        testMapper.insertConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE, streamId, 5);

        PostingProgressResponseBody body = query(bookId);

        assertAll(() -> assertEquals(Long.toString(bookId), body.getLedgerBookId()),
            () -> assertEquals(OE, body.getEntityType()), () -> assertEquals(ENTITY_NO, body.getEntityId()),
            () -> assertEquals(USD, body.getCurrency()));
        PostingProgressResponseBody.FactSegment fact = body.getFactSegment();
        assertAll(() -> assertEquals(PostingProgressStatus.CONSISTENT.getCode(), fact.getStatus()),
            () -> assertEquals("0", fact.getBillingPendingCount()), () -> assertNull(fact.getBillingMaxPendingFactId()),
            () -> assertEquals("2", fact.getBillingPublishedCount()),
            () -> assertEquals("9002", fact.getBillingMaxPublishedFactId()),
            () -> assertEquals("2", fact.getAccountAppliedCount()),
            () -> assertEquals("9002", fact.getAccountMaxAppliedFactId()));
        assertEquals(PostingProgressStatus.CONSISTENT.getCode(), body.getStreamSegment().getStatus());
        PostingProgressResponseBody.Stream stream = onlyStream(body);
        assertAll(() -> assertEquals(streamId, stream.getStreamId()), () -> assertEquals(OE, stream.getEntityType()),
            () -> assertEquals(ENTITY_NO, stream.getEntityId()), () -> assertEquals(USD, stream.getCurrency()),
            () -> assertEquals("0", stream.getShardNo()), () -> assertEquals("5", stream.getAccountMaxSequence()),
            () -> assertEquals(LAST_POSTED, stream.getAccountLastPostedTime()),
            () -> assertEquals("5", stream.getLedgerConsumedSequence()), () -> assertEquals("0", stream.getLag()),
            () -> assertEquals(PostingProgressStatus.CONSISTENT.getCode(), stream.getStatus()));
    }

    /** 段 ①：billing 仍有未发布事实、account 少应用一条时为 BEHIND，并带出未发布最大 factId；过账流段不受影响。 */
    @Test
    void test_factSegmentBehind() {
        billingFacts.mockProgress(OE, ENTITY_NO, USD, 1, 9003L, 2, 9002L);
        accountPostings.mockProgress(OE, ENTITY_NO, USD, 1, 9001L, new StreamProgress(OE, ENTITY_NO, USD, 0, 4));
        testMapper.insertConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE, streamId, 4);

        PostingProgressResponseBody body = query(bookId);

        PostingProgressResponseBody.FactSegment fact = body.getFactSegment();
        assertAll(() -> assertEquals(PostingProgressStatus.BEHIND.getCode(), fact.getStatus()),
            () -> assertEquals("1", fact.getBillingPendingCount()),
            () -> assertEquals("9003", fact.getBillingMaxPendingFactId()),
            () -> assertEquals("1", fact.getAccountAppliedCount()),
            () -> assertEquals("9001", fact.getAccountMaxAppliedFactId()),
            () -> assertEquals(PostingProgressStatus.CONSISTENT.getCode(), body.getStreamSegment().getStatus()));
    }

    /** 段 ②：一条流落后 2 批、另一条从未消费（序号 0），段判定取最差的 BEHIND。 */
    @Test
    void test_streamSegmentBehind() {
        billingFacts.mockProgress(OE, ENTITY_NO, USD, 0, 0, null);
        accountPostings.mockProgress(OE, ENTITY_NO, USD, 0, null, new StreamProgress(OE, ENTITY_NO, USD, 0, 5),
            new StreamProgress(OE, ENTITY_NO, USD, 1, 3));
        testMapper.insertConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE, streamId, 3);

        PostingProgressResponseBody body = query(bookId);

        assertEquals(PostingProgressStatus.CONSISTENT.getCode(), body.getFactSegment().getStatus());
        assertEquals(PostingProgressStatus.BEHIND.getCode(), body.getStreamSegment().getStatus());
        List<PostingProgressResponseBody.Stream> streams = body.getStreamSegment().getStreams();
        assertEquals(2, streams.size());
        PostingProgressResponseBody.Stream behind = streams.get(0);
        PostingProgressResponseBody.Stream neverConsumed = streams.get(1);
        assertAll(() -> assertEquals(streamId, behind.getStreamId()),
            () -> assertEquals("3", behind.getLedgerConsumedSequence()), () -> assertEquals("2", behind.getLag()),
            () -> assertEquals(PostingProgressStatus.BEHIND.getCode(), behind.getStatus()),
            () -> assertEquals(AccountPostedPayload.aggregateId(AccountEntityType.OPERATING_ENTITY, ENTITY_NO, USD, 1),
                neverConsumed.getStreamId()),
            () -> assertEquals("1", neverConsumed.getShardNo()),
            () -> assertEquals("0", neverConsumed.getLedgerConsumedSequence()),
            () -> assertEquals("3", neverConsumed.getLag()),
            () -> assertEquals(PostingProgressStatus.BEHIND.getCode(), neverConsumed.getStatus()));
    }

    /** billing 不可用：只有计费事实段为 UNAVAILABLE 且数字为空，过账流段照常给出。 */
    @Test
    void test_billingUnavailable() {
        billingFacts.mockProgressFailure(OE, ENTITY_NO, USD, BillingFactMocker.unavailable());
        accountPostings.mockProgress(OE, ENTITY_NO, USD, 2, 9002L, new StreamProgress(OE, ENTITY_NO, USD, 0, 5));
        testMapper.insertConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE, streamId, 5);

        PostingProgressResponseBody body = query(bookId);

        PostingProgressResponseBody.FactSegment fact = body.getFactSegment();
        assertAll(() -> assertEquals(PostingProgressStatus.UNAVAILABLE.getCode(), fact.getStatus()),
            () -> assertNull(fact.getBillingPendingCount()), () -> assertNull(fact.getBillingPublishedCount()),
            () -> assertNull(fact.getAccountAppliedCount()),
            () -> assertEquals(PostingProgressStatus.CONSISTENT.getCode(), body.getStreamSegment().getStatus()),
            () -> assertEquals("5", onlyStream(body).getLedgerConsumedSequence()));
    }

    /** account 不可用：计费事实段与过账流段都为 UNAVAILABLE，流清单为空，接口本身仍 200。 */
    @Test
    void test_accountUnavailable() {
        billingFacts.mockProgress(OE, ENTITY_NO, USD, 0, 2, 9002L);
        accountPostings.mockProgressFailure(OE, ENTITY_NO, USD, AccountPostingMocker.unavailable());

        PostingProgressResponseBody body = query(bookId);

        assertAll(() -> assertEquals(PostingProgressStatus.UNAVAILABLE.getCode(), body.getFactSegment().getStatus()),
            () -> assertNull(body.getFactSegment().getBillingPublishedCount()),
            () -> assertEquals(PostingProgressStatus.UNAVAILABLE.getCode(), body.getStreamSegment().getStatus()),
            () -> assertEquals(List.of(), body.getStreamSegment().getStreams()));
    }

    /** Finance Entity 账簿：account 进度同时列出 FE 自身流与服务费所在 OE 流，OE 流落后时段判定为 BEHIND。 */
    @Test
    void test_financeEntityBookIncludesOperatingEntityStream() {
        long financeEntityId = ledgerMocker.mockFinanceEntity("FE-PLATFORM", 880001L, FinanceEntityStatus.ACTIVE);
        String feId = String.valueOf(financeEntityId);
        long feBook = ledgerMocker.mockBook(LedgerEntityType.FINANCE_ENTITY, feId, USD, LedgerMocker.STANDARD_CHART_ID);
        String feStream = AccountPostedPayload.aggregateId(AccountEntityType.FINANCE_ENTITY, feId, USD, 0);
        billingFacts.mockProgress(FE, feId, USD, 0, 1, 9101L);
        accountPostings.mockProgress(FE, feId, USD, 1, 9101L, new StreamProgress(FE, feId, USD, 0, 2),
            new StreamProgress(OE, ENTITY_NO, USD, 0, 7));
        testMapper.insertConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE, feStream, 2);
        testMapper.insertConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE, streamId, 6);

        PostingProgressResponseBody body = query(feBook);

        List<PostingProgressResponseBody.Stream> streams = body.getStreamSegment().getStreams();
        assertEquals(2, streams.size());
        assertAll(() -> assertEquals(FE, body.getEntityType()), () -> assertEquals(feId, body.getEntityId()),
            () -> assertEquals(PostingProgressStatus.CONSISTENT.getCode(), body.getFactSegment().getStatus()),
            () -> assertEquals(PostingProgressStatus.BEHIND.getCode(), body.getStreamSegment().getStatus()),
            () -> assertEquals(feStream, streams.get(0).getStreamId()),
            () -> assertEquals(PostingProgressStatus.CONSISTENT.getCode(), streams.get(0).getStatus()),
            () -> assertEquals(streamId, streams.get(1).getStreamId()), () -> assertEquals(OE, streams.get(1).getEntityType()),
            () -> assertEquals("1", streams.get(1).getLag()),
            () -> assertEquals(PostingProgressStatus.BEHIND.getCode(), streams.get(1).getStatus()));
    }

    /** 账簿不存在为 400 LEDGER_BOOK_NOT_FOUND，不调用 billing / account；路径不是十进制主键为参数错误。 */
    @Test
    void test_bookNotFoundAndInvalid() {
        get(PATH + "123456789").assertRejected(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
        get(PATH + "0").assertInvalidRequest();

        assertAll(() -> assertEquals(List.of(), accountPostings.calls()),
            () -> assertEquals(List.of(), billingFacts.calls()));
    }

    /** 发起查询并读取响应。 */
    PostingProgressResponseBody query(long ledgerBookId) {
        return get(PATH + ledgerBookId).assertSuccess(200).as(PostingProgressResponseBody.class);
    }

    /** 只有一条流。 */
    static PostingProgressResponseBody.Stream onlyStream(PostingProgressResponseBody body) {
        assertEquals(1, body.getStreamSegment().getStreams().size());
        return body.getStreamSegment().getStreams().getFirst();
    }
}
