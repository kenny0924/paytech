package com.paytech.ledger.auto.domainservice.posting;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientFee;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientPrincipal;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.partnerShare;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.platformServiceCharge;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.sellerTaxWithoutAuthority;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.ledger.api.enums.AmountSource;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.BillingFactBuilder;
import com.paytech.ledger.test.builder.RuleVersionRequestBuilder;

/**
 * 控制科目过账 Fail-Closed：平台账簿缺失整体回滚、必需来源缺失、事实缺少渠道清算账户 / Finance Entity / 税务机关 / 支付成功时间 / 明细金额、
 * 业务时间无适用规则版本（早于首个版本、退役版本缺失效时间）、没有记账规则的事实类型、流水科目不在明细账目录、批次与事实借贷不平；拒绝后不残留指令、Journal 或自动创建的账户
 * <li>拒绝矩阵直接调用 AccountPostedPostingHandler（事务外，Handler 自管事务），断言 {@code DomainEventRejectedException} 携带的拒绝原因；
 * {@link #test_rejectedEventGoesToDeadLetter} 经真实 Broker 证明拒绝的信封直接进入 ledger.account.dlq 且不登记消费。</li>
 *
 * @see com.paytech.ledger.ext.mq.AccountPostedPostingHandler#consume(com.paytech.foundation.messaging.api.DomainEvent)
 * @see com.paytech.ledger.domainservice.service.TransactionPostingService#post(com.paytech.ledger.domain.AccountPostingCommand)
 */
class TransactionPostingRejectionTest extends AbstractTransactionPostingTest {
    /** 初始化 DML 预置的 Bundle 主键。 */
    private static final long BUNDLE_ID = 5001L;

    /** 初始化 DML 预置的 Bundle 代码。 */
    private static final String STANDARD_TRANSACTION_BUNDLE = "STANDARD_TRANSACTION";

    /** 业务时间没有区间覆盖的规则版本时的拒绝原因。 */
    private static final String NO_APPLICABLE_VERSION = "业务时间无适用的自动记账规则版本";

