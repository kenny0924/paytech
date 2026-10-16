package com.paytech.finance.auto.domainservice.posting;

import static com.paytech.finance.test.builder.EconomicComponentBuilder.clientFee;
import static com.paytech.finance.test.builder.EconomicComponentBuilder.clientPrincipal;
import static com.paytech.finance.test.builder.EconomicComponentBuilder.partnerShare;
import static com.paytech.finance.test.builder.EconomicComponentBuilder.platformServiceCharge;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.enums.AmountSource;
import com.paytech.finance.accounting.core.enums.AutoInstructionType;
import com.paytech.finance.accounting.core.enums.EconomicsType;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.accounting.core.enums.RuleVersionStatus;
import com.paytech.finance.test.base.AbstractTransactionPostingTest;
import com.paytech.finance.test.builder.TransactionEconomicsBuilder;
import com.paytech.finance.test.dal.row.TransactionEconomicsRow;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 交易自动记账 Fail-Closed：平台账簿缺失整体回滚、必需来源缺失、渠道清算账户缺失或不属于交易 OE、期间未开放、无生效规则版本、
 * Partner 三元组与组件不一致、非 PAYMENT 经济结果；拒绝后不残留指令、Journal 或自动创建的账户。
 *
 * @see com.paytech.finance.accounting.domainservice.service.TransactionPostingService#post(com.paytech.finance.accounting.domain.TransactionPostingCommand)
 */
class TransactionPostingRejectionTest extends AbstractTransactionPostingTest {
    /** 初始化 DML 预置的 Bundle 主键。 */
    private static final long BUNDLE_ID = 5001L;

    /** 标准无 Partner 支付：gross 100 = 本金 97 + 手续费 3。 */
    private TransactionEconomicsRow standardPayment() {
        return economicsMocker.mockEconomics(payment("100"), clientPrincipal("97", CLIENT_ID),
            clientFee("3", ENTITY_NO));
    }

    /** 平台 FE 账簿不存在：OE 账簿两条指令已可生成，但整笔回滚，不残留任何指令、Journal 或账户。 */
    @Test
    void test_platformBookMissingRollsBackAll() {
        long financeEntityId = economicsMocker.mockPlatformFinanceEntity(PLATFORM_ENTITY_CODE,
            PLATFORM_LEGAL_PARTY_ID);
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(
            payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", financeEntityId));

        assertRejected(economics.getId(), MID_ID, AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM.getCode(),
            "账簿不存在或未开放");
    }

    /** 必需来源 CLIENT_PRINCIPAL 缺失。 */
    @Test
    void test_requiredSourceMissingRejected() {
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(payment("100"),
            clientFee("3", ENTITY_NO));

        assertRejected(economics.getId(), MID_ID, "必需金额来源缺失", AmountSource.CLIENT_PRINCIPAL.getCode());
    }

    /** MID 未配置渠道清算账户。 */
    @Test
    void test_channelAccountMissingRejected() {
        TransactionEconomicsRow economics = standardPayment();

        assertRejected(economics.getId(), MID_ID + 1, "渠道清算账户", String.valueOf(MID_ID + 1));
    }

    /** 渠道清算账户属于其他经营主体。 */
    @Test
    void test_channelAccountOfOtherEntityRejected() {
        long otherMid = MID_ID + 2;
        economicsMocker.mockChannelAccount(otherMid, "AE002", CURRENCY);
        TransactionEconomicsRow economics = standardPayment();

        assertRejected(economics.getId(), otherMid, "渠道清算账户", String.valueOf(otherMid));
    }

    /** 成功时间所在期间已关闭。 */
    @Test
    void test_closedPeriodRejected() {
        testMapper.updatePeriodStatus(oeLedger.getOpenPeriodId(), PeriodStatus.CLOSED.getCode());
        TransactionEconomicsRow economics = standardPayment();

        assertRejected(economics.getId(), MID_ID, "会计期间不存在或未开放");
    }

    /** 成功时间所在期间不存在（两个月前，无期间）。 */
    @Test
    void test_missingPeriodRejected() {
        Instant twoMonthsAgo = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(62, ChronoUnit.DAYS);
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(
            payment("100").paymentSucceededTime(twoMonthsAgo), clientPrincipal("97", CLIENT_ID),
            clientFee("3", ENTITY_NO));

        assertRejected(economics.getId(), MID_ID, "会计期间不存在或未开放");
    }

    /** 当前版本不再 ACTIVE：无生效规则版本（清理组件恢复 5101）。 */
    @Test
    void test_retiredCurrentVersionRejected() {
        testMapper.updateRuleVersionStatus(INITIAL_VERSION_ID, RuleVersionStatus.RETIRED.getCode());
        TransactionEconomicsRow economics = standardPayment();

        assertRejected(economics.getId(), MID_ID, "无生效的自动记账规则版本");
    }

    /** Bundle 当前版本指针为空：无可用规则集（清理组件恢复指针）。 */
    @Test
    void test_bundleWithoutCurrentVersionRejected() {
        testMapper.clearRuleBundleCurrentVersion(BUNDLE_ID);
        TransactionEconomicsRow economics = standardPayment();

        assertRejected(economics.getId(), MID_ID, "无可用自动记账规则集");
    }

    /** Partner 三元组不完整。 */
    @Test
    void test_incompletePartnerTripleRejected() {
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(
            payment("100").localPartnerId(PARTNER_ID), clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO),
            partnerShare("1", PARTNER_ID));

        assertRejected(economics.getId(), MID_ID, "Partner 三元组不完整");
    }

