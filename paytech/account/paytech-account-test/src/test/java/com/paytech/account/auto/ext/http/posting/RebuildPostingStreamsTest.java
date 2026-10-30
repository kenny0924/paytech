package com.paytech.account.auto.ext.http.posting;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.AccountPostingApi;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.AccountPostingErrorCode;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.request.RebuildPostingStreamsRequestBody;
import com.paytech.account.api.response.PostingStreamsRebuildResponseBody;
import com.paytech.account.test.base.AbstractBalanceTest;

/**
 * 按入账流重建余额：经真实 HTTP 入口锁流行、按 t_account_entry 重算全部账户并以旧版本 CAS 加一（设计 4.5）
 * <li>被篡改的余额恢复为流水合计，余额版本加一，不删不改流水；主体与币种下没有流时为 STREAM_NOT_FOUND。</li>
 * <li>changedAccountCount 只统计余额实际被纠正的账户：数据一致时为 0，篡改一个账户后为 1。</li>
 *
 * @see AccountPostingApi#rebuildPostingStreams(RebuildPostingStreamsRequestBody)
 */
class RebuildPostingStreamsTest extends AbstractBalanceTest {
    private static final String PATH = "/account-postings/v1/streams/rebuild";

    /** 余额被篡改后重建：恢复为流水合计，版本加一，流水与批次不变。 */
    @Test
    void test_rebuildRestoresBalances() {
        consumed(clientCredit("10").build());
        consumed(clientCredit("2.5").build());
        Map<String, Object> before = account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING);
        long accountId = ((Number)before.get("id")).longValue();
        long version = ((Number)before.get("balance_version")).longValue();
        testMapper.tamperBalance(accountId, new BigDecimal("999"), BigDecimal.ZERO, new BigDecimal("-999"));

        PostingStreamsRebuildResponseBody body = post(PATH, request(ENTITY_NO)).assertSuccess(200)
            .as(PostingStreamsRebuildResponseBody.class);

        assertAll(() -> assertEquals("1", body.getAccountCount()), () -> assertEquals("1", body.getChangedAccountCount()),
            () -> assertEquals("2", body.getEntryCount()),
            () -> assertEquals("2", body.getMaxPostingSequence()),
            () -> assertEquals(version + 1, ((Number)account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING)
                .get("balance_version")).longValue()),
            () -> assertEquals(2, testMapper.selectPostings(AccountEntityType.OPERATING_ENTITY.getCode(), ENTITY_NO,
                CURRENCY).size()));
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "12.5", "12.5");
        posted.await(2);
    }

    /** 余额与流水一致时重建：账户照常重算、版本加一，但没有账户被纠正，余额不变。 */
    @Test
    void test_rebuildConsistentBalancesChangesNothing() {
        consumed(clientCredit("10").build());
        long version = ((Number)account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING)
            .get("balance_version")).longValue();

        PostingStreamsRebuildResponseBody body = post(PATH, request(ENTITY_NO)).assertSuccess(200)
            .as(PostingStreamsRebuildResponseBody.class);

        assertAll(() -> assertEquals("1", body.getAccountCount()), () -> assertEquals("0", body.getChangedAccountCount()),
            () -> assertEquals("1", body.getEntryCount()), () -> assertEquals("1", body.getMaxPostingSequence()),
            () -> assertEquals(version + 1, ((Number)account(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING)
                .get("balance_version")).longValue()));
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING, "0", "10", "10");
        posted.await(1);
    }

    /** 主体与币种下没有入账流：400 STREAM_NOT_FOUND；缺少操作人为请求体字段校验失败 422。 */
    @Test
    void test_streamNotFoundAndInvalid() {
        post(PATH, request("AE002")).assertRejected(AccountPostingErrorCode.STREAM_NOT_FOUND);

        RebuildPostingStreamsRequestBody missingOperator = request(ENTITY_NO);
        missingOperator.setOperatorId(null);
        post(PATH, missingOperator).assertValidationError();
    }

    /** AE001 之外主体的重建请求。 */
    private static RebuildPostingStreamsRequestBody request(String entityNo) {
        RebuildPostingStreamsRequestBody body = new RebuildPostingStreamsRequestBody();
        body.setEntityType(AccountEntityType.OPERATING_ENTITY.getCode());
        body.setEntityId(entityNo);
        body.setCurrency(CURRENCY);
        body.setOperatorId("90001");
        return body;
    }
}
