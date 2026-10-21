package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.reconciliation.api.bank.request.AppendBankStatementLinesRequestBody;
import com.paytech.reconciliation.api.bank.request.StartBankStatementImportRequestBody;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.test.base.AbstractBankReconciliationTest;
import com.paytech.reconciliation.test.builder.BankStatementImportBuilder;
import com.paytech.reconciliation.test.checker.ReconciliationResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 银行对账单分段导入（AC-09，UC-R11，technique D-01、§2.5）
 * <li>期初 + Σ ≠ 期末整单 REJECTED 并给原因；与上一张接不上时导入成功并开不阻塞的 BANK_STATEMENT_GAP；同一银行账户的交易号只入库一次。</li>
 *
 * @see com.paytech.reconciliation.api.bank.BankStatementApi#startBankStatementImport(String, String, StartBankStatementImportRequestBody)
 * @see com.paytech.reconciliation.api.bank.BankStatementApi#appendBankStatementLines(String, String, String, AppendBankStatementLinesRequestBody)
 * @see com.paytech.reconciliation.api.bank.BankStatementApi#completeBankStatementImport(String, String, String)
 * @see com.paytech.reconciliation.api.bank.BankStatementApi#listBankStatementLinesOfStatement(String, Integer, Integer)
 */
class BankStatementImportTest extends AbstractBankReconciliationTest {

    /** 三步导入成功：IMPORTED、行入库、无连续性异常；重复完成返回当前状态；行查询按行号升序。 */
    @Test
    void test_importAndReplay() {
        CashAccount cash = activeCashAccount();
        BankStatementImportBuilder statement = BankStatementImportBuilder.of(cash, "BS-1").opening("1000.00")
            .line("TX-1", SettlementDirection.CREDIT, "67.40").line("TX-2", SettlementDirection.DEBIT, "10.00");
        long statementId = startBank(statement).assertSuccess(201)
            .assertText("status", BankStatementStatus.RECEIVING.getCode()).id("bankStatementId");
        appendBank(statementId, statement.lines()).assertSuccess(200).assertText("importedLineCount", "2");
        appendBank(statementId, statement.lines()).assertSuccess(200).assertText("importedLineCount", "2");

        completeBank(statementId).assertSuccess(200).assertText("status", BankStatementStatus.IMPORTED.getCode())
            .assertText("gapExceptionId", null).assertText("duplicateLineCount", "0");
        awaitIdle();
        completeBank(statementId).assertSuccess(200).assertText("status", BankStatementStatus.IMPORTED.getCode());
        List<JsonNode> lines = get(BANK_STATEMENTS + "/" + statementId + "/lines").assertSuccess(200).assertPage(1, 10, 2);
        assertAll(() -> assertEquals("TX-1", lines.get(0).path("bankTransactionRef").asString()),
            () -> assertEquals("67.40", lines.get(0).path("amount").asString()),
            () -> assertEquals("UNMATCHED", lines.get(1).path("matchStatus").asString()));
        appendBank(statementId, statement.lines()).assertBusinessRejected("不在接收中");
    }

