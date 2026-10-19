package com.paytech.ledger.auto.domainservice.posting;

import static com.paytech.ledger.test.builder.EconomicComponentBuilder.adjustment;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.AutoInstructionType;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.builder.AccountPostingBuilder;
import com.paytech.ledger.test.builder.TransactionEconomicsBuilder;

/**
 * 人工 / 成本调整事实的控制科目过账（STANDARD_ADJUSTMENT，Bundle 5002 / 版本 5102）
 * <li>每条明细行付方记借方、收方记贷方；Client / Local Partner 一侧由 account 流水记到 OE 控制科目，其余角色按“费用类型 × 角色 × 方向”匹配规则行。</li>
 * <li>版本替代：替代事实的指令 = 新事实规则行 + 流水（含冲回行）+ 被替代事实正向规则行取反，同一指令内净额替代；多次替代后只剩最新事实。</li>
 * <li>替代先到：被替代事实后到的 SKIPPED 批次不读事实、不生成任何行，只推进进度。</li>
 * <li>无法映射的组合（PLATFORM 角色、没有规则行的费用类型 × 角色、缺少付方或收方）整笔拒绝。</li>
 *
 * @see com.paytech.ledger.ext.mq.AccountPostedPostingHandler#consume(com.paytech.foundation.messaging.api.DomainEvent)
 * @see com.paytech.ledger.domainservice.service.TransactionPostingService#post(com.paytech.ledger.domain.AccountPostingCommand)
 */
class EconomicAdjustmentPostingTest extends AbstractTransactionPostingTest {
    private static final String CLIENT = String.valueOf(CLIENT_ID);

    private static final String PARTNER = String.valueOf(PARTNER_ID);

    /** Client / Partner 两侧记控制科目流水行，OE 一侧按规则：本金、手续费、分成三行六侧。 */
    @Test
    void test_manualAdjustmentPostsByPartyRoles() {
        EconomicFactPayload fact = fact(manual().partner(PARTNER_ID, PARTNER_AGREEMENT_OE_ID, 8001L),
            adjustment(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                CLIENT, FactLineDirection.CHARGE, "10"),
            adjustment(AmountSource.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactLineDirection.ADJUSTMENT, "1"),
            adjustment(AmountSource.PARTNER_SHARE, FactPartyRole.LOCAL_PARTNER, PARTNER, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, FactLineDirection.CHARGE, "4"));
        AccountPostedPayload posting = posting(fact, 1)
            .entry(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, EntrySide.CREDIT,
                "10", 1)
            .entry(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, EntrySide.DEBIT,
                "1", 2)
            .entry(StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER, PARTNER, BalanceBucket.PENDING,
                EntrySide.DEBIT, "4", 3).build();

        var views = postBatch(fact, posting);

        assertEquals(1, views.size());
        long book = oeLedger.getLedgerBookId();
        AccountingInstruction instruction = instruction(posting, AutoInstructionType.ECONOMIC_ADJUSTMENT);
        assertPostingInstruction(instruction, fact, posting, book, ADJUSTMENT_VERSION_ID, recordedDate(fact));
        long clientControl = controlAccount(StandardAccountCodes.CLIENT_PAYABLE);
        long partnerControl = controlAccount(StandardAccountCodes.PARTNER_PAYABLE);
        assertInstructionLines(instruction.getId(), debit(5301, oeAccount("RECON_ADJUSTMENT_EXPENSE"), "10"),
            credit(5304, oeAccount("CLIENT_FEE_REVENUE"), "1"), credit(5306, oeAccount("PARTNER_SHARE_EXPENSE"), "4"),
            entry(posting.getEntries().get(0), clientControl), entry(posting.getEntries().get(1), clientControl),
            entry(posting.getEntries().get(2), partnerControl));
        ledgerChecker.assertBalancedJournals(book, 1).assertAccount(clientControl, "1", "10", "9", 1)
            .assertAccount(partnerControl, "4", "0", "-4", 1);
    }

    /** 成本调整不影响 Owner：0 流水批次，只有渠道成本两条规则行。 */
    @Test
    void test_costAdjustmentPostsChannelCost() {
        EconomicFactPayload fact = fact(payment("3").economicsType(EconomicsType.COST_ADJUSTMENT),
            adjustment(AmountSource.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL, "77",
                FactLineDirection.ADJUSTMENT, "3"));
        AccountPostedPayload posting = posting(fact, 1).build();

        postBatch(fact, posting);

        long book = oeLedger.getLedgerBookId();
        AccountingInstruction instruction = instruction(posting, AutoInstructionType.CHANNEL_COST_ADJUSTMENT);
        assertPostingInstruction(instruction, fact, posting, book, ADJUSTMENT_VERSION_ID, recordedDate(fact));
        assertInstructionLines(instruction.getId(), debit(5331, oeAccount("CHANNEL_FEE_EXPENSE"), "3"),
            credit(5330, existingAccount(book, StandardAccountCodes.CHANNEL_FEE_ACCRUAL, LedgerOwnerType.CHANNEL_ACCOUNT,
                String.valueOf(CHANNEL_ACCOUNT_ID)), "3"));
    }

