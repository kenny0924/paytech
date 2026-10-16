package com.paytech.finance.auto.domainservice.posting;

import static com.paytech.finance.test.builder.EconomicComponentBuilder.channelCost;
import static com.paytech.finance.test.builder.EconomicComponentBuilder.clientFee;
import static com.paytech.finance.test.builder.EconomicComponentBuilder.clientPrincipal;
import static com.paytech.finance.test.builder.EconomicComponentBuilder.partnerShare;
import static com.paytech.finance.test.builder.EconomicComponentBuilder.platformServiceCharge;
import static com.paytech.finance.test.builder.EconomicComponentBuilder.sellerTax;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.constant.StandardAccountCodes;
import com.paytech.finance.accounting.core.dal.dataobject.AccountingInstruction;
import com.paytech.finance.accounting.core.enums.AutoInstructionType;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.domain.view.PostedInstructionView;
import com.paytech.finance.test.base.AbstractTransactionPostingTest;
import com.paytech.finance.test.builder.RuleVersionRequestBuilder;
import com.paytech.finance.test.dal.row.TransactionEconomicsRow;
import com.paytech.finance.test.mocker.local.LedgerFixture;

/**
 * 交易自动记账成功路径：无 Partner 单指令、有 Partner 三指令双账簿、零金额来源跳过、税与渠道成本行，以及重复调用幂等。
 *
 * @see com.paytech.finance.accounting.domainservice.service.TransactionPostingService#post(com.paytech.finance.accounting.domain.TransactionPostingCommand)
 */
class TransactionPostingTest extends AbstractTransactionPostingTest {
    private static final String CLIENT = String.valueOf(CLIENT_ID);

    /** 无 Partner：OE 账簿 1 条 AUTO 指令与 Journal，Client 本金进入 PENDING，Client/收入/渠道应收账户自动创建。 */
    @Test
    void test_paymentWithoutPartner() {
        long book = oeLedger.getLedgerBookId();
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(payment("100"),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));
        assertNull(account(book, StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING));

        List<PostedInstructionView> views = postEconomics(economics.getId());