    /** 期初 + Σ ≠ 期末、起息日不在期间内：整单 REJECTED，带逐行原因，已接收流水删除；同号不同内容与金额 0 在追加时拒绝。 */
    @Test
    void test_rejections() {
        CashAccount cash = activeCashAccount();
        LocalDate end = BankStatementImportBuilder.today();
        BankStatementImportBuilder statement = BankStatementImportBuilder.of(cash, "BS-BAD").period(end.minusDays(1), end)
            .line("TX-A", SettlementDirection.CREDIT, "50.00").line("TX-B", SettlementDirection.CREDIT, "5.00",
                end.plusDays(1)).closing("60.00");
        long statementId = startBank(statement).assertSuccess(201).id("bankStatementId");
        appendBank(statementId, statement.lines()).assertSuccess(200);

        AppendBankStatementLinesRequestBody.Line changed = statement.lines().getFirst();
        changed.setAmount("51.00");
        appendBank(statementId, List.of(changed)).assertBusinessRejected("同一行号已接收不同内容");
        changed.setAmount("0");
        appendBank(statementId, List.of(changed)).assertBusinessRejected("流水金额必须大于 0");
        changed.setAmount("50.00");

        ReconciliationResponseChecker completed = completeBank(statementId).assertSuccess(200)
            .assertText("status", BankStatementStatus.REJECTED.getCode());
        List<JsonNode> reasons = List.copyOf(completed.json().path("rejections").valueStream().toList());
        assertAll(() -> assertEquals(2, reasons.size()),
            () -> assertTrue(reasons.get(0).path("lineNo").isNull()),
            () -> assertTrue(reasons.get(0).path("reason").asString().contains("与期末余额 60.00 不一致")),
            () -> assertEquals(2, reasons.get(1).path("lineNo").asInt()),
            () -> assertTrue(reasons.get(1).path("reason").asString().contains("不在对账单期间")),
            () -> assertTrue(completed.text("rejectReason").startsWith("校验不通过，共 2 条原因")),
            () -> assertEquals(List.of(), bankStatementLines.listByStatement(statementId)));
        completeBank(statementId).assertSuccess(200).assertText("status", BankStatementStatus.REJECTED.getCode());
        assertEquals(0, completeBank(statementId).json().path("rejections").size());
    }

    /** 开始导入的头部校验：末四位、币种、期间倒置、对账单编号重复。 */
    @Test
    void test_startValidation() {
        CashAccount cash = activeCashAccount();
        LocalDate today = BankStatementImportBuilder.today();
        startBank(BankStatementImportBuilder.of(cash, "BS-L4").last4("9999")).assertBusinessRejected("末四位");
        startBank(BankStatementImportBuilder.of(cash, "BS-CUR").currency("EUR")).assertBusinessRejected("币种");
        startBank(BankStatementImportBuilder.of(cash, "BS-PER").period(today, today.minusDays(1)))
            .assertBusinessRejected("结束日早于开始日");
        importedBank(BankStatementImportBuilder.of(cash, "BS-DUP"));
        startBank(BankStatementImportBuilder.of(cash, "BS-DUP")).assertBusinessRejected("对账单编号已存在");
        startBank(BankStatementImportBuilder.of(cash, "BS-AMT").opening("1.001")).assertBusinessRejected("精度");
    }

    /** 期初不等于上一张期末或期间不相接：导入成功并开不阻塞的 BANK_STATEMENT_GAP；接得上的不开。 */
    @Test
    void test_continuityGap() {
        CashAccount cash = activeCashAccount();
        LocalDate today = BankStatementImportBuilder.today();
        importedBank(BankStatementImportBuilder.of(cash, "BS-P1").period(today.minusDays(9), today.minusDays(5))
            .opening("100.00").line("G-1", SettlementDirection.CREDIT, "20.00", today.minusDays(6)));
        ReconciliationResponseChecker continuous = importBank(BankStatementImportBuilder.of(cash, "BS-P2")
            .period(today.minusDays(4), today.minusDays(3)).opening("120.00"));
        continuous.assertText("status", BankStatementStatus.IMPORTED.getCode()).assertText("gapExceptionId", null);

        ReconciliationResponseChecker gapped = importBank(BankStatementImportBuilder.of(cash, "BS-P3")
            .period(today.minusDays(1), today).opening("130.00").line("G-2", SettlementDirection.DEBIT, "30.00"));
        gapped.assertText("status", BankStatementStatus.IMPORTED.getCode());
        ReconException gap = reconExceptions.findById(gapped.id("gapExceptionId"));
        assertAll(() -> assertEquals(ReconExceptionType.BANK_STATEMENT_GAP, gap.getExceptionType()),
            () -> assertEquals(ReconExceptionSourceType.BANK_STATEMENT, gap.getSourceType()),
            () -> assertEquals(ReconLayer.BANK, gap.getReconLayer()), () -> assertEquals(false, gap.getBlocking()),
            () -> assertEquals(cash.getId(), gap.getCashAccountId()), () -> assertNull(gap.getStatementId()),
            () -> assertEquals(0, new BigDecimal("10.00").compareTo(gap.getDifferenceAmount())),
            () -> assertEquals(ReconExceptionStatus.OPEN, gap.getStatus()));
    }

