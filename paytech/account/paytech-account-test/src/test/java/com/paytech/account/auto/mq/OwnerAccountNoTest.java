package com.paytech.account.auto.mq;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.app.ext.job.BalanceMaturityJobHandler;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.test.builder.BalanceMovementRequestBuilder;
import com.paytech.account.test.builder.BillingFactBuilder;
import com.paytech.account.test.probe.AccountPostedProbe;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;

import tools.jackson.databind.JsonNode;

/**
 * 明细账可读账户号：开户的同一 account 本地事务内按「主体 + 币种 + 科目代码」序列生成 {主体段}-{币种}-{科目编号}-S{序号≥6位}
 * <li>入口：计费事实消费（BillingFactRecorded）、到期转可用 Job 与桶移动 HTTP，三条开户路径共用 AccountPostingWriter。</li>
 * <li>覆盖：首次开户格式；同主体同科目第二个 Owner 序号递增；同 Owner 不同桶各自取号；已有账户再次入账账户号不变；Finance Entity 主体段
 * FE{entityId}；AccountPosted 载荷不含账户号（2026-10-19 科目分级与可读账户号决定第 7 条）。</li>
 *
 * @see com.paytech.account.domainservice.process.AccountPostingWriter#resolveAccount
 */
class OwnerAccountNoTest extends AbstractBalanceTest {
    private static final long OTHER_CLIENT_ID = 1002L;

    private static final String OTHER_CLIENT = String.valueOf(OTHER_CLIENT_ID);

    @Autowired
    private BalanceMaturityJobHandler job;

    /** 首次开户：Client 应付与 Partner 应付各取本科目序号 1；AccountPosted 载荷与流水行都没有账户号。 */
    @Test
    void test_firstOpenAssignsAccountNo() {
        consumed(payment("100").partner(PARTNER_ID, FINANCE_ENTITY_ID)
            .component(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, FactPartyRole.CLIENT, "94")
            .component(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY,
                FactPartyRole.LOCAL_PARTNER, "1").build());

        AccountPostedProbe.Posted event = posted.await(1).getFirst();
        assertAll(() -> assertEquals("AE001-USD-22010101-S000001", accountNo(OwnerAccountType.CLIENT_PAYABLE, CLIENT,
            BalanceBucket.PENDING)),
            () -> assertEquals(expected(OwnerAccountType.PARTNER_PAYABLE, 1), accountNo(OwnerAccountType.PARTNER_PAYABLE,
                PARTNER, BalanceBucket.PENDING)),
            () -> assertFalse(names(event.json()).contains("accountNo"), "AccountPosted 载荷不含账户号"),
            () -> event.json().path("entries").forEach(entry -> assertFalse(names(entry).contains("accountNo"),
                "AccountPosted 流水不含账户号")));
    }

    /** 同主体同科目的第二个 Owner：序号递增为 2；不同科目互不影响。 */
    @Test
    void test_secondOwnerIncrementsSequence() {
        consumed(clientCredit("1").build());
        consumed(BillingFactBuilder.adjustment(nextFactId(), OTHER_CLIENT_ID, ENTITY_NO, CURRENCY)
            .line(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                OTHER_CLIENT, FactLineDirection.CHARGE, "2").build());

        assertAll(() -> assertEquals(expected(OwnerAccountType.CLIENT_PAYABLE, 1), accountNo(
            OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING)),
            () -> assertEquals(expected(OwnerAccountType.CLIENT_PAYABLE, 2), accountNo(OwnerAccountType.CLIENT_PAYABLE,
                OTHER_CLIENT, BalanceBucket.PENDING)));
        posted.await(2);
    }

    /** 同一 Owner：再次入账不改账户号；到期开立 AVAILABLE、出款锁定开立 LOCKED，各自取下一个序号。 */
    @Test
    void test_eachBucketTakesOwnNumberAndReuseKeepsIt() {
        consumed(payment("10")
            .component(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, FactPartyRole.CLIENT, "10").balanceAvailableTime(past()).build());
        String pending = accountNo(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING);
        consumed(payment("5")
            .component(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, FactPartyRole.CLIENT, "5")
            .balanceAvailableTime(Instant.now().plus(1, ChronoUnit.DAYS)).build());
        job.runOnce();
        post("/balance-movements/v1", BalanceMovementRequestBuilder.template().amount("3").build()).assertSuccess(201);

        assertAll(() -> assertEquals(expected(OwnerAccountType.CLIENT_PAYABLE, 1), pending),
            () -> assertEquals(pending, accountNo(OwnerAccountType.CLIENT_PAYABLE, CLIENT, BalanceBucket.PENDING)),
            () -> assertEquals(expected(OwnerAccountType.CLIENT_PAYABLE, 2), accountNo(OwnerAccountType.CLIENT_PAYABLE,
                CLIENT, BalanceBucket.AVAILABLE)),
            () -> assertEquals(expected(OwnerAccountType.CLIENT_PAYABLE, 3), accountNo(OwnerAccountType.CLIENT_PAYABLE,
                CLIENT, BalanceBucket.LOCKED)),
            () -> assertEquals(3, testMapper.selectAccounts().size()));
        AccountPostedProbe.assertContiguous(posted.await(4), STREAM);
    }

    /** Finance Entity 主体：主体段为 FE 加主体主键，序列与 OE 主体互不影响。 */
    @Test
    void test_financeEntityAccountNo() {
        String financeEntity = Long.toString(FINANCE_ENTITY_ID);
        accounts.mockAccount(AccountEntityType.FINANCE_ENTITY, financeEntity, CURRENCY, OwnerAccountType.CLIENT_PAYABLE,
            CLIENT, BalanceBucket.AVAILABLE, "100");

        post("/balance-movements/v1", BalanceMovementRequestBuilder.template()
            .entity(AccountEntityType.FINANCE_ENTITY, financeEntity).build()).assertSuccess(201);

        Map<String, Object> locked = testMapper.selectAccount(AccountEntityType.FINANCE_ENTITY.getCode(), financeEntity,
            CURRENCY, OwnerAccountType.CLIENT_PAYABLE.getCode(), OwnerAccountType.CLIENT_PAYABLE.getOwnerType().getCode(),
            CLIENT, BalanceBucket.LOCKED.getCode());
        assertEquals("FE" + financeEntity + "-" + CURRENCY + "-" + OwnerAccountType.CLIENT_PAYABLE.getChartAccountNo()
            + "-S000001", locked.get("account_no"));
        posted.await(1);
    }

    /** AE001/USD 下一个账户的账户号。 */
    private String accountNo(OwnerAccountType type, String ownerId, BalanceBucket bucket) {
        return (String)account(type, ownerId, bucket).get("account_no");
    }

    /** AE001/USD 下某科目第 sequence 个账户号。 */
    private static String expected(OwnerAccountType type, int sequence) {
        return ENTITY_NO + "-" + CURRENCY + "-" + type.getChartAccountNo() + "-S" + String.format("%06d", sequence);
    }

    /** JSON 对象的字段名。 */
    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    /** 已到期的时间。 */
    private static Instant past() {
        return Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
    }
}