    /** 无 Partner 却存在分润组件。 */
    @Test
    void test_partnerComponentWithoutPartnerRejected() {
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(payment("100"),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID));

        assertRejected(economics.getId(), MID_ID, "Partner 组件不一致", AmountSource.PARTNER_SHARE.getCode());
    }

    /** 有 Partner 但缺少平台服务费组件。 */
    @Test
    void test_partnerWithoutServiceChargeRejected() {
        long financeEntityId = Long.parseLong(economicsMocker.mockPlatformFinanceEntityLedger(PLATFORM_ENTITY_CODE,
            PLATFORM_LEGAL_PARTY_ID, CURRENCY).getEntityNo());
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(
            payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID));

        assertRejected(economics.getId(), MID_ID, "Partner 组件不一致",
            AmountSource.PLATFORM_SERVICE_CHARGE.getCode());
    }

    /** 平台服务费归属与 Header Finance Entity 不一致。 */
    @Test
    void test_serviceChargePartyMismatchRejected() {
        long financeEntityId = Long.parseLong(economicsMocker.mockPlatformFinanceEntityLedger(PLATFORM_ENTITY_CODE,
            PLATFORM_LEGAL_PARTY_ID, CURRENCY).getEntityNo());
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(
            payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", financeEntityId + 1));

        assertRejected(economics.getId(), MID_ID, "平台服务费归属");
    }

    /** 非 PAYMENT 经济结果。 */
    @Test
    void test_nonPaymentEconomicsRejected() {
        TransactionEconomicsBuilder refund = payment("100").economicsType(EconomicsType.REFUND);
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(refund, clientPrincipal("97", CLIENT_ID),
            clientFee("3", ENTITY_NO));

        assertRejected(economics.getId(), MID_ID, "不是 PAYMENT", EconomicsType.REFUND.getCode());
    }

    /** 调用被拒：消息含经济结果 ID 与给定片段，且整笔回滚无指令、Journal、账户残留、序号未推进。 */
    private void assertRejected(long economicsId, long midId, String... fragments) {
        BizCheckFailException error = assertThrows(BizCheckFailException.class,
            () -> postEconomics(economicsId, midId));

        String message = error.getMessage();
        assertTrue(message.contains(String.valueOf(economicsId)), "拒绝消息应含经济结果 ID: " + message);
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), "拒绝消息应含 [" + fragment + "]: " + message);
        }
        assertAll(() -> assertEquals(0, testMapper.countAllInstructions(), "不应残留指令"),
            () -> assertEquals(0, testMapper.countAllJournalEntries(), "不应残留 Journal"),
            () -> assertEquals(0, testMapper.countAllAccounts(), "不应残留自动创建的账户"),
            () -> assertEquals(1L, testMapper.selectNextSequence(oeLedger.getLedgerBookId()), "序号不应推进"));
    }
}
