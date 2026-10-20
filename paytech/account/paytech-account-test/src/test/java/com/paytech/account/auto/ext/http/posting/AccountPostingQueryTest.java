package com.paytech.account.auto.ext.http.posting;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.AccountPostingErrorCode;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.FactApplyStatus;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.enums.PostingSourceType;
import com.paytech.account.api.response.AccountPostingListResponseBody;
import com.paytech.account.api.response.AccountPostingProgressResponseBody;
import com.paytech.account.api.response.AccountPostingResponseBody;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.money.Money;

/**
 * 入账进度、批次列表（ledger 追平）与批次详情：经真实 HTTP 入口读取本地 MySQL，批次由消费 BillingFactRecorded 生成
 * <li>进度只计自身事件已到达的事实（排除 PENDING_SUPERSEDED）；FINANCE_ENTITY 主体的流包含引用它的事实所在的 OE 流；没有流时为空。</li>
 * <li>列表按序号升序、满页给出下一页起始序号，limit 超过 200 为 400；详情不存在为 POSTING_NOT_FOUND。</li>
 *
 * @see AccountPostingApi#getPostingProgress(String, String, String)
 * @see AccountPostingApi#listPostings(String, String, String, String, String, String)
 * @see AccountPostingApi#getPosting(String)
 */
class AccountPostingQueryTest extends AbstractBalanceTest {
    private static final String PROGRESS = "/account-postings/v1/progress?entityType=%s&entityId=%s&currency=USD";

    private static final String LIST = "/account-postings/v1?entityType=" + AccountEntityType.OPERATING_ENTITY.getCode()
        + "&entityId=%s&currency=USD&shardNo=0&fromSequence=%s";

    /** OE 进度：三条已到达事实（替代先到的占位不计），一条流最大序号 3。 */
    @Test
    void test_progressForOperatingEntity() {
        BillingFactPayload partnerFact = partnerPayment();
        BillingFactPayload plain = clientCredit("1").build();
        BillingFactPayload v2 = clientCredit("2").supersedes(nextFactId(), 2).build();
        consumed(partnerFact);
        consumed(plain);
        consumed(v2);

        AccountPostingProgressResponseBody body = get(PROGRESS.formatted(AccountEntityType.OPERATING_ENTITY.getCode(),
            ENTITY_NO)).assertSuccess(200).as(AccountPostingProgressResponseBody.class);

        assertAll(() -> assertEquals("3", body.getAppliedFactCount()),
            () -> assertEquals(Long.toString(v2.getFactId()), body.getMaxAppliedFactId()),
            () -> assertEquals(1, body.getStreams().size()),
            () -> assertEquals(ENTITY_NO, body.getStreams().getFirst().getEntityId()),
            () -> assertEquals("0", body.getStreams().getFirst().getShardNo()),
            () -> assertEquals("3", body.getStreams().getFirst().getMaxPostingSequence()));
        posted.await(3);
    }

    /** Finance Entity 进度：只计引用它的事实，流为这些事实所在的 OE 流。 */
    @Test
    void test_progressForFinanceEntity() {
        BillingFactPayload partnerFact = partnerPayment();
        consumed(partnerFact);
        consumed(clientCredit("1").build());

        AccountPostingProgressResponseBody body = get(PROGRESS.formatted(AccountEntityType.FINANCE_ENTITY.getCode(),
            FINANCE_ENTITY_ID)).assertSuccess(200).as(AccountPostingProgressResponseBody.class);

        assertAll(() -> assertEquals("1", body.getAppliedFactCount()),
            () -> assertEquals(Long.toString(partnerFact.getFactId()), body.getMaxAppliedFactId()),
            () -> assertEquals(1, body.getStreams().size()),
            () -> assertEquals(AccountEntityType.OPERATING_ENTITY.getCode(), body.getStreams().getFirst().getEntityType()),
            () -> assertEquals("2", body.getStreams().getFirst().getMaxPostingSequence()));
        posted.await(2);
    }

    /** 没有任何事实与流：计数 0、最大值为空、流为空列表；主体标识与类型不符为参数错误。 */
    @Test
    void test_progressEmptyAndInvalid() {
        AccountPostingProgressResponseBody body = get(PROGRESS.formatted(AccountEntityType.OPERATING_ENTITY.getCode(),
            "AE002")).assertSuccess(200).as(AccountPostingProgressResponseBody.class);

        assertAll(() -> assertEquals("0", body.getAppliedFactCount()), () -> assertNull(body.getMaxAppliedFactId()),
            () -> assertTrue(body.getStreams().isEmpty()));
        get(PROGRESS.formatted(AccountEntityType.FINANCE_ENTITY.getCode(), ENTITY_NO)).assertInvalidRequest();
    }

