package com.paytech.ledger.auto.domainservice.posting;

import static com.paytech.ledger.test.builder.EconomicComponentBuilder.clientFee;
import static com.paytech.ledger.test.builder.EconomicComponentBuilder.clientPrincipal;
import static com.paytech.ledger.test.builder.EconomicComponentBuilder.partnerShare;
import static com.paytech.ledger.test.builder.EconomicComponentBuilder.platformServiceCharge;
import static com.paytech.ledger.test.builder.EconomicComponentBuilder.sellerTaxWithoutAuthority;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.TransactionEconomicsBuilder;

/**
 * 交易自动记账 Fail-Closed：平台账簿缺失整体回滚、必需来源缺失、载荷缺少渠道清算账户 / Local Partner / Finance Entity / 税务机关 /
 * 支付成功时间 / 明细金额、期间未开放、无生效规则版本、非 PAYMENT 经济事实；拒绝后不残留指令、Journal 或自动创建的账户
 * <li>输入驱动（任务卡 07 第 3.2 项）：Partner 三元组与服务费归属等 billing 登记不变量由 billing 校验（见 billing 的 EconomicFactInvariantsTest），
 * ledger 只断言载荷完整性；渠道清算账户的存在、启用与所属 OE 由 billing 解析时保证。</li>
 *
 * @see com.paytech.ledger.domainservice.service.TransactionPostingService#post(com.paytech.ledger.domain.TransactionPostingCommand)
 */
class TransactionPostingRejectionTest extends AbstractTransactionPostingTest {
    /** 初始化 DML 预置的 Bundle 主键。 */
    private static final long BUNDLE_ID = 5001L;