    /** REVERSAL 方向：OE 一侧改记贷方规则行，Client 一侧为借方流水。 */
    @Test
    void test_reversalSwapsSides() {
        EconomicFactPayload fact = fact(manual(), adjustment(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY,
            ENTITY_NO, FactPartyRole.CLIENT, CLIENT, FactLineDirection.REVERSAL, "5"));
        AccountPostedPayload posting = posting(fact, 1).entry(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT,
            CLIENT, BalanceBucket.PENDING, EntrySide.DEBIT, "5", 1).build();

        postBatch(fact, posting);

        AccountingInstruction instruction = instruction(posting, AutoInstructionType.ECONOMIC_ADJUSTMENT);
        assertInstructionLines(instruction.getId(), credit(5302, oeAccount("RECON_ADJUSTMENT_EXPENSE"), "5"),
            entry(posting.getEntries().get(0), controlAccount(StandardAccountCodes.CLIENT_PAYABLE)));
    }

    /**
     * 两次替代的净额：F2 替代 F1、F3 替代 F2；每个替代批次的指令 = 新规则行 + 流水（含冲回）+ 前一事实正向规则行取反，
     * 前一事实中已是反向的规则行不再取反，最终科目净额只剩 F3
     */
    @Test
    void test_supersedingFactsNetToLatest() {
        EconomicFactPayload first = clientCharge(manual(), "10");
        AccountPostedPayload firstPosting = clientCredit(posting(first, 1), "10", null).build();
        postBatch(first, firstPosting);
        EconomicFactPayload second = clientCharge(manual().supersedes(first), "12");
        AccountPostedPayload secondPosting = clientCredit(posting(second, 2).entry(StandardAccountCodes.CLIENT_PAYABLE,
            OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, EntrySide.DEBIT, "10", 1)
            .reverses(firstPosting.getEntries().get(0).getEntryId()), "12", null).build();

        postBatch(second, secondPosting);

        long expense = oeAccount("RECON_ADJUSTMENT_EXPENSE");
        long control = controlAccount(StandardAccountCodes.CLIENT_PAYABLE);
        AccountingInstruction secondInstruction = instruction(secondPosting, AutoInstructionType.ECONOMIC_ADJUSTMENT);
        assertInstructionLines(secondInstruction.getId(), debit(5301, expense, "12"),
            entry(secondPosting.getEntries().get(0), control), entry(secondPosting.getEntries().get(1), control),
            credit(5301, expense, "10"));
        ledgerChecker.assertAccount(expense, "22", "10", "12", 2).assertAccount(control, "10", "22", "12", 2);

        EconomicFactPayload third = clientCharge(manual().supersedes(second), "7");
        AccountPostedPayload thirdPosting = clientCredit(posting(third, 3).entry(StandardAccountCodes.CLIENT_PAYABLE,
            OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, EntrySide.DEBIT, "12", 1)
            .reverses(secondPosting.getEntries().get(1).getEntryId()), "7", null).build();

        postBatch(third, thirdPosting);

        assertInstructionLines(instruction(thirdPosting, AutoInstructionType.ECONOMIC_ADJUSTMENT).getId(),
            debit(5301, expense, "7"), entry(thirdPosting.getEntries().get(0), control),
            entry(thirdPosting.getEntries().get(1), control), credit(5301, expense, "12"));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 3)
            .assertAccount(expense, "29", "22", "7", 3).assertAccount(control, "22", "29", "7", 3);
        assertEquals(3, consumedSequence(thirdPosting));
    }

    /** 替代先到：F2 批次没有可取反的 F1 行；F1 后到的 SKIPPED 批次不读事实、不过账，只推进进度。 */
    @Test
    void test_skippedBatchOnlyAdvances() {
        EconomicFactPayload first = clientCharge(manual(), "10");
        EconomicFactPayload second = clientCharge(manual().supersedes(first), "12");
        AccountPostedPayload secondPosting = clientCredit(posting(second, 1), "12", null).build();
        postBatch(second, secondPosting);
        AccountPostedPayload skipped = posting(first, 2).skipped().build();

        List<?> views = publishAndAwait(skipped);

        assertAll(() -> assertTrue(views.isEmpty(), "SKIPPED 批次不生成指令"),
            () -> assertEquals(2, consumedSequence(skipped)),
            () -> assertEquals(1, testMapper.countAllInstructions()),
            () -> assertEquals(0, economicFacts.factCalls(first.getFactId()), "SKIPPED 批次不读事实"));
        assertInstructionLines(instruction(secondPosting, AutoInstructionType.ECONOMIC_ADJUSTMENT).getId(),
            debit(5301, oeAccount("RECON_ADJUSTMENT_EXPENSE"), "12"),
            entry(secondPosting.getEntries().get(0), controlAccount(StandardAccountCodes.CLIENT_PAYABLE)));
    }

    /**
     * v1 已应用 → v3 先到（替代 v2）→ v2 后到为 SKIPPED 但带 v1 的冲回流水：v2 批次不对自身应用规则，只记冲回流水行与 v1 正向规则行的反向行，
     * 最终科目净额只剩 v3
     */
    @Test
    void test_skippedBatchWithReversalEntries() {
        EconomicFactPayload first = clientCharge(manual(), "10");
        AccountPostedPayload firstPosting = clientCredit(posting(first, 1), "10", null).build();
        postBatch(first, firstPosting);
        EconomicFactPayload second = clientCharge(manual().supersedes(first), "12");
        EconomicFactPayload third = clientCharge(manual().supersedes(second), "7");
        postBatch(third, clientCredit(posting(third, 2), "7", null).build());
        AccountPostedPayload skipped = posting(second, 3).skipped().entry(StandardAccountCodes.CLIENT_PAYABLE,
            OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING, EntrySide.DEBIT, "10", 1)
            .reverses(firstPosting.getEntries().get(0).getEntryId()).build();

        postBatch(second, skipped);

        long expense = oeAccount("RECON_ADJUSTMENT_EXPENSE");
        long control = controlAccount(StandardAccountCodes.CLIENT_PAYABLE);
        assertInstructionLines(instruction(skipped, AutoInstructionType.ECONOMIC_ADJUSTMENT).getId(),
            entry(skipped.getEntries().get(0), control), credit(5301, expense, "10"));
        ledgerChecker.assertBalancedJournals(oeLedger.getLedgerBookId(), 3)
            .assertAccount(expense, "17", "10", "7", 3).assertAccount(control, "10", "17", "7", 3);
        assertEquals(3, consumedSequence(skipped));
    }

    /** 真正无法映射的组合：PLATFORM 角色、没有规则行的费用类型 × 角色、缺少收方、成本调整中的非渠道成本。 */
    @Test
    void test_unmappableCombinationRejected() {
        assertRejected(fact(manual(), adjustment(AmountSource.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT,
            FactPartyRole.PLATFORM, "1", FactLineDirection.CHARGE, "1")), "角色没有记账规则 PLATFORM");
        assertRejected(fact(manual(), adjustment(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.FINANCE_ENTITY, "8001",
            FactPartyRole.CLIENT, CLIENT, FactLineDirection.CHARGE, "1")), "匹配 0 条规则行");
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

    /** 一行 OE 向 Client 支付本金的人工调整。 */
    EconomicFactPayload clientCharge(TransactionEconomicsBuilder builder, String amount) {
        return fact(builder, adjustment(AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
            FactPartyRole.CLIENT, CLIENT, FactLineDirection.CHARGE, amount));
    }

    /** 追加 Client 应付贷方流水。 */
    static AccountPostingBuilder clientCredit(AccountPostingBuilder builder, String amount, Long reversesEntryId) {
        builder.entry(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, CLIENT, BalanceBucket.PENDING,
            EntrySide.CREDIT, amount, 1);
        return reversesEntryId == null ? builder : builder.reverses(reversesEntryId);
    }

    /** 交易 OE 账簿下以 OE 为 Owner 的科目账户。 */
    long oeAccount(String accountCode) {
        return existingAccount(oeLedger.getLedgerBookId(), accountCode, LedgerOwnerType.OPERATING_ENTITY, ENTITY_NO);
    }

    /** Handler 拒绝且原因含给定片段；批次不含流水，拒绝发生在规则匹配阶段。 */
    void assertRejected(EconomicFactPayload fact, String fragment) {
        economicFacts.mockFact(fact);
        DomainEventRejectedException rejected = assertThrows(DomainEventRejectedException.class,
            () -> consumeDirectly(posting(fact, 1).build()));
        String message = assertInstanceOf(BizCheckFailException.class, rejected.getCause()).getMessage();
        assertTrue(message.contains(fragment), "拒绝消息应含 [" + fragment + "]: " + message);
    }
}