    /** 标准无 Partner 支付：gross 100 = 本金 97 + 手续费 3。 */
    private BillingFactPayload standardPayment() {
        return fact(payment("100"), clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));
    }

    /** 平台 FE 账簿不存在：OE 账簿两条指令已可生成，但整笔回滚，不残留任何指令、Journal 或账户。 */
    @Test
    void test_platformBookMissingRollsBackAll() {
        long financeEntityId = mockPlatformFinanceEntity();
        BillingFactPayload economics = fact(
            payment("100").partner(PARTNER_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", financeEntityId));

        assertRejected(economics, paymentPosting(economics, 1, "97", "1").build(),
            AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM.getCode(), "账簿不存在或未开放");
    }

    /** 必需来源 CLIENT_FEE（规则行 5203）缺失；CLIENT_PRINCIPAL 已不由规则行承载（Owner 行来自流水）。 */
    @Test
    void test_requiredSourceMissingRejected() {
        BillingFactPayload economics = fact(payment("100"), clientPrincipal("100", CLIENT_ID));

        assertRejected(economics, paymentPosting(economics, 1, "100", null).build(), "必需金额来源缺失",
            AmountSource.CLIENT_FEE.getCode());
    }

    /** 记账需要新建账户而科目已停用：整笔回滚，已建的其他账户一并撤销。 */
    @Test
    void test_inactiveChartAccountRejectsNewAccount() {
        testMapper.updateStandardChartAccountStatus(RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            ChartAccountStatus.INACTIVE.getCode());
        BillingFactPayload economics = standardPayment();

        assertRejected(economics, paymentPosting(economics, 1, "97", null).build(), "科目已停用",
            RuleVersionRequestBuilder.CLIENT_FEE_REVENUE);
    }

    /** 事实头缺少渠道清算账户主键。 */
    @Test
    void test_channelAccountMissingRejected() {
        BillingFactPayload economics = fact(payment("100").channelAccountId(null), clientPrincipal("97", CLIENT_ID),
            clientFee("3", ENTITY_NO));

        assertRejected(economics, paymentPosting(economics, 1, "97", null).build(), "缺少 channel_account_id");
    }

    /** SELLER_TAX 明细行缺少税务机关归属。 */
    @Test
    void test_sellerTaxWithoutAuthorityRejected() {
        BillingFactPayload economics = fact(payment("100"), clientPrincipal("95", CLIENT_ID),
            clientFee("0", ENTITY_NO), sellerTaxWithoutAuthority("5"));

        assertRejected(economics, paymentPosting(economics, 1, "95", null).build(),
            "SELLER_TAX 组件税务机关缺失或不唯一");
    }

    /** 唯一版本被置为 RETIRED 却没有失效时间：视为数据损坏、不作候选，无适用规则版本（清理组件恢复 5101）。 */
    @Test
    void test_retiredCurrentVersionRejected() {
        testMapper.updateRuleVersionStatus(INITIAL_VERSION_ID, RuleVersionStatus.RETIRED.getCode());
        BillingFactPayload economics = standardPayment();

        assertRejected(economics, paymentPosting(economics, 1, "97", null).build(), NO_APPLICABLE_VERSION,
            STANDARD_TRANSACTION_BUNDLE, String.valueOf(economics.getBusinessTime()));
    }

    /** AC-05：业务时间早于首个版本（5101 自 2000-01-01 起）：无适用规则版本，拒绝信息含规则集与业务时间，整笔不残留。 */
    @Test
    void test_businessTimeBeforeFirstVersionRejected() {
        Instant businessTime = Instant.parse("1999-12-31T23:59:59Z");
        BillingFactPayload economics = fact(payment("100").businessTime(businessTime),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));

        assertRejected(economics, paymentPosting(economics, 1, "97", null).build(), NO_APPLICABLE_VERSION,
            STANDARD_TRANSACTION_BUNDLE, businessTime.toString());
    }

    /** Bundle 当前版本指针为空：无可用规则集（清理组件恢复指针）。 */
    @Test
    void test_bundleWithoutCurrentVersionRejected() {
        testMapper.clearRuleBundleCurrentVersion(BUNDLE_ID);
        BillingFactPayload economics = standardPayment();

        assertRejected(economics, paymentPosting(economics, 1, "97", null).build(), "无可用自动记账规则集");
    }

    /** 存在平台服务费明细但事实缺少 Finance Entity：服务费应付行无法解析平台 Owner。 */
    @Test
    void test_serviceChargeWithoutFinanceEntityRejected() {
        BillingFactPayload economics = fact(payment("100").localPartnerId(PARTNER_ID),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", PLATFORM_LEGAL_PARTY_ID));

        assertRejected(economics, paymentPosting(economics, 1, "97", "1").build(),
            AutoInstructionType.PLATFORM_SERVICE_CHARGE_PARTNER.getCode(), "缺少 finance_entity_id");
    }

    /** PAYMENT 事实缺少业务时间（businessTime）。 */
    @Test
    void test_missingBusinessTimeRejected() {
        BillingFactPayload economics = fact(payment("100").businessTime(null),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));

        assertRejected(economics, paymentPosting(economics, 1, "97", null).build(), "缺少业务时间", "businessTime");
    }

    /** 明细行缺少已归整金额：事实不完整。 */
    @Test
    void test_incompleteFactPayloadRejected() {
        BillingFactPayload economics = fact(payment("100"), clientPrincipal("97", CLIENT_ID),
            clientFee("3", ENTITY_NO).postedAmount(null));

        assertRejected(economics, paymentPosting(economics, 1, "97", null).build(), "载荷不完整", "posted_amount");
    }

    /** 没有记账规则的事实类型（REFUND）。 */
    @Test
    void test_nonPaymentEconomicsRejected() {
        BillingFactBuilder refund = payment("100").factType(BillingFactType.REFUND);
        BillingFactPayload economics = fact(refund, clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));

        assertRejected(economics, posting(economics, 1).build(), "不是 PAYMENT", BillingFactType.REFUND.getCode());
    }

    /** 批次流水的科目不在 account 明细账目录（渠道科目只在总账）：拒绝。 */
    @Test
    void test_entryOutsideSubledgerCatalogRejected() {
        BillingFactPayload economics = standardPayment();
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null)
            .entry(StandardAccountCodes.CHANNEL_FEE_ACCRUAL, OwnerType.CLIENT, String.valueOf(CLIENT_ID),
                BalanceBucket.NONE, EntrySide.CREDIT, "1", 2).build();

        assertRejected(economics, posting, "入账流水科目不在明细账目录", StandardAccountCodes.CHANNEL_FEE_ACCRUAL);
    }

    /** 批次流水与规则行合计不平（account 漏记 Client 应付）：拒绝，不过账。 */
    @Test
    void test_unbalancedBatchRejected() {
        BillingFactPayload economics = standardPayment();

        assertRejected(economics, posting(economics, 1).build(), "指令借贷不平衡");
    }

    /** 经真实 Broker 投递的没有记账规则的事实（REFUND）批次：Handler 明确拒绝，信封直接进入死信队列，不登记消费、不残留任何指令。 */
    @Test
    void test_rejectedEventGoesToDeadLetter() {
        BillingFactPayload economics = fact(payment("100").factType(BillingFactType.REFUND),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));
        billingFacts.mockFact(economics);

        accountEvents.publish(posting(economics, 1).build());

        await().atMost(CONSUME_WAIT).until(() -> accountEvents.deadLetters() == 1);
        assertAll(() -> assertEquals(0, testMapper.countAllConsumedEvents(), "拒绝不登记消费"),
            () -> assertEquals(0, testMapper.countAllInstructions(), "不应残留指令"));
    }

    /** 调用被拒：Handler 以 DomainEventRejectedException 拒绝，原因含计费事实 ID 与给定片段，且整笔回滚无指令、Journal、账户残留、序号未推进。 */
    private void assertRejected(BillingFactPayload economics, AccountPostedPayload posting, String... fragments) {
        billingFacts.mockFact(economics);
        DomainEventRejectedException rejected = assertThrows(DomainEventRejectedException.class,
            () -> consumeDirectly(posting));
        BizCheckFailException error = assertInstanceOf(BizCheckFailException.class, rejected.getCause());

        String message = error.getMessage();
        assertTrue(message.contains(String.valueOf(economics.getFactId())), "拒绝消息应含计费事实 ID: " + message);
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), "拒绝消息应含 [" + fragment + "]: " + message);
        }
        assertAll(() -> assertEquals(0, testMapper.countAllInstructions(), "不应残留指令"),
            () -> assertEquals(0, testMapper.countAllJournalEntries(), "不应残留 Journal"),
            () -> assertEquals(0, testMapper.countAllAccounts(), "不应残留自动创建的账户"),
            () -> assertEquals(1L, testMapper.selectNextSequence(oeLedger.getLedgerBookId()), "序号不应推进"),
            () -> assertEquals(0, consumedSequence(posting), "直接调用不推进消费进度"));
    }
}