    /** 标准无 Partner 支付：gross 100 = 本金 97 + 手续费 3。 */
    private EconomicFactPayload standardPayment() {
        return fact(payment("100"), clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));
    }

    /** 平台 FE 账簿不存在：OE 账簿两条指令已可生成，但整笔回滚，不残留任何指令、Journal 或账户。 */
    @Test
    void test_platformBookMissingRollsBackAll() {
        long financeEntityId = mockPlatformFinanceEntity();
        EconomicFactPayload economics = fact(
            payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", financeEntityId));

        assertRejected(economics, AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM.getCode(), "账簿不存在或未开放");
    }

    /** 必需来源 CLIENT_PRINCIPAL 缺失。 */
    @Test
    void test_requiredSourceMissingRejected() {
        EconomicFactPayload economics = fact(payment("100"), clientFee("3", ENTITY_NO));

        assertRejected(economics, "必需金额来源缺失", AmountSource.CLIENT_PRINCIPAL.getCode());
    }

    /** 载荷头缺少渠道清算账户主键（原 MID 未配置渠道清算账户）。 */
    @Test
    void test_channelAccountMissingRejected() {
        EconomicFactPayload economics = fact(payment("100").channelAccountId(null), clientPrincipal("97", CLIENT_ID),
            clientFee("3", ENTITY_NO));

        assertRejected(economics, "缺少 channel_account_id");
    }

    /** SELLER_TAX 明细行缺少税务机关归属。 */
    @Test
    void test_sellerTaxWithoutAuthorityRejected() {
        EconomicFactPayload economics = fact(payment("100"), clientPrincipal("95", CLIENT_ID),
            clientFee("0", ENTITY_NO), sellerTaxWithoutAuthority("5"));

        assertRejected(economics, "SELLER_TAX 组件税务机关缺失或不唯一");
    }

    /** 成功时间所在期间已关闭。 */
    @Test
    void test_closedPeriodRejected() {
        testMapper.updatePeriodStatus(oeLedger.getOpenPeriodId(), PeriodStatus.CLOSED.getCode());
        EconomicFactPayload economics = standardPayment();

        assertRejected(economics, "会计期间不存在或未开放");
    }

    /** 成功时间所在期间不存在（两个月前，无期间）。 */
    @Test
    void test_missingPeriodRejected() {
        Instant twoMonthsAgo = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(62, ChronoUnit.DAYS);
        EconomicFactPayload economics = fact(payment("100").paymentSucceededTime(twoMonthsAgo),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));

        assertRejected(economics, "会计期间不存在或未开放");
    }

    /** 当前版本不再 ACTIVE：无生效规则版本（清理组件恢复 5101）。 */
    @Test
    void test_retiredCurrentVersionRejected() {
        testMapper.updateRuleVersionStatus(INITIAL_VERSION_ID, RuleVersionStatus.RETIRED.getCode());
        EconomicFactPayload economics = standardPayment();

        assertRejected(economics, "无生效的自动记账规则版本");
    }

    /** Bundle 当前版本指针为空：无可用规则集（清理组件恢复指针）。 */
    @Test
    void test_bundleWithoutCurrentVersionRejected() {
        testMapper.clearRuleBundleCurrentVersion(BUNDLE_ID);
        EconomicFactPayload economics = standardPayment();

        assertRejected(economics, "无可用自动记账规则集");
    }

    /** 存在分润明细但载荷缺少 Local Partner：分润应付行无法解析 Owner。 */
    @Test
    void test_partnerShareWithoutLocalPartnerRejected() {
        EconomicFactPayload economics = fact(payment("100"), clientPrincipal("97", CLIENT_ID),
            clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID));

        assertRejected(economics, AutoInstructionType.PAYMENT_SUCCEEDED.getCode(), "缺少 local_partner_id");
    }

    /** 存在平台服务费明细但载荷缺少 Finance Entity：服务费应付行无法解析平台 Owner。 */
    @Test
    void test_serviceChargeWithoutFinanceEntityRejected() {
        EconomicFactPayload economics = fact(payment("100").localPartnerId(PARTNER_ID),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", PLATFORM_LEGAL_PARTY_ID));

        assertRejected(economics, AutoInstructionType.PLATFORM_SERVICE_CHARGE_PARTNER.getCode(),
            "缺少 finance_entity_id");
    }

    /** PAYMENT 载荷缺少支付成功时间。 */
    @Test
    void test_missingSucceededTimeRejected() {
        EconomicFactPayload economics = fact(payment("100").paymentSucceededTime(null),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));

        assertRejected(economics, "缺少支付成功时间");
    }

    /** 明细行缺少已归整金额：载荷不完整。 */
    @Test
    void test_incompleteFactPayloadRejected() {
        EconomicFactPayload economics = fact(payment("100"), clientPrincipal("97", CLIENT_ID),
            clientFee("3", ENTITY_NO).postedAmount(null));

        assertRejected(economics, "载荷不完整", "posted_amount");
    }

    /** 非 PAYMENT 经济事实。 */
    @Test
    void test_nonPaymentEconomicsRejected() {
        TransactionEconomicsBuilder refund = payment("100").economicsType(EconomicsType.REFUND);
        EconomicFactPayload economics = fact(refund, clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));

        assertRejected(economics, "不是 PAYMENT", EconomicsType.REFUND.getCode());
    }

    /** 调用被拒：消息含经济事实 ID 与给定片段，且整笔回滚无指令、Journal、账户残留、序号未推进。 */
    private void assertRejected(EconomicFactPayload economics, String... fragments) {
        BizCheckFailException error = assertThrows(BizCheckFailException.class, () -> postFact(economics));

        String message = error.getMessage();
        assertTrue(message.contains(String.valueOf(economics.getFactId())), "拒绝消息应含经济事实 ID: " + message);
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), "拒绝消息应含 [" + fragment + "]: " + message);
        }
        assertAll(() -> assertEquals(0, testMapper.countAllInstructions(), "不应残留指令"),
            () -> assertEquals(0, testMapper.countAllJournalEntries(), "不应残留 Journal"),
            () -> assertEquals(0, testMapper.countAllAccounts(), "不应残留自动创建的账户"),
            () -> assertEquals(1L, testMapper.selectNextSequence(oeLedger.getLedgerBookId()), "序号不应推进"));
    }
}
