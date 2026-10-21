package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.statement.request.AppendChannelStatementLinesRequestBody;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.test.base.AbstractReconciliationFlowTest;
import com.paytech.reconciliation.test.builder.ChannelStatementBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.builder.StatementImportBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import tools.jackson.databind.JsonNode;

/**
 * 渠道账单分段导入（AC-05，technique D-01）：开始 → 分段追加（按行号幂等）→ 完成时一次性校验
 * <li>校验不通过整单 REJECTED 并返回逐行原因；账单币种须等于清算账户币种；同一批次只允许一个未作废版本，已对平不能更正。</li>
 *
 * @see com.paytech.reconciliation.api.statement.ChannelStatementApi#startChannelStatementImport(String, String, com.paytech.reconciliation.api.statement.request.StartChannelStatementImportRequestBody)
 * @see com.paytech.reconciliation.api.statement.ChannelStatementApi#appendChannelStatementLines(String, String, String, AppendChannelStatementLinesRequestBody)
 * @see com.paytech.reconciliation.api.statement.ChannelStatementApi#completeChannelStatementImport(String, String, String)
 */
class ChannelStatementImportTest extends AbstractReconciliationFlowTest {

    /** 正常三步：RECEIVING → 分段接收（同号同内容忽略）→ MATCHING；未匹配的行等待（没有平台流水）。 */
    @Test
    void test_importAccepted() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        StatementImportBuilder statement = StatementImportBuilder.of(account, "BATCH-OK").payment("TXN-1", "100.00", "0.45")
            .refund("TXN-1R", "30.00", "0");

        var started = start(statement).assertSuccess(201);
        long statementId = started.id("statementId");
        started.assertText("status", ChannelStatementStatus.RECEIVING.getCode()).assertText("revision", "1")
            .assertText("clearingAccountCode", account.getClearingAccountCode()).assertText("netAmount", "69.55");
        append(statementId, statement.lines().subList(0, 1)).assertSuccess(200).assertText("importedLineCount", "1");
        append(statementId, statement.lines()).assertSuccess(200).assertText("importedLineCount", "2");
        var completed = complete(statementId).assertSuccess(200);
        awaitIdle();