    /**
     * 交易号重复（已在其他对账单导入、或本文件另一行已用）：追加时不入库、计数且同一段重发不重复计，完成时整单 REJECTED 并给逐行原因；
     * 同编号更正重传复用原对账单头重新接收（2026-10-20 主会话决定）
     */
    @Test
    void test_duplicateRejectedAndResend() {
        CashAccount cash = activeCashAccount();
        LocalDate today = BankStatementImportBuilder.today();
        importedBank(BankStatementImportBuilder.of(cash, "BS-D1").period(today.minusDays(1), today)
            .line("DUP-1", SettlementDirection.CREDIT, "100.00"));
        BankStatementImportBuilder overlapping = BankStatementImportBuilder.of(cash, "BS-D2")
            .period(today.minusDays(1), today).line("DUP-1", SettlementDirection.CREDIT, "100.00")
            .line("NEW-1", SettlementDirection.CREDIT, "50.00").line("NEW-1", SettlementDirection.CREDIT, "5.00");
        long statementId = startBank(overlapping).assertSuccess(201).id("bankStatementId");
        appendBank(statementId, overlapping.lines()).assertSuccess(200).assertText("importedLineCount", "1")
            .assertText("duplicateLineCount", "2").assertText("rejectReason", null);
        appendBank(statementId, overlapping.lines()).assertSuccess(200).assertText("duplicateLineCount", "2");

        ReconciliationResponseChecker completed = completeBank(statementId).assertSuccess(200)
            .assertText("status", BankStatementStatus.REJECTED.getCode()).assertText("duplicateLineCount", "2");
        List<JsonNode> reasons = completed.json().path("rejections").valueStream().toList();
        assertAll(() -> assertEquals(2, reasons.size()), () -> assertEquals(1, reasons.get(0).path("lineNo").asInt()),
            () -> assertEquals("银行交易号 DUP-1 已在对账单 BS-D1 导入，请上传不重叠的对账单",
                reasons.get(0).path("reason").asString()),
            () -> assertEquals(3, reasons.get(1).path("lineNo").asInt()),
            () -> assertEquals("银行交易号 NEW-1 与第 2 行重复", reasons.get(1).path("reason").asString()),
            () -> assertEquals(List.of(), bankStatementLines.listByStatement(statementId)),
            () -> assertEquals(1, bankStatementLines.listByTransactionRefs(cash.getId(), List.of("DUP-1")).size()));

        BankStatementImportBuilder corrected = BankStatementImportBuilder.of(cash, "BS-D2").period(today, today)
            .opening("100.00").line("NEW-1", SettlementDirection.CREDIT, "50.00")
            .line("NEW-2", SettlementDirection.CREDIT, "5.00");
        startBank(corrected).assertSuccess(201).assertText("bankStatementId", String.valueOf(statementId))
            .assertText("status", BankStatementStatus.RECEIVING.getCode()).assertText("importedLineCount", "0")
            .assertText("duplicateLineCount", "0").assertText("rejectReason", null).assertText("declaredLineCount", "2");
        appendBank(statementId, corrected.lines()).assertSuccess(200);
        completeBank(statementId).assertSuccess(200).assertText("status", BankStatementStatus.IMPORTED.getCode());
        awaitIdle();
        startBank(BankStatementImportBuilder.of(cash, "BS-D1")).assertBusinessRejected("对账单编号已存在");
        BankStatementImportBuilder receiving = BankStatementImportBuilder.of(cash, "BS-D3")
            .line("R-1", SettlementDirection.CREDIT, "1.00");
        startBank(receiving).assertSuccess(201);
        startBank(receiving).assertBusinessRejected("对账单编号已存在");
    }
}
