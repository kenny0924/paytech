package com.paytech.account.auto.ext.http.movement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.BalanceMovementApi;
import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.BalanceMovementErrorCode;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.enums.PostingSourceType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.account.api.request.BalanceMovementRequestBody;
import com.paytech.account.api.response.BalanceMovementResponseBody;
import com.paytech.account.core.enums.AccountStatus;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.test.builder.BalanceMovementRequestBuilder;
import com.paytech.account.test.probe.AccountPostedProbe;

/**
 * 余额桶移动命令：经真实 HTTP 入口、MoveBalanceProcessor、BalanceMovementService 与本地 MySQL，AccountPosted 经 account.events 观察
 * <li>首次 201、同来源重复 200 replayed；余额不足、非法桶对 / 科目组合、账户不存在、账户冻结均为 400 专用错误码；CASH_ACCOUNT 的锁定与释放。</li>
 * <li>前置账户由 OwnerAccountMocker 插入（本期没有释放 AVAILABLE 与 BANK_CASH 的事实来源）。</li>
 *
 * @see BalanceMovementApi#moveBalance(BalanceMovementRequestBody)
 */
class BalanceMovementTest extends AbstractBalanceTest {
    private static final String PATH = "/balance-movements/v1";

    private static final String BANK_ACCOUNT = "770001";

    /** 首次移动：201，转出桶借记、转入桶贷记（Client 应付正常方向贷方），转入账户自动开立，生成两条流水的 BALANCE_MOVEMENT 批次。 */
    @Test
    void test_firstMovementCreated() {
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "100");
        BalanceMovementRequestBody request = BalanceMovementRequestBuilder.template().amount("30").build();