        completed.assertText("status", ChannelStatementStatus.MATCHING.getCode());
        assertEquals(0, completed.json().path("rejections").size());
        assertStatementStatus(statementId, ChannelStatementStatus.MATCHING);
        complete(statementId).assertSuccess(200).assertText("status", ChannelStatementStatus.MATCHING.getCode());
        append(statementId, statement.lines()).assertBusinessRejected("不在接收中");
        events.assertNone();
    }

    /** 同号不同内容拒绝；行号超出声明行数拒绝；非上传人不能追加与完成。 */
    @Test
    void test_appendRejected() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        StatementImportBuilder statement = StatementImportBuilder.of(account, "BATCH-APPEND").payment("TXN-1", "10.00", "0");
        long statementId = start(statement).assertSuccess(201).id("statementId");
        append(statementId, statement.lines()).assertSuccess(200);

        AppendChannelStatementLinesRequestBody.Line changed = StatementImportBuilder.of(account, "X")
            .payment("TXN-1", "11.00", "0").lines().getFirst();
        append(statementId, List.of(changed)).assertBusinessRejected("同一行号已接收不同内容");
        AppendChannelStatementLinesRequestBody.Line beyond = StatementImportBuilder.of(account, "X")
            .payment("TXN-2", "1.00", "0").payment("TXN-3", "1.00", "0").lines().get(1);
        append(statementId, List.of(beyond)).assertBusinessRejected("行号超出声明行数");
        post(STATEMENTS + "/" + statementId + "/completion", operator(ReconciliationTestConstants.CHECKER_ID), null)
            .assertBusinessRejected("只有开始导入的上传人可以完成导入");
        AppendChannelStatementLinesRequestBody.Line precise = StatementImportBuilder.of(account, "X")
            .payment("TXN-4", "1.001", "0").lines().getFirst();
        append(statementId, List.of(precise)).assertBusinessRejected("小数位超过币种");
    }

    /** 恒等式、行数、交易号与方向不通过：整单 REJECTED，返回逐行原因，账单头保留汇总原因。 */
    @Test
    void test_completionRejectedWithReasons() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        StatementImportBuilder statement = StatementImportBuilder.of(account, "BATCH-BAD")
            .payment("TXN-1", "100.00", "0")
            .line(ChannelLineType.PAYMENT, SettlementDirection.CREDIT, "5.00", "0", null, null)
            .line(ChannelLineType.REFUND, SettlementDirection.CREDIT, "1.00", "0",
                ReconciliationTestConstants.REFUND_REFERENCE_TYPE, "TXN-R")
            .totals("200.00", "0", "200.00", CashDirection.CREDIT).declaredLineCount(4);
        long statementId = start(statement).assertSuccess(201).id("statementId");
        append(statementId, statement.lines()).assertSuccess(200);

        var completed = complete(statementId).assertSuccess(200);

        completed.assertText("status", ChannelStatementStatus.REJECTED.getCode());
        JsonNode rejections = completed.json().path("rejections");
        assertTrue(completed.text("rejectReason").startsWith("校验不通过"));
        assertTrue(rejections.get(0).path("lineNo").isNull() || rejections.get(0).path("lineNo").isMissingNode());
        assertTrue(rejections.get(0).path("reason").asString().contains("与声明行数 4 不一致"));
        assertEquals(List.of(2, 3), List.of(rejections.get(rejections.size() - 2).path("lineNo").asInt(),
            rejections.get(rejections.size() - 1).path("lineNo").asInt()));
        assertStatementStatus(statementId, ChannelStatementStatus.REJECTED);
        ChannelStatement rejected = channelStatements.findById(statementId);
        assertEquals(completed.text("rejectReason"), rejected.getRejectReason());
        assertEquals(0, complete(statementId).assertSuccess(200).json().path("rejections").size());
        events.assertNone();
    }

    /** 切片 1 不支持净额为借方的账单（决定 I-04）。 */
    @Test
    void test_debitNetRejected() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        StatementImportBuilder statement = StatementImportBuilder.of(account, "BATCH-DEBIT").refund("TXN-R", "30.00", "0");
        long statementId = start(statement).assertSuccess(201).id("statementId");
        append(statementId, statement.lines()).assertSuccess(200);

        var completed = complete(statementId).assertSuccess(200);

        completed.assertText("status", ChannelStatementStatus.REJECTED.getCode());
        assertTrue(completed.text("rejectReason").contains("净额为借方"));
    }

    /** 账单币种必须等于清算账户币种；清算账户不存在拒绝。 */
    @Test
    void test_currencyMustMatch() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());

        start(StatementImportBuilder.of(account, "BATCH-AED").payment("TXN-1", "1.00", "0").currency("AED"))
            .assertBusinessRejected("账单币种必须等于清算账户币种");
        ClearingAccount missing = ClearingAccountBuilder.template().build();
        start(StatementImportBuilder.of(missing, "BATCH-X").payment("TXN-1", "1.00", "0"))
            .assertBusinessRejected("清算账户不存在或已停用");
    }

    /** 更正重传：有未作废版本须先作废；作废后 revision 递增；已对平的批次一律拒绝。 */
    @Test
    void test_revisionRules() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        StatementImportBuilder statement = StatementImportBuilder.of(account, "BATCH-REV").payment("TXN-1", "1.00", "0");
        long first = start(statement).assertSuccess(201).id("statementId");

        start(statement).assertBusinessRejected("须先作废");
        voidStatement(first).assertSuccess(200).assertText("status", ChannelStatementStatus.VOIDED.getCode());
        start(statement).assertSuccess(201).assertText("revision", "2");

        ChannelStatement reconciled = ChannelStatementBuilder.matching(account, "BATCH-DONE", "1.00", "0", "1.00",
            CashDirection.CREDIT, 0).with(item -> item.setStatus(ChannelStatementStatus.RECONCILED)).build();
        data.channelStatement(reconciled, List.of());
        var rejected = start(StatementImportBuilder.of(account, "BATCH-DONE").payment("TXN-9", "1.00", "0"));
        assertAll(() -> rejected.assertBusinessRejected("已对平，不能更正重传"),
            () -> assertEquals(1, channelStatements.listByBatch(account.getId(), "BATCH-DONE").size()));
    }
}
