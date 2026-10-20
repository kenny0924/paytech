package com.paytech.ledger.auto.domainservice.posting;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.channelCost;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientFee;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientPrincipal;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.partnerShare;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.platformServiceCharge;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.sellerTax;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.domain.view.PostedInstructionView;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.RuleVersionRequestBuilder;
import com.paytech.ledger.test.mocker.local.LedgerFixture;

/**
 * PAYMENT 控制科目过账成功路径：Owner 控制科目行来自 account 流水（account_entry_id 指回流水）、非 Owner 行来自 STANDARD_TRANSACTION
 * 规则、借贷平衡；有 Partner 三指令双账簿；零金额来源跳过；重复投递与重复调用幂等
 * <li>测试扮演 account 向真实本机 RabbitMQ 发布 AccountPosted，经 ledger.account 分区队列与幂等拦截器由正式 Handler 事务外经 billing-api
 * 读事实后过账；断言只落 ledger 表，控制账户 Owner 为账簿主体、桶 NONE。</li>
 *
 * @see com.paytech.ledger.ext.mq.AccountPostedPostingHandler#consume(com.paytech.foundation.messaging.api.DomainEvent)
 * @see com.paytech.ledger.domainservice.service.TransactionPostingService#post(com.paytech.ledger.domain.AccountPostingCommand)
 */
class TransactionPostingTest extends AbstractTransactionPostingTest {

    /** 无 Partner：一条 ACCOUNT_POSTING 指令；规则行 5201、5203，Client 应付行来自流水并记到 OE 控制账户。 */
    @Test
    void test_paymentWithoutPartner() {
        long book = oeLedger.getLedgerBookId();
        BillingFactPayload economics = fact(payment("100"),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();

        List<PostedInstructionView> views = postBatch(economics, posting);

        assertEquals(1, views.size());
        AccountingInstruction instruction = instruction(posting, AutoInstructionType.PAYMENT_SUCCEEDED);
        assertPostingInstruction(instruction, economics, posting, book, INITIAL_VERSION_ID, businessDate(economics));
        long receivable = channelReceivable(book);
        long feeRevenue = existingAccount(book, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO);
        long clientControl = controlAccount(StandardAccountCodes.CLIENT_PAYABLE);
        assertInstructionLines(instruction.getId(), debit(5201, receivable, "100"), credit(5203, feeRevenue, "3"),
            entry(posting.getEntries().get(0), clientControl));
        ledgerChecker.assertBalancedJournals(book, 1).assertAccount(receivable, "100", "0", "100", 1)
            .assertAccount(clientControl, "0", "97", "97", 1).assertAccount(feeRevenue, "0", "3", "3", 1);
        assertEquals(1, consumedSequence(posting));
        assertEquals(1, billingFacts.factCalls(economics.getFactId()));
        // 记账自动建户：账户号 {entityNo}-{币种}-{科目编号}-{同账簿同科目序号}
        assertAll(() -> assertEquals("AE001-USD-11010101-0001", accountNo(receivable)),
            () -> assertEquals("AE001-USD-40010101-0001", accountNo(feeRevenue)),
            () -> assertEquals("AE001-USD-22010101-0001", accountNo(clientControl)));
    }

    /** 科目停用不影响已有账户记账：已存在的收入账户照常过账，不新建账户。 */
    @Test
    void test_inactiveChartAccountKeepsExistingAccountPosting() {
        long book = oeLedger.getLedgerBookId();
        long feeRevenue = ledgerMocker.mockAccount(book, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO, BalanceBucket.NONE);
        testMapper.updateStandardChartAccountStatus(RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            ChartAccountStatus.INACTIVE.getCode());
        BillingFactPayload economics = fact(payment("100"), clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));

        postBatch(economics, paymentPosting(economics, 1, "97", null).build());

        assertEquals(feeRevenue, existingAccount(book, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO));
        ledgerChecker.assertBalancedJournals(book, 1).assertAccount(feeRevenue, "0", "3", "3", 1);
    }

    /** 账户号。 */
    String accountNo(long accountId) {
        return testMapper.selectAccount(accountId).getAccountNo();
    }

