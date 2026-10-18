package com.paytech.account.auto.mq;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.test.builder.EconomicFactBuilder;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactPayload;

/**
 * account 消费 EconomicFactRecorded 维护 Owner 余额（design/04 §5）：测试扮演 billing 向真实本机 RabbitMQ 的 billing.events 发布信封，经
 * account.billing 分区队列、paytech-messaging 幂等拦截器与 EconomicFactRecordedBalanceHandler 在 account 本地事务内应用余额。
 * <p>
 * 覆盖：交易事实按隐含角色映射（与 ledger STANDARD_TRANSACTION 的 Owner 行一致，三服务链路的 account 段）；同一事实重复投递只应用一次；
 * 版本 2 覆盖版本 1（顺序与乱序到达）；人工调整按付方 / 收方角色映射、平台侧角色不产生余额；REVERSAL 借贷互换；Owner 角色的未映射费用类型拒绝进入 DLQ。
 *
 * @see com.paytech.account.ext.mq.EconomicFactRecordedBalanceHandler
 */
class EconomicFactBalanceConsumeTest extends AbstractBalanceTest {
    private static final String CLIENT = String.valueOf(CLIENT_ID);

    private static final String PARTNER = String.valueOf(PARTNER_ID);

    private static final String CHANNEL = String.valueOf(CHANNEL_ACCOUNT_ID);

    /** 交易事实：总额记渠道应收借方，本金记 Client 应付 PENDING 贷方，渠道成本记渠道预提贷方；手续费与平台服务费不涉及 Owner 余额。 */
    @Test
    void test_paymentFactUpdatesOwnerBalances() {
        EconomicFactPayload fact = payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID)
            .component(AmountSource.CLIENT_PRINCIPAL, "94").component(AmountSource.CLIENT_FEE, "3")
            .component(AmountSource.CHANNEL_COST, "2").component(AmountSource.PARTNER_SHARE, "1")
            .component(AmountSource.PLATFORM_SERVICE_CHARGE, "0.5").build();

        consumed(fact);

        assertBalance(StandardAccountCodes.CHANNEL_UNSETTLED_RECEIVABLE, OwnerType.CHANNEL_ACCOUNT, CHANNEL,
            BalanceBucket.NONE, "100", "0", "100");
        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "0", "94", "94");
        assertBalance(StandardAccountCodes.CHANNEL_FEE_ACCRUAL, OwnerType.CHANNEL_ACCOUNT, CHANNEL, BalanceBucket.NONE,
            "0", "2", "2");
        assertBalance(StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER, PARTNER, BalanceBucket.PENDING,
            "0", "1", "1");
        assertAll(() -> assertEquals(4, testMapper.selectBalances().size(), "只建立 Owner 余额"),
            () -> assertEquals(AppliedFactStatus.APPLIED.getCode(), applyStatus(fact.getFactId())),
            () -> assertEquals(4, testMapper.countEntries(fact.getFactId())));
    }

    /** 同一事实（同一 eventId）重复投递：消费登记一次、余额只增加一次。 */
    @Test
    void test_duplicateDeliveryAppliedOnce() {
        EconomicFactPayload fact = payment("50").component(AmountSource.CLIENT_PRINCIPAL, "50").build();

        consumed(fact);
        billing.publish(fact);
        await().atMost(CONSUME_WAIT).until(() -> billing.pending() == 0);

        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "0", "50", "50");
        assertEquals(1, testMapper.countAllConsumedEvents());
    }

    /** 版本 2 覆盖版本 1：冲回版本 1 的余额变化再应用版本 2，两步同事务；版本 1 标记 REVERSED。 */
    @Test
    void test_version2SupersedesVersion1() {
        EconomicFactPayload v1 = clientCredit("10").build();
        EconomicFactPayload v2 = clientCredit("12").supersedes(v1.getFactId(), 2).build();

        consumed(v1);
        consumed(v2);

        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "10", "22", "12");
        assertAll(() -> assertEquals(AppliedFactStatus.REVERSED.getCode(), applyStatus(v1.getFactId())),
            () -> assertEquals(AppliedFactStatus.APPLIED.getCode(), applyStatus(v2.getFactId())));
    }

    /** 乱序：版本 2 先到时预登记版本 1，版本 1 到达后跳过，余额只反映版本 2。 */
    @Test
    void test_version2BeforeVersion1() {
        EconomicFactPayload v1 = clientCredit("10").build();
        EconomicFactPayload v2 = clientCredit("12").supersedes(v1.getFactId(), 2).build();

        consumed(v2);
        assertEquals(AppliedFactStatus.PENDING_SUPERSEDED.getCode(), applyStatus(v1.getFactId()));
        consumed(v1);

        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "0", "12", "12");
        assertAll(() -> assertEquals(AppliedFactStatus.SKIPPED.getCode(), applyStatus(v1.getFactId())),
            () -> assertEquals(0, testMapper.countEntries(v1.getFactId())));
    }

    /** 人工调整按付方 / 收方映射：Client 收方记贷方、付方记借方，Partner 付方记借方；经营主体与平台角色不产生余额。 */
    @Test
    void test_adjustmentRolesMapToBalances() {
        EconomicFactPayload fact = adjustment().partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, FINANCE_ENTITY_ID)
            .line(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT,
                FactLineDirection.CHARGE, "10")
            .line(AmountSource.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT, FactPartyRole.PLATFORM, "1",
                FactLineDirection.ADJUSTMENT, "1")
            .line(AmountSource.PARTNER_SHARE, FactPartyRole.LOCAL_PARTNER, PARTNER, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, FactLineDirection.CHARGE, "4")
            .build();

        consumed(fact);

        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "1", "10", "9");
        assertBalance(StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER, PARTNER, BalanceBucket.PENDING,
            "4", "0", "-4");
        assertEquals(2, testMapper.selectBalances().size(), "经营主体与平台角色不建立余额");
    }

    /** REVERSAL 方向：收方的贷方影响改为借方。 */
    @Test
    void test_reversalDirectionSwapsSides() {
        EconomicFactPayload fact = adjustment().line(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, FactPartyRole.CLIENT, CLIENT, FactLineDirection.REVERSAL, "5").build();

        consumed(fact);

        assertBalance(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, "5", "0", "-5");
    }

    /** Owner 角色出现在没有余额映射的费用类型上：整条事实拒绝进入死信队列，不登记消费、不建立余额。 */
    @Test
    void test_unmappedOwnerFeeTypeRejectedToDeadLetter() {
        EconomicFactPayload fact = adjustment().line(AmountSource.SELLER_TAX, FactPartyRole.CLIENT, CLIENT,
            FactPartyRole.TAX_AUTHORITY, "4001", FactLineDirection.CHARGE, "1").build();

        billing.publish(fact);

        await().atMost(CONSUME_WAIT).until(() -> billing.deadLetters() == 1);
        assertAll(() -> assertEquals(0, testMapper.countAllConsumedEvents()),
            () -> assertTrue(testMapper.selectBalances().isEmpty()), () -> assertNull(applyStatus(fact.getFactId())));
    }

    /** 给 Client 记一笔贷方的人工调整。 */
    private static EconomicFactBuilder clientCredit(String amount) {
        return adjustment().line(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
            FactPartyRole.CLIENT, CLIENT, FactLineDirection.CHARGE, amount);
    }
}