    /** 追平列表：按序号升序分页，满页给出下一页起点，末页为空；超过 200 拒绝；流不存在为空页。 */
    @Test
    void test_listPostingsForCatchUp() {
        consumed(clientCredit("1").build());
        consumed(clientCredit("2").build());
        consumed(clientCredit("3").build());

        AccountPostingListResponseBody page = get(LIST.formatted(ENTITY_NO, "2") + "&limit=1").assertSuccess(200)
            .as(AccountPostingListResponseBody.class);
        AccountPostingListResponseBody tail = get(LIST.formatted(ENTITY_NO, "2")).assertSuccess(200)
            .as(AccountPostingListResponseBody.class);
        AccountPostingListResponseBody empty = get(LIST.formatted("AE002", "1")).assertSuccess(200)
            .as(AccountPostingListResponseBody.class);

        assertAll(() -> assertEquals(List.of("2"), page.getItems().stream().map(AccountPostingResponseBody::getPostingSequence)
            .toList()), () -> assertEquals("3", page.getNextFromSequence()),
            () -> assertEquals(List.of("2", "3"), tail.getItems().stream().map(AccountPostingResponseBody::getPostingSequence)
                .toList()), () -> assertNull(tail.getNextFromSequence()),
            () -> assertEquals(1, tail.getItems().getFirst().getEntries().size()),
            () -> assertTrue(empty.getItems().isEmpty()), () -> assertNull(empty.getNextFromSequence()));
        get(LIST.formatted(ENTITY_NO, "1") + "&limit=201").assertInvalidRequest();
        posted.await(3);
    }

    /** 批次详情：批次头（含 Broker 确认后的发布时间）、来源事实与带账户号、科目、Owner、桶的流水；不存在为 POSTING_NOT_FOUND。 */
    @Test
    void test_getPostingDetail() {
        BillingFactPayload fact = partnerPayment();
        consumed(fact);
        String postingId = factPosting(fact.getFactId()).get("id").toString();
        posted.await(1);
        await().atMost(CONSUME_WAIT).until(() -> testMapper.countCompensationEvents() == 0);
        Instant publishedTime = ((LocalDateTime)factPosting(fact.getFactId()).get("published_time"))
            .toInstant(ZoneOffset.UTC);

        AccountPostingResponseBody body = get("/account-postings/v1/" + postingId).assertSuccess(200)
            .as(AccountPostingResponseBody.class);

        AccountPostingResponseBody.Entry client = body.getEntries().getFirst();
        assertAll(() -> assertEquals(postingId, body.getPostingId()), () -> assertEquals("1", body.getPostingSequence()),
            () -> assertEquals(PostingSourceType.ECONOMIC_FACT.getCode(), body.getSourceType()),
            () -> assertEquals(Long.toString(fact.getFactId()), body.getFactId()),
            () -> assertEquals(FactApplyStatus.APPLIED.getCode(), body.getFactApplyStatus()),
            () -> assertEquals(Long.toString(FINANCE_ENTITY_ID), body.getFinanceEntityId()),
            () -> assertEquals(publishedTime, body.getPublishedTime()),
            () -> assertNull(body.getMovementId()), () -> assertEquals(2, body.getEntries().size()),
            () -> assertEquals(OwnerAccountType.CLIENT_PAYABLE.getCode(), client.getAccountCode()),
            () -> assertEquals(account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING).get("account_no"),
                client.getAccountNo()),
            () -> assertEquals(CLIENT, client.getOwnerId()),
            () -> assertEquals(BalanceBucket.PENDING.getCode(), client.getBalanceBucket()),
            () -> assertEquals(EntrySide.CREDIT.getCode(), client.getEntrySide()),
            () -> assertEquals(Money.of(CURRENCY, "94"), client.getAmount()),
            () -> assertEquals("1", client.getFactLineNo()));
        get("/account-postings/v1/9999").assertRejected(AccountPostingErrorCode.POSTING_NOT_FOUND);
    }

    /** 带合作方的交易事实：Client 94、Partner 1，平台服务费收取方为 Finance Entity。 */
    private static BillingFactPayload partnerPayment() {
        return payment("100").partner(PARTNER_ID, FINANCE_ENTITY_ID)
            .component(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, FactPartyRole.CLIENT, "94")
            .component(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY,
                FactPartyRole.LOCAL_PARTNER, "1").build();
    }
}
