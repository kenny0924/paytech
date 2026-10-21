package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.reconciliation.api.bank.request.CreateBankMatchesRequestBody;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.event.ChannelSettlementConfirmedPayload;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.test.base.AbstractBankReconciliationTest;
import com.paytech.reconciliation.test.builder.BankStatementImportBuilder;
import com.paytech.reconciliation.test.builder.CashAccountBuilder;

/**
 * 银行匹配建议的确认 / 拒绝与人工多选匹配（AC-10，UC-R13，设计 F2、§6.4 不变量 5–6）
 * <li>确认后流水、预期流水与渠道账单进度推进，每条确认的渠道匹配发一条 ChannelSettlementConfirmed（按匹配 ID 幂等）。</li>
 * <li>人工匹配合计超出流水金额、单条超出预期剩余、跨银行账户、同一预期流水重复均被拒绝。</li>
 *
 * @see com.paytech.reconciliation.api.bank.BankMatchApi#confirmBankMatch(String, String, String)
 * @see com.paytech.reconciliation.api.bank.BankMatchApi#rejectBankMatch(String, String, String)
 * @see com.paytech.reconciliation.api.bank.BankMatchApi#createBankMatches(String, String, CreateBankMatchesRequestBody)
 * @see com.paytech.reconciliation.api.bank.BankMatchApi#listBankMatches(String, String, String, Integer, Integer)
 */
class BankMatchApiTest extends AbstractBankReconciliationTest {