        BalanceMovementResponseBody body = post(PATH, request).assertSuccess(201).as(BalanceMovementResponseBody.class);

        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "30", "100", "70");
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.LOCKED, "0", "30", "30");
        Map<String, Object> movement = testMapper.selectMovements().getFirst();
        assertAll(() -> assertTrue(!body.isReplayed()), () -> assertEquals("1", body.getPostingSequence()),
            () -> assertEquals(movement.get("id").toString(), body.getMovementId()),
            () -> assertEquals(MovementSourceType.PAYOUT_LOCK.getCode(), movement.get("source_type")),
            () -> assertEquals(90001L, ((Number)movement.get("operator_id")).longValue()));

        AccountPostedPayload payload = posted.await(1).getFirst().payload();
        assertAll(() -> assertEquals(PostingSourceType.BALANCE_MOVEMENT.getCode(), payload.getSourceType()),
            () -> assertEquals(Long.parseLong(body.getMovementId()), payload.getMovementId()),
            () -> assertEquals(MovementSourceType.PAYOUT_LOCK.getCode(), payload.getMovementSourceType()),
            () -> assertEquals(request.getSourceRef(), payload.getMovementSourceRef()),
            () -> assertNull(payload.getFactId()), () -> assertNull(payload.getFactApplyStatus()),
            () -> assertEquals(2, payload.getEntries().size()),
            () -> assertEquals(BalanceBucket.AVAILABLE.getCode(), payload.getEntries().get(0).getBalanceBucket()),
            () -> assertEquals(EntrySide.DEBIT.getCode(), payload.getEntries().get(0).getEntrySide()),
            () -> assertEquals(BalanceBucket.LOCKED.getCode(), payload.getEntries().get(1).getBalanceBucket()),
            () -> assertEquals(EntrySide.CREDIT.getCode(), payload.getEntries().get(1).getEntrySide()),
            () -> assertNull(payload.getEntries().get(0).getFactLineNo()));
    }

    /** 同一来源重复请求：200 replayed，返回原移动与批次，不再移动、不再发布。 */
    @Test
    void test_sameSourceReplayed() {
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "100");
        BalanceMovementRequestBody request = BalanceMovementRequestBuilder.template().amount("30").build();
        BalanceMovementResponseBody first = post(PATH, request).assertSuccess(201).as(BalanceMovementResponseBody.class);

        BalanceMovementResponseBody again = post(PATH, request).assertSuccess(200).as(BalanceMovementResponseBody.class);

        assertAll(() -> assertTrue(again.isReplayed()), () -> assertEquals(first.getMovementId(), again.getMovementId()),
            () -> assertEquals(first.getPostingId(), again.getPostingId()),
            () -> assertEquals(first.getPostingSequence(), again.getPostingSequence()),
            () -> assertEquals(1, testMapper.selectMovements().size()));
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "30", "100", "70");
        posted.await(1);
    }

    /** 转出桶余额不足：400 BALANCE_INSUFFICIENT，整个事务回滚，不留下移动、批次或发布任务。 */
    @Test
    void test_insufficientBalance() {
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "10");

        post(PATH, BalanceMovementRequestBuilder.template().amount("10.01").build())
            .assertRejected(BalanceMovementErrorCode.BALANCE_INSUFFICIENT);

        assertRolledBack();
        assertBalance(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "0", "10", "10");
    }

    /** 桶方向与来源类型不符、无桶科目、科目与 Owner 类型不符：400 MOVEMENT_NOT_ALLOWED。 */
    @Test
    void test_movementNotAllowed() {
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.AVAILABLE, "100");

        post(PATH, BalanceMovementRequestBuilder.template().buckets(BalanceBucket.LOCKED, BalanceBucket.AVAILABLE).build())
            .assertRejected(BalanceMovementErrorCode.MOVEMENT_NOT_ALLOWED);
        post(PATH, BalanceMovementRequestBuilder.template().account(OwnerAccountType.CLIENT_SECURITY_DEPOSIT, CLIENT).build())
            .assertRejected(BalanceMovementErrorCode.MOVEMENT_NOT_ALLOWED);
        post(PATH, BalanceMovementRequestBuilder.template().ownerType(OwnerType.LOCAL_PARTNER.getCode()).build())
            .assertRejected(BalanceMovementErrorCode.MOVEMENT_NOT_ALLOWED);

        assertRolledBack();
    }

    /** FACT_MATURITY 只能内部发起（请求体字段校验 422）；金额币种不一致、金额非正、主体标识与类型不符：400 参数错误。 */
    @Test
    void test_invalidParameter() {
        post(PATH, BalanceMovementRequestBuilder.template().sourceCode(MovementSourceType.FACT_MATURITY.getCode())
            .buckets(BalanceBucket.PENDING, BalanceBucket.AVAILABLE).build()).assertValidationError();
        post(PATH, BalanceMovementRequestBuilder.template().amount("EUR", "1").build()).assertInvalidRequest();
        post(PATH, BalanceMovementRequestBuilder.template().amount("0").build()).assertInvalidRequest();
        post(PATH, BalanceMovementRequestBuilder.template().entity(AccountEntityType.FINANCE_ENTITY, "AE001").build())
            .assertInvalidRequest();

        assertRolledBack();
    }

    /** 转出账户不存在：400 ACCOUNT_NOT_FOUND。 */
    @Test
    void test_accountNotFound() {
        post(PATH, BalanceMovementRequestBuilder.template().build())
            .assertRejected(BalanceMovementErrorCode.ACCOUNT_NOT_FOUND);

        assertRolledBack();
        assertTrue(testMapper.selectAccounts().isEmpty(), "转入账户不应在失败事务中留下");
    }

    /** 转出账户冻结：400 ACCOUNT_NOT_ACTIVE。 */
    @Test
    void test_frozenAccountRejected() {
        long accountId = accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.CLIENT_PAYABLE, CLIENT,
            BalanceBucket.AVAILABLE, "100");
        accounts.status(accountId, AccountStatus.FROZEN);

        post(PATH, BalanceMovementRequestBuilder.template().build())
            .assertRejected(BalanceMovementErrorCode.ACCOUNT_NOT_ACTIVE);

        assertRolledBack();
    }

    /** 银行现金容量：缴税锁定 AVAILABLE → LOCKED 后部分释放；BANK_CASH 正常方向借方，转出桶贷记、转入桶借记。 */
    @Test
    void test_cashAccountLockAndRelease() {
        accounts.mockAccount(ENTITY_NO, CURRENCY, OwnerAccountType.BANK_CASH, BANK_ACCOUNT, BalanceBucket.AVAILABLE, "1000");

        post(PATH, BalanceMovementRequestBuilder.template().source(MovementSourceType.TAX_PAYMENT_LOCK)
            .account(OwnerAccountType.BANK_CASH, BANK_ACCOUNT).amount("400").build()).assertSuccess(201);
        post(PATH, BalanceMovementRequestBuilder.template().source(MovementSourceType.TAX_PAYMENT_RELEASE)
            .account(OwnerAccountType.BANK_CASH, BANK_ACCOUNT).amount("150").build()).assertSuccess(201);

        assertBalance(OwnerAccountType.BANK_CASH, BANK_ACCOUNT, BalanceBucket.AVAILABLE, "1150", "400", "750");
        assertBalance(OwnerAccountType.BANK_CASH, BANK_ACCOUNT, BalanceBucket.LOCKED, "400", "150", "250");
        List<AccountPostedProbe.Posted> events = posted.await(2);
        AccountPostedProbe.assertContiguous(events, STREAM);
        AccountPostedPayload lock = events.getFirst().payload();
        assertAll(() -> assertEquals(EntrySide.CREDIT.getCode(), lock.getEntries().get(0).getEntrySide()),
            () -> assertEquals(OwnerType.CASH_ACCOUNT.getCode(), lock.getEntries().get(0).getOwnerType()),
            () -> assertEquals(EntrySide.DEBIT.getCode(), lock.getEntries().get(1).getEntrySide()));
    }

    /** 失败命令不留下移动、批次、入账流与发布任务。 */
    private void assertRolledBack() {
        assertAll(() -> assertTrue(testMapper.selectMovements().isEmpty()),
            () -> assertTrue(testMapper.selectPostings(AccountEntityType.OPERATING_ENTITY.getCode(), ENTITY_NO, CURRENCY)
                .isEmpty()),
            () -> assertNull(testMapper.selectStream(AccountEntityType.OPERATING_ENTITY.getCode(), ENTITY_NO, CURRENCY)),
            () -> assertEquals(0, testMapper.countCompensationEvents()));
    }
}
