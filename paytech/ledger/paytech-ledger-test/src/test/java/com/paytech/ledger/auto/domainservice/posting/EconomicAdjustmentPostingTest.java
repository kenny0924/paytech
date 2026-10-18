package com.paytech.ledger.auto.domainservice.posting;

import static com.paytech.ledger.test.builder.EconomicComponentBuilder.adjustment;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.ledger.api.response.LedgerAccountTotalsResponseBody;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.TransactionEconomicsBuilder;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 人工 / 成本调整经济事实按 STANDARD_ADJUSTMENT 规则包过账（第 11b 步补充要求，design/04 §4 第 3 行）
 * <li>扮演 billing 发布 MANUAL_ADJUSTMENT / COST_ADJUSTMENT 事实，经 ledger.billing 分区队列由正式 Handler 过账：每条明细行付方记借方、收方记贷方，
 * 按费用类型 × 角色 × 方向匹配初始化 DML 中的规则行（Bundle 5002 / 版本 5102 / 规则行 5301–5332）。</li>
 * <li>三服务链路的 ledger 段：{@link #test_manualAdjustmentPostsByPartyRoles} 与 account 的
 * {@code EconomicFactBalanceConsumeTest.test_adjustmentRolesMapToBalances} 使用同形事实，Owner 账户分录聚合值与 account 余额一致。</li>
 * <li>无法映射的组合（PLATFORM / CONSUMER 角色、没有规则行的费用类型 × 角色、缺少付方或收方）整笔拒绝。</li>
 */
class EconomicAdjustmentPostingTest extends AbstractTransactionPostingTest {
    /** STANDARD_ADJUSTMENT 版本 1 的主键。 */
    private static final long ADJUSTMENT_VERSION_ID = 5102L;

    private static final String CLIENT = String.valueOf(CLIENT_ID);

    private static final String PARTNER = String.valueOf(PARTNER_ID);

    @Test
    void test_manualAdjustmentPostsByPartyRoles() {
        EconomicFactPayload fact = fact(manual().partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, 8001L),
            adjustment(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                CLIENT, FactLineDirection.CHARGE, "10"),
            adjustment(AmountSource.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactLineDirection.ADJUSTMENT, "1"),
            adjustment(AmountSource.PARTNER_SHARE, FactPartyRole.LOCAL_PARTNER, PARTNER, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, FactLineDirection.CHARGE, "4"));

        var views = postFact(fact);

        assertEquals(1, views.size());
        long book = oeLedger.getLedgerBookId();
        AccountingInstruction instruction = assertAdjustmentInstruction(fact, AutoInstructionType.ECONOMIC_ADJUSTMENT);
        long clientPending = existingAccount(book, StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT,
            BalanceBucket.PENDING);
        long partnerPending = existingAccount(book, StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER,
            PARTNER, BalanceBucket.PENDING);
        long reconExpense = existingAccount(book, "RECON_ADJUSTMENT_EXPENSE", OwnerType.OPERATING_ENTITY, ENTITY_NO,
            BalanceBucket.NONE);
        long feeRevenue = existingAccount(book, "CLIENT_FEE_REVENUE", OwnerType.OPERATING_ENTITY, ENTITY_NO,
            BalanceBucket.NONE);
        long partnerExpense = existingAccount(book, "PARTNER_SHARE_EXPENSE", OwnerType.OPERATING_ENTITY, ENTITY_NO,
            BalanceBucket.NONE);
        assertInstructionLines(instruction.getId(), debit(5301, reconExpense, "10"), credit(5316, clientPending, "10"),
            debit(5317, clientPending, "1"), credit(5304, feeRevenue, "1"), debit(5319, partnerPending, "4"),
            credit(5306, partnerExpense, "4"));
        ledgerChecker.assertBalancedJournals(book, 1).assertAccount(clientPending, "1", "10", "9", 1)
            .assertAccount(partnerPending, "4", "0", "-4", 1);

        var totals = get("/ledger/v1/books/" + book + "/account-totals").assertSuccess(200)
            .as(LedgerAccountTotalsResponseBody.class);
        assertEquals(List.of(StandardAccountCodes.CLIENT_PAYABLE + ":9.00", StandardAccountCodes.PARTNER_PAYABLE + ":-4.00"),
            totals.getItems().stream().map(item -> item.getAccountCode() + ":" + item.getNetBalance()).toList(), "Owner 余额与 account 映射一致");
    }

    @Test
    void test_costAdjustmentPostsChannelCost() {
        EconomicFactPayload fact = fact(payment("3").economicsType(EconomicsType.COST_ADJUSTMENT),
            adjustment(AmountSource.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL, "77",
                FactLineDirection.ADJUSTMENT, "3"));

        postFact(fact);

        long book = oeLedger.getLedgerBookId();
        AccountingInstruction instruction = assertAdjustmentInstruction(fact, AutoInstructionType.CHANNEL_COST_ADJUSTMENT);
        long expense = existingAccount(book, "CHANNEL_FEE_EXPENSE", OwnerType.OPERATING_ENTITY, ENTITY_NO,
            BalanceBucket.NONE);
        long accrual = existingAccount(book, StandardAccountCodes.CHANNEL_FEE_ACCRUAL, OwnerType.CHANNEL_ACCOUNT,
            String.valueOf(CHANNEL_ACCOUNT_ID), BalanceBucket.NONE);
        assertInstructionLines(instruction.getId(), debit(5331, expense, "3"), credit(5330, accrual, "3"));
    }

    @Test
    void test_reversalSwapsSides() {
        EconomicFactPayload fact = fact(manual(), adjustment(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, FactPartyRole.CLIENT, CLIENT, FactLineDirection.REVERSAL, "5"));

        postFact(fact);

        AccountingInstruction instruction = assertAdjustmentInstruction(fact, AutoInstructionType.ECONOMIC_ADJUSTMENT);
        long book = oeLedger.getLedgerBookId();
        assertInstructionLines(instruction.getId(),
            credit(5302, existingAccount(book, "RECON_ADJUSTMENT_EXPENSE", OwnerType.OPERATING_ENTITY, ENTITY_NO,
                BalanceBucket.NONE), "5"),
            debit(5315, existingAccount(book, StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT,
                BalanceBucket.PENDING), "5"));
    }

    /** 真正无法映射的组合：PLATFORM 角色、没有规则行的费用类型 × 角色、缺少收方、成本调整中的非渠道成本。 */
    @Test
    void test_unmappableCombinationRejected() {
        assertRejected(fact(manual(), adjustment(AmountSource.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT,
            FactPartyRole.PLATFORM, "1", FactLineDirection.CHARGE, "1")), "角色没有记账规则 PLATFORM");
        assertRejected(fact(manual(), adjustment(AmountSource.SELLER_TAX, FactPartyRole.CLIENT, CLIENT,
            FactPartyRole.TAX_AUTHORITY, "4001", FactLineDirection.CHARGE, "1")), "匹配 0 条规则行");
        assertRejected(fact(manual(), adjustment(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, null, null, FactLineDirection.CHARGE, "1")), "缺少付方或收方");
        assertRejected(fact(payment("1").economicsType(EconomicsType.COST_ADJUSTMENT), adjustment(
            AmountSource.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
            FactLineDirection.CHARGE, "1")), "匹配 0 条规则行");
        assertAll(() -> assertEquals(0, testMapper.countAllInstructions()),
            () -> assertEquals(0, testMapper.countAllAccounts(), "拒绝后不残留自动创建的账户"));
    }

    /** 人工调整事实模板。 */
    static TransactionEconomicsBuilder manual() {
        return payment("0").economicsType(EconomicsType.MANUAL_ADJUSTMENT);
    }

    /** 断言事实只生成一条已过账的 AUTO 指令：规则版本 5102、STANDARD 分录、过账日为登记时间在日历时区的日期。 */
    AccountingInstruction assertAdjustmentInstruction(EconomicFactPayload fact, AutoInstructionType type) {
        AccountingInstruction instruction = instruction(fact.getFactId(), type);
        JournalEntry journal = testMapper.selectJournalEntryByInstruction(instruction.getId());
        assertAll(() -> assertEquals(InstructionStatus.POSTED, instruction.getStatus()),
            () -> assertEquals(ADJUSTMENT_VERSION_ID, instruction.getAccountingRuleBundleVerId()),
            () -> assertEquals(oeLedger.getLedgerBookId(), instruction.getLedgerBookId()),
            () -> assertEquals(JournalEntryType.STANDARD, journal.getEntryType()),
            () -> assertEquals(LocalDate.ofInstant(fact.getRecordedTime(), ZoneId.of(LedgerMocker.TIMEZONE)),
                journal.getPostingDate()));
        return instruction;
    }

    /** Handler 拒绝且原因含给定片段。 */
    void assertRejected(EconomicFactPayload fact, String fragment) {
        DomainEventRejectedException rejected = assertThrows(DomainEventRejectedException.class,
            () -> consumeDirectly(fact));
        String message = assertInstanceOf(BizCheckFailException.class, rejected.getCause()).getMessage();
        assertTrue(message.contains(fragment), "拒绝消息应含 [" + fragment + "]: " + message);
    }
}