        assertEquals(1, views.size());
        AccountingInstruction instruction = instruction(economics.getId(), AutoInstructionType.PAYMENT_SUCCEEDED);
        assertAutoInstruction(instruction, economics, book, views.get(0));
        long receivable = channelReceivable(book);
        long clientPending = existingAccount(book, StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT,
            BalanceBucket.PENDING);
        long feeRevenue = existingAccount(book, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
            OwnerType.OPERATING_ENTITY, ENTITY_NO, BalanceBucket.NONE);
        assertInstructionLines(instruction.getId(), debit(5201, receivable, "100"),
            credit(5202, clientPending, "97"), credit(5203, feeRevenue, "3"));
        ledgerChecker.assertBalancedJournals(book, 1).assertAccount(receivable, "100", "0", "100", 1)
            .assertAccount(clientPending, "0", "97", "97", 1).assertAccount(feeRevenue, "0", "3", "3", 1);
        assertEquals(1, testMapper.selectEconomicsInstructions(economics.getId()).size());
    }

    /** 有 Partner：OE 账簿支付指令含分润两行与 Partner 侧服务费指令，平台 FE 账簿 1 条服务费指令；三条 Journal 同一事务提交。 */
    @Test
    void test_paymentWithPartner() {
        LedgerFixture platform = economicsMocker.mockPlatformFinanceEntityLedger(PLATFORM_ENTITY_CODE,
            PLATFORM_LEGAL_PARTY_ID, CURRENCY);
        long financeEntityId = Long.parseLong(platform.getEntityNo());
        long book = oeLedger.getLedgerBookId();
        long platformBook = platform.getLedgerBookId();
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(
            payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", financeEntityId));

        Map<AutoInstructionType, PostedInstructionView> views = byType(postEconomics(economics.getId()));

        assertEquals(3, views.size());
        String partner = String.valueOf(PARTNER_ID);
        String fe = String.valueOf(financeEntityId);
        AccountingInstruction payment = instruction(economics.getId(), AutoInstructionType.PAYMENT_SUCCEEDED);
        assertAutoInstruction(payment, economics, book, views.get(AutoInstructionType.PAYMENT_SUCCEEDED));
        long partnerExpense = existingAccount(book, RuleVersionRequestBuilder.PARTNER_SHARE_EXPENSE,
            OwnerType.OPERATING_ENTITY, ENTITY_NO, BalanceBucket.NONE);
        long partnerPending = existingAccount(book, StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER,
            partner, BalanceBucket.PENDING);
        assertInstructionLines(payment.getId(), debit(5201, channelReceivable(book), "100"),
            credit(5202, existingAccount(book, StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT,
                BalanceBucket.PENDING), "97"),
            credit(5203, existingAccount(book, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE,
                OwnerType.OPERATING_ENTITY, ENTITY_NO, BalanceBucket.NONE), "3"),
            debit(5207, partnerExpense, "1"), credit(5208, partnerPending, "1"));

        AccountingInstruction partnerSide = instruction(economics.getId(),
            AutoInstructionType.PLATFORM_SERVICE_CHARGE_PARTNER);
        assertAutoInstruction(partnerSide, economics, book,
            views.get(AutoInstructionType.PLATFORM_SERVICE_CHARGE_PARTNER));
        long serviceExpense = existingAccount(book, RuleVersionRequestBuilder.PLATFORM_SERVICE_FEE_EXPENSE,
            OwnerType.OPERATING_ENTITY, ENTITY_NO, BalanceBucket.NONE);
        long servicePayable = existingAccount(book, RuleVersionRequestBuilder.PLATFORM_SERVICE_PAYABLE,
            OwnerType.FINANCE_ENTITY, fe, BalanceBucket.NONE);
        assertInstructionLines(partnerSide.getId(), debit(5209, serviceExpense, "0.5"),
            credit(5210, servicePayable, "0.5"));

        AccountingInstruction platformSide = instruction(economics.getId(),
            AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM);
        assertAutoInstruction(platformSide, economics, platformBook,
            views.get(AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM));
        long serviceReceivable = existingAccount(platformBook, RuleVersionRequestBuilder.PLATFORM_SERVICE_RECEIVABLE,
            OwnerType.OPERATING_ENTITY, ENTITY_NO, BalanceBucket.NONE);
        long serviceRevenue = existingAccount(platformBook, RuleVersionRequestBuilder.PLATFORM_SERVICE_REVENUE,
            OwnerType.FINANCE_ENTITY, fe, BalanceBucket.NONE);
        assertInstructionLines(platformSide.getId(), debit(5211, serviceReceivable, "0.5"),
            credit(5212, serviceRevenue, "0.5"));

        ledgerChecker.assertBalancedJournals(book, 2).assertBalancedJournals(platformBook, 1)
            .assertAccount(partnerPending, "0", "1", "1", 1).assertAccount(servicePayable, "0", "0.5", "0.5", 2)
            .assertAccount(serviceRevenue, "0", "0.5", "0.5", 1);
    }

    /** 手续费为 0 时收入行整组跳过；Seller 税与渠道成本组件存在时生成对应行。 */
    @Test
    void test_zeroFeeSkippedWithTaxAndChannelCost() {
        long book = oeLedger.getLedgerBookId();
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(payment("100"),
            clientPrincipal("95", CLIENT_ID), clientFee("0", ENTITY_NO), sellerTax("5", TAX_AUTHORITY_ID),
            channelCost("2"));

        List<PostedInstructionView> views = postEconomics(economics.getId());

        assertEquals(1, views.size());
        AccountingInstruction instruction = instruction(economics.getId(), AutoInstructionType.PAYMENT_SUCCEEDED);
        assertAutoInstruction(instruction, economics, book, views.get(0));
        String channelAccount = String.valueOf(channelAccountId);
        long taxPayable = existingAccount(book, RuleVersionRequestBuilder.TAX_PAYABLE, OwnerType.TAX_AUTHORITY,
            String.valueOf(TAX_AUTHORITY_ID), BalanceBucket.NONE);
        long channelExpense = existingAccount(book, RuleVersionRequestBuilder.CHANNEL_FEE_EXPENSE,
            OwnerType.OPERATING_ENTITY, ENTITY_NO, BalanceBucket.NONE);
        long channelAccrual = existingAccount(book, RuleVersionRequestBuilder.CHANNEL_FEE_ACCRUAL,
            OwnerType.CHANNEL_ACCOUNT, channelAccount, BalanceBucket.NONE);
        assertInstructionLines(instruction.getId(), debit(5201, channelReceivable(book), "100"),
            credit(5202, existingAccount(book, StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT,
                BalanceBucket.PENDING), "95"),
            credit(5204, taxPayable, "5"), debit(5205, channelExpense, "2"), credit(5206, channelAccrual, "2"));
        assertNull(account(book, RuleVersionRequestBuilder.CLIENT_FEE_REVENUE, OwnerType.OPERATING_ENTITY, ENTITY_NO,
            BalanceBucket.NONE), "零手续费不应创建收入账户");
        ledgerChecker.assertBalancedJournals(book, 1).assertAccount(taxPayable, "0", "5", "5", 1)
            .assertAccount(channelAccrual, "0", "2", "2", 1);
    }

    /** 同一经济结果重复调用：返回原指令与 Journal，不新增指令、Journal 或序号。 */
    @Test
    void test_repeatedPostReturnsOriginal() {
        LedgerFixture platform = economicsMocker.mockPlatformFinanceEntityLedger(PLATFORM_ENTITY_CODE,
            PLATFORM_LEGAL_PARTY_ID, CURRENCY);
        long financeEntityId = Long.parseLong(platform.getEntityNo());
        TransactionEconomicsRow economics = economicsMocker.mockEconomics(
            payment("100").partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, financeEntityId),
            clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO), partnerShare("1", PARTNER_ID),
            platformServiceCharge("0.5", financeEntityId));
        List<PostedInstructionView> first = sorted(postEconomics(economics.getId()));

        List<PostedInstructionView> second = postEconomics(economics.getId());

        assertEquals(sorted(second).stream().map(this::identity).toList(),
            first.stream().map(this::identity).toList());
        assertEquals(second, sorted(second), "幂等返回按账簿 ID、指令主键升序");
        assertAll(() -> assertEquals(3, testMapper.countAllInstructions()),
            () -> assertEquals(3, testMapper.countAllJournalEntries()));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 2)
            .assertBalancedJournals(platform.getLedgerBookId(), 1)
            .assertAccount(channelReceivable(oeLedger.getLedgerBookId()), "100", "0", "100", 1);
    }

    /** 渠道应收账户：Owner 为渠道清算账户主键，由引擎自动创建。 */
    private long channelReceivable(long book) {
        return existingAccount(book, RuleVersionRequestBuilder.CHANNEL_UNSETTLED_RECEIVABLE,
            OwnerType.CHANNEL_ACCOUNT, String.valueOf(channelAccountId), BalanceBucket.NONE);
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