    /** 导入后对金额相等的流水出建议；确认后 MATCHED / CONFIRMED / CASH_CONFIRMED 并发一条到账事件；重复确认不重发。 */
    @Test
    void test_confirmProposal() {
        CashAccount cash = activeCashAccount();
        ClearingAccount account = clearingAccountFor(cash);
        ExpectedBankMovement movement = reconciledMovement(account, cash, "67.55");
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-C1")
            .line("IN-1", SettlementDirection.CREDIT, "67.55").line("IN-2", SettlementDirection.CREDIT, "67.40"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();
        BankMatch proposal = bankMatches.listByLine(line.getId(), null).getFirst();
        assertAll(() -> assertEquals(BankMatchStatus.PROPOSED, proposal.getStatus()),
            () -> assertEquals(MatchMethod.AUTO, proposal.getMatchMethod()),
            () -> assertEquals(movement.getId(), proposal.getExpectedMovementId()),
            () -> assertEquals(List.of(), bankMatches.listByLine(bankStatementLines.listByStatement(statementId).get(1)
                .getId(), null)));

        postAsMaker(BANK_MATCHES + "/" + proposal.getId() + "/confirmation", null).assertSuccess(200)
            .assertText("status", BankMatchStatus.CONFIRMED.getCode()).assertText("matchedAmount", "67.55");
        awaitIdle();
        assertAll(() -> assertEquals(LineMatchStatus.MATCHED, bankStatementLines.findById(line.getId()).getMatchStatus()),
            () -> assertEquals(ExpectedMovementStatus.CONFIRMED, expectedMovements.findById(movement.getId()).getStatus()),
            () -> assertStatementStatus(movement.getSourceId(), ChannelStatementStatus.CASH_CONFIRMED));
        ChannelSettlementConfirmedPayload payload = events.payload(events.await(1).getFirst(),
            ChannelSettlementConfirmedPayload.class);
        assertAll(() -> assertEquals(proposal.getId(), payload.getBankMatchId()),
            () -> assertEquals(movement.getSourceId(), payload.getStatementId()),
            () -> assertEquals(account.getClearingAccountCode(), payload.getClearingAccountCode()),
            () -> assertEquals(cash.getId(), payload.getCashAccountId()),
            () -> assertEquals(SettlementDirection.CREDIT.getCode(), payload.getDirection()),
            () -> assertEquals(0, new BigDecimal("67.55").compareTo(payload.getMatchedAmount())),
            () -> assertEquals(line.getValueDate(), payload.getValueDate()));

        postAsMaker(BANK_MATCHES + "/" + proposal.getId() + "/confirmation", null).assertSuccess(200)
            .assertText("status", BankMatchStatus.CONFIRMED.getCode());
        postAsMaker(BANK_MATCHES + "/" + proposal.getId() + "/rejection", null).assertBusinessRejected("已确认");
        awaitIdle();
        events.assertNone();
        assertEquals(1, get(BANK_MATCHES + "?expectedMovementId=" + movement.getId()).assertSuccess(200)
            .assertPage(1, 10, 1).size());
        get(BANK_MATCHES).assertInvalidRequest();
    }

    /** 拒绝建议后同一对不再建议；被拒绝的建议不能确认。 */
    @Test
    void test_rejectProposal() {
        CashAccount cash = activeCashAccount();
        ExpectedBankMovement movement = reconciledMovement(clearingAccountFor(cash), cash, "10.00");
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-R1")
            .line("R-1", SettlementDirection.CREDIT, "10.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();
        BankMatch proposal = bankMatches.listByLine(line.getId(), null).getFirst();

        postAsMaker(BANK_MATCHES + "/" + proposal.getId() + "/rejection", null).assertSuccess(200)
            .assertText("status", BankMatchStatus.REJECTED.getCode());
        postAsMaker(BANK_MATCHES + "/" + proposal.getId() + "/confirmation", null).assertBusinessRejected("已拒绝");
        importedBank(BankStatementImportBuilder.of(cash, "BS-R2"));
        assertAll(() -> assertEquals(1, bankMatches.listByLine(line.getId(), null).size()),
            () -> assertEquals(ExpectedMovementStatus.OPEN, expectedMovements.findById(movement.getId()).getStatus()));
        events.assertNone();
    }

    /** 一条贷方流水分给两张账单（合并到账），另一笔借方预期流水带负号抵扣；每条渠道匹配一条事件。 */
    @Test
    void test_manualMultiSelect() {
        CashAccount cash = activeCashAccount();
        ClearingAccount account = clearingAccountFor(cash);
        ExpectedBankMovement first = reconciledMovement(account, cash, "60.00");
        ExpectedBankMovement second = reconciledMovement(account, cash, "50.00");
        ExpectedBankMovement debit = reconciledMovement(account, cash, SettlementDirection.DEBIT, "10.00");
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-M1")
            .line("M-1", SettlementDirection.CREDIT, "100.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();

        postAsMaker(BANK_MATCHES, matches(line, alloc(first, "60.00"), alloc(second, "50.00"), alloc(debit, "10.00")))
            .assertSuccess(201);
        awaitIdle();
        BankStatementLine matched = bankStatementLines.findById(line.getId());
        assertAll(() -> assertEquals(LineMatchStatus.MATCHED, matched.getMatchStatus()),
            () -> assertEquals(0, new BigDecimal("100").compareTo(matched.getMatchedAmount())),
            () -> assertEquals(3, bankMatches.listByLine(line.getId(), BankMatchStatus.CONFIRMED).size()),
            () -> assertEquals(MatchMethod.MANUAL, bankMatches.listByLine(line.getId(), null).getFirst()
                .getMatchMethod()),
            () -> assertStatementStatus(first.getSourceId(), ChannelStatementStatus.CASH_CONFIRMED),
            () -> assertStatementStatus(debit.getSourceId(), ChannelStatementStatus.CASH_CONFIRMED));
        List<DomainEvent> published = events.await(3);
        assertEquals(List.of(ChannelSettlementConfirmedPayload.EVENT_TYPE, ChannelSettlementConfirmedPayload.EVENT_TYPE,
            ChannelSettlementConfirmedPayload.EVENT_TYPE), published.stream().map(DomainEvent::eventType).toList());
    }

    /** 拒绝路径：超出流水金额、超出预期剩余、跨银行账户、同一预期流水重复、建议不存在；拒绝后零写入零事件。 */
    @Test
    void test_manualRejections() {
        CashAccount cash = activeCashAccount();
        ClearingAccount account = clearingAccountFor(cash);
        ExpectedBankMovement movement = reconciledMovement(account, cash, "60.00");
        ExpectedBankMovement other = reconciledMovement(account, cash, "40.00");
        CashAccount foreignCash = data.cashAccount(CashAccountBuilder.template()
            .with(item -> item.setEntityId("AE002")).build());
        ExpectedBankMovement foreign = reconciledMovement(clearingAccountFor(foreignCash), foreignCash, "50.00");
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-N1")
            .line("N-1", SettlementDirection.CREDIT, "50.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();

        postAsMaker(BANK_MATCHES, matches(line, alloc(movement, "30.00"), alloc(other, "30.00")))
            .assertBusinessRejected("超出银行流水金额");
        postAsMaker(BANK_MATCHES, matches(line, alloc(other, "45.00"))).assertBusinessRejected("超过预期银行流水剩余金额");
        postAsMaker(BANK_MATCHES, matches(line, alloc(foreign, "50.00"))).assertBusinessRejected("不是同一银行账户");
        postAsMaker(BANK_MATCHES, matches(line, alloc(movement, "10.00"), alloc(movement, "10.00")))
            .assertBusinessRejected("重复出现");
        postAsMaker(BANK_MATCHES + "/" + 1 + "/confirmation", null).assertBusinessRejected("银行匹配不存在");

        assertAll(() -> assertEquals(List.of(), bankMatches.listByLine(line.getId(), BankMatchStatus.CONFIRMED)),
            () -> assertEquals(0, BigDecimal.ZERO.compareTo(bankStatementLines.findById(line.getId()).getMatchedAmount())));
        events.assertNone();
    }

    /**
     * 钱比账单先到：先导入流水并开 BANK_LINE_UNMATCHED，预期银行流水生成（扩展点同一事务登记建议任务）后出建议，确认后异常 AUTO_CLOSED
     *
     * @see com.paytech.reconciliation.domainservice.service.ExpectedBankMovementService#createChannelSettlement(ChannelStatement, ClearingAccount)
     */
    @Test
    void test_resuggestWhenMovementArrives() {
        CashAccount cash = activeCashAccount();
        ClearingAccount account = clearingAccountFor(cash);
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-E1")
            .line("E-1", SettlementDirection.CREDIT, "25.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();
        long exceptionId = openException(ReconExceptionSourceType.BANK_STATEMENT_LINE, line.getId()).assertSuccess(201)
            .assertText("differenceAmount", "25.00").id("exceptionId");
        assertEquals(List.of(), bankMatches.listByLine(line.getId(), null));

        ChannelStatement statement = reconciledStatement(account, SettlementDirection.CREDIT, "25.00");
        ExpectedBankMovement movement = expectedMovementService.createChannelSettlement(statement, account);
        awaitIdle();
        BankMatch proposal = bankMatches.listByLine(line.getId(), BankMatchStatus.PROPOSED).getFirst();
        assertEquals(movement.getId(), proposal.getExpectedMovementId());

        postAsMaker(BANK_MATCHES + "/" + proposal.getId() + "/confirmation", null).assertSuccess(200);
        awaitIdle();
        assertAll(() -> assertExceptionStatus(exceptionId, ReconExceptionStatus.AUTO_CLOSED),
            () -> assertNull(reconExceptions.findById(exceptionId).getActiveSlot()));
        events.await(1);
    }

    /** 人工匹配请求。 */
    static CreateBankMatchesRequestBody matches(BankStatementLine line, CreateBankMatchesRequestBody.Allocation... items) {
        CreateBankMatchesRequestBody body = new CreateBankMatchesRequestBody();
        body.setBankStatementLineId(line.getId().toString());
        body.setAllocations(List.of(items));
        return body;
    }

    /** 一条分配。 */
    static CreateBankMatchesRequestBody.Allocation alloc(ExpectedBankMovement movement, String amount) {
        CreateBankMatchesRequestBody.Allocation allocation = new CreateBankMatchesRequestBody.Allocation();
        allocation.setExpectedMovementId(movement.getId().toString());
        allocation.setAmount(amount);
        return allocation;
    }
}