    /** 有 Partner：OE 账簿支付指令含分润费用规则行与两条控制科目流水行、Partner 侧服务费指令；平台 FE 账簿 1 条服务费指令。 */
    @Test
    void test_paymentWithPartner() {
        LedgerFixture platform = mockPlatformFinanceEntityLedger();
        long financeEntityId = Long.parseLong(platform.getEntityNo());
        long book = oeLedger.getLedgerBookId();
        long platformBook = platform.getLedgerBookId();
        BillingFactPayload economics = fact(payment("100").partner(PARTNER_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", financeEntityId));
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", "1").build();

        Map<AutoInstructionType, PostedInstructionView> views = byType(postBatch(economics, posting));

        assertEquals(3, views.size());
        String fe = String.valueOf(financeEntityId);
        AccountingInstruction payment = instruction(posting, AutoInstructionType.PAYMENT_SUCCEEDED);
        assertPostingInstruction(payment, economics, posting, book, INITIAL_VERSION_ID, businessDate(economics));
        long partnerControl = controlAccount(StandardAccountCodes.PARTNER_PAYABLE);
        assertInstructionLines(payment.getId(), debit(5201, channelReceivable(book), "100"),
            credit(5203, existingAccount(book, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
                LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO), "3"),
            debit(5207, existingAccount(book, RuleVersionRequestBuilder.PARTNER_SHARE_EXPENSE,
                LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO), "1"),
            entry(posting.getEntries().get(0), controlAccount(StandardAccountCodes.CLIENT_PAYABLE)),
            entry(posting.getEntries().get(1), partnerControl));

        AccountingInstruction partnerSide = instruction(posting, AutoInstructionType.PLATFORM_SERVICE_CHARGE_PARTNER);
        assertPostingInstruction(partnerSide, economics, posting, book, INITIAL_VERSION_ID, businessDate(economics));
        long servicePayable = existingAccount(book, RuleVersionRequestBuilder.PLATFORM_SERVICE_PAYABLE,
            LedgerOwnerType.FINANCE_ENTITY, fe);
        assertInstructionLines(partnerSide.getId(), debit(5209, existingAccount(book,
            RuleVersionRequestBuilder.PLATFORM_SERVICE_FEE_EXPENSE, LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO), "0.5"),
            credit(5210, servicePayable, "0.5"));

        AccountingInstruction platformSide = instruction(posting, AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM);
        assertPostingInstruction(platformSide, economics, posting, platformBook, INITIAL_VERSION_ID,
            businessDate(economics));
        long serviceRevenue = existingAccount(platformBook, RuleVersionRequestBuilder.PLATFORM_SERVICE_REVENUE,
            LedgerOwnerType.FINANCE_ENTITY, fe);
        assertInstructionLines(platformSide.getId(), debit(5211, existingAccount(platformBook,
            RuleVersionRequestBuilder.PLATFORM_SERVICE_RECEIVABLE, LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO), "0.5"),
            credit(5212, serviceRevenue, "0.5"));

        assertAll(() -> assertEquals(PLATFORM_ENTITY_CODE + "-USD-40010201-0001", accountNo(serviceRevenue)),
            () -> assertEquals("AE001-USD-22510101-0001", accountNo(servicePayable)));
        ledgerChecker.assertBalancedJournals(book, 2).assertBalancedJournals(platformBook, 1)
            .assertAccount(partnerControl, "0", "1", "1", 1).assertAccount(servicePayable, "0", "0.5", "0.5", 2)
            .assertAccount(serviceRevenue, "0", "0.5", "0.5", 1);
        assertNull(account(book, StandardAccountCodes.PARTNER_PAYABLE, LedgerOwnerType.OPERATING_ENTITY,
            String.valueOf(PARTNER_ID)), "总账不再按 Partner 开户");
    }

    /** 手续费为 0 时收入行跳过；Seller 税与渠道成本组件存在时生成对应规则行。 */
    @Test
    void test_zeroFeeSkippedWithTaxAndChannelCost() {
        long book = oeLedger.getLedgerBookId();
        BillingFactPayload economics = fact(payment("100"),
            clientPrincipal("95", CLIENT_ID), clientFee("0", ENTITY_NO), sellerTax("5", TAX_AUTHORITY_ID),
            channelCost("2"));
        AccountPostedPayload posting = paymentPosting(economics, 1, "95", null).build();

        List<PostedInstructionView> views = postBatch(economics, posting);

        assertEquals(1, views.size());
        AccountingInstruction instruction = instruction(posting, AutoInstructionType.PAYMENT_SUCCEEDED);
        long taxPayable = existingAccount(book, RuleVersionRequestBuilder.TAX_PAYABLE, LedgerOwnerType.TAX_AUTHORITY,
            String.valueOf(TAX_AUTHORITY_ID));
        long channelAccrual = existingAccount(book, RuleVersionRequestBuilder.CHANNEL_FEE_ACCRUAL,
            LedgerOwnerType.CHANNEL_ACCOUNT, String.valueOf(CHANNEL_ACCOUNT_ID));
        assertInstructionLines(instruction.getId(), debit(5201, channelReceivable(book), "100"),
            credit(5204, taxPayable, "5"),
            debit(5205, existingAccount(book, RuleVersionRequestBuilder.CHANNEL_FEE_EXPENSE,
                LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO), "2"),
            credit(5206, channelAccrual, "2"),
            entry(posting.getEntries().get(0), controlAccount(StandardAccountCodes.CLIENT_PAYABLE)));
        assertNull(account(book, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE, LedgerOwnerType.OPERATING_ENTITY,
            ENTITY_NO), "零手续费不应创建收入账户");
        ledgerChecker.assertBalancedJournals(book, 1).assertAccount(taxPayable, "0", "5", "5", 1)
            .assertAccount(channelAccrual, "0", "2", "2", 1);
    }

    /** 同一批次重复投递只消费一次；直接重复调用引擎返回原指令与 Journal，不新增指令、Journal 或序号。 */
    @Test
    void test_repeatedPostReturnsOriginal() {
        LedgerFixture platform = mockPlatformFinanceEntityLedger();
        long financeEntityId = Long.parseLong(platform.getEntityNo());
        BillingFactPayload economics = fact(payment("100").partner(PARTNER_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", financeEntityId));
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", "1").build();
        List<PostedInstructionView> first = sorted(postBatch(economics, posting));
        accountEvents.publish(posting);
        await().atMost(CONSUME_WAIT).until(() -> accountEvents.pending() == 0);

        List<PostedInstructionView> second = postDirectly(economics, posting);

        assertEquals(sorted(second).stream().map(this::identity).toList(), first.stream().map(this::identity).toList());
        assertEquals(second, sorted(second), "幂等返回按账簿 ID、指令主键升序");
        assertAll(() -> assertEquals(3, testMapper.countAllInstructions()),
            () -> assertEquals(3, testMapper.countAllJournalEntries()),
            () -> assertEquals(1, testMapper.countAllConsumedEvents(), "同一 eventId 只登记一次消费"),
            () -> assertEquals(1, consumedSequence(posting)));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 2)
            .assertBalancedJournals(platform.getLedgerBookId(), 1)
            .assertAccount(channelReceivable(oeLedger.getLedgerBookId()), "100", "0", "100", 1);
    }

    /** 渠道应收账户：Owner 为渠道清算账户主键，由引擎自动创建。 */
    private long channelReceivable(long book) {
        return existingAccount(book, RuleVersionRequestBuilder.CHANNEL_UNSETTLED_RECEIVABLE,
            LedgerOwnerType.CHANNEL_ACCOUNT, String.valueOf(CHANNEL_ACCOUNT_ID));
    }

    private static Map<AutoInstructionType, PostedInstructionView> byType(List<PostedInstructionView> views) {
        return views.stream().collect(Collectors.toMap(PostedInstructionView::getInstructionType,
            Function.identity()));
    }

    private static List<PostedInstructionView> sorted(List<PostedInstructionView> views) {
        return views.stream().sorted(Comparator.comparing(PostedInstructionView::getLedgerBookId)
            .thenComparing(PostedInstructionView::getInstructionId)).toList();
    }

    private List<Object> identity(PostedInstructionView view) {
        return List.of(view.getInstructionId(), view.getLedgerBookId(), view.getInstructionType(),
            view.getJournalEntryId(), view.getJournalSequence());
    }
}
