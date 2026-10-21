package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.reconciliation.api.bank.request.CreateBankMatchesRequestBody;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.test.base.AbstractBankReconciliationTest;
import com.paytech.reconciliation.test.builder.BankStatementImportBuilder;
import com.paytech.reconciliation.test.builder.ChannelStatementBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.builder.PlatformTxnBuilder;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import tools.jackson.databind.JsonNode;

/**
 * 对账控制台详情页与渠道流水页的只读接口（需求 20261021 §2.1，AC-05、AC-06）
 * <li>跨账单渠道账单行：筛选、未指定账单状态时排除 VOIDED / REJECTED / RECEIVING、显式状态覆盖默认、交易日期降序（无日期在最后）
 * 同日主键降序、分页与每页上限 100、外部交易号精确。</li>
 * <li>银行对账单头与银行流水详情：全部状态的银行匹配带预期银行流水、流水上的未结异常；不存在时 404 合同错误码。</li>
 *
 * @see com.paytech.reconciliation.api.statement.ChannelStatementLineApi
 * @see com.paytech.reconciliation.api.bank.BankStatementApi#getBankStatement(String)
 * @see com.paytech.reconciliation.api.bank.BankStatementApi#getBankStatementLine(String)
 */
class ReconciliationDetailQueryTest extends AbstractBankReconciliationTest {
    private static final String CHANNEL_LINES = "/reconciliation/v1/channel-statement-lines";
    private static final String BANK_LINES = "/reconciliation/v1/bank-statement-lines";
    private static final LocalDate DAY_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 10, 2);
    private static final LocalDate DAY_3 = LocalDate.of(2026, 10, 3);

    /** 未指定账单状态：只含 MATCHING / RECONCILED / CASH_CONFIRMED 账单的行；交易日期降序、无日期在最后、同日主键降序；字段带账单与清算账户。 */
    @Test
    void test_channelLinesDefaultExclusionAndOrdering() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        PlatformTxn txn = data.platformTxn(PlatformTxnBuilder.payment(account, "CL-1", "100.00", "0.45").build());
        ChannelStatement matching = statement(account, "B-MATCHING", ChannelStatementStatus.MATCHING);
        ChannelStatementLine matched = line(matching, 1, ChannelLineType.PAYMENT, "CL-1", DAY_2);
        matched.setMatchStatus(LineMatchStatus.MATCHED);
        matched.setPlatformTxnId(txn.getId());
        matched.setMatchMethod(MatchMethod.AUTO);
        matched.setActualFee(new BigDecimal("0.45"));
        ChannelStatementLine sameDay = line(matching, 2, ChannelLineType.REFUND, "CL-2", DAY_2);
        ChannelStatementLine undated = line(matching, 3, ChannelLineType.ADJUSTMENT, null, null);
        data.channelStatement(matching, List.of(matched, sameDay, undated));
        ChannelStatement reconciled = statement(account, "B-RECONCILED", ChannelStatementStatus.RECONCILED);
        ChannelStatementLine latest = line(reconciled, 1, ChannelLineType.PAYMENT, "CL-3", DAY_3);
        data.channelStatement(reconciled, List.of(latest));
        ChannelStatement cashConfirmed = statement(account, "B-CASH", ChannelStatementStatus.CASH_CONFIRMED);
        ChannelStatementLine earliest = line(cashConfirmed, 1, ChannelLineType.FEE, "CL-4", DAY_1);
        data.channelStatement(cashConfirmed, List.of(earliest));
        for (ChannelStatementStatus excluded : List.of(ChannelStatementStatus.VOIDED, ChannelStatementStatus.REJECTED,
            ChannelStatementStatus.RECEIVING)) {
            ChannelStatement hidden = statement(account, "B-" + excluded.getCode(), excluded);
            data.channelStatement(hidden, List.of(line(hidden, 1, ChannelLineType.PAYMENT, "CL-1", DAY_3)));
        }

        List<JsonNode> items = get(CHANNEL_LINES + "?clearingAccountId=" + account.getId()).assertSuccess(200)
            .assertPage(1, 10, 5);
        long sameDayFirst = Math.max(matched.getId(), sameDay.getId());
        assertEquals(List.of(latest.getId(), sameDayFirst, Math.min(matched.getId(), sameDay.getId()), earliest.getId(),
            undated.getId()), ids(items));
        JsonNode row = items.stream().filter(item -> item.path("lineId").asLong() == matched.getId()).findFirst()
            .orElseThrow();
        assertAll(() -> assertEquals(matching.getId().toString(), row.path("statementId").asString()),
            () -> assertEquals("B-MATCHING", row.path("channelBatchRef").asString()),
            () -> assertEquals(1, row.path("revision").asInt()),
            () -> assertEquals(ChannelStatementStatus.MATCHING.getCode(), row.path("statementStatus").asString()),
            () -> assertEquals(matching.getSettlementDate().toString(), row.path("settlementDate").asString()),
            () -> assertEquals(account.getId().toString(), row.path("clearingAccountId").asString()),
            () -> assertEquals(account.getClearingAccountCode(), row.path("clearingAccountCode").asString()),
            () -> assertEquals(ReconciliationTestConstants.CURRENCY, row.path("currency").asString()),
            () -> assertEquals("100.00", row.path("amount").asString()),
            () -> assertEquals(ReconciliationTestConstants.PAY_REFERENCE_TYPE, row.path("referenceType").asString()),
            () -> assertEquals("CL-1", row.path("externalTransactionId").asString()),
            () -> assertEquals(DAY_2.toString(), row.path("transactionDate").asString()),
            () -> assertEquals(txn.getId().toString(), row.path("platformTxnId").asString()),
            () -> assertEquals(MatchMethod.AUTO.getCode(), row.path("matchMethod").asString()),
            () -> assertEquals("0.45", row.path("actualFee").asString()),
            () -> assertEquals(LineMatchStatus.MATCHED.getCode(), row.path("matchStatus").asString()));
        assertNull(items.getLast().path("transactionDate").asString(null));
    }

    /** 显式账单状态只按该状态过滤（含默认排除的状态）；匹配状态、行类型、交易日期区间、清算账户、外部交易号精确筛选。 */
    @Test
    void test_channelLinesFilters() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        ClearingAccount other = data.clearingAccount(ClearingAccountBuilder.template().build());
        ChannelStatement matching = statement(account, "F-MATCHING", ChannelStatementStatus.MATCHING);
        ChannelStatementLine payment = line(matching, 1, ChannelLineType.PAYMENT, "EXT-1", DAY_1);
        ChannelStatementLine refund = line(matching, 2, ChannelLineType.REFUND, "EXT-10", DAY_2);
        refund.setMatchStatus(LineMatchStatus.EXCEPTION);
        data.channelStatement(matching, List.of(payment, refund));
        ChannelStatement voided = statement(account, "F-VOIDED", ChannelStatementStatus.VOIDED);
        ChannelStatementLine voidedLine = line(voided, 1, ChannelLineType.PAYMENT, "EXT-1", DAY_3);
        data.channelStatement(voided, List.of(voidedLine));
        ChannelStatement foreign = statement(other, "F-OTHER", ChannelStatementStatus.MATCHING);
        ChannelStatementLine foreignLine = line(foreign, 1, ChannelLineType.PAYMENT, "EXT-1", DAY_3);
        data.channelStatement(foreign, List.of(foreignLine));

        assertEquals(List.of(voidedLine.getId()), ids(get(CHANNEL_LINES + "?clearingAccountId=" + account.getId()
            + "&statementStatus=" + ChannelStatementStatus.VOIDED.getCode()).assertSuccess(200).assertPage(1, 10, 1)));
        assertEquals(List.of(refund.getId()), ids(get(CHANNEL_LINES + "?clearingAccountId=" + account.getId()
            + "&matchStatus=" + LineMatchStatus.EXCEPTION.getCode()).assertSuccess(200).assertPage(1, 10, 1)));
        assertEquals(List.of(refund.getId()), ids(get(CHANNEL_LINES + "?clearingAccountId=" + account.getId()
            + "&lineType=" + ChannelLineType.REFUND.getCode()).assertSuccess(200).assertPage(1, 10, 1)));
        assertEquals(List.of(payment.getId()), ids(get(CHANNEL_LINES + "?clearingAccountId=" + account.getId()
            + "&transactionDateFrom=" + DAY_1 + "&transactionDateTo=" + DAY_1).assertSuccess(200).assertPage(1, 10, 1)));
        // 外部交易号精确：EXT-1 不命中 EXT-10；跨清算账户，默认排除作废账单的行
        assertEquals(List.of(foreignLine.getId(), payment.getId()), ids(get(CHANNEL_LINES + "?externalTransactionId=EXT-1")
            .assertSuccess(200).assertPage(1, 10, 2)));
        assertEquals(List.of(payment.getId()), ids(get(CHANNEL_LINES + "?clearingAccountId=" + account.getId()
            + "&referenceType=" + ReconciliationTestConstants.PAY_REFERENCE_TYPE + "&externalTransactionId=EXT-1")
            .assertSuccess(200).assertPage(1, 10, 1)));
        assertEquals(List.of(), ids(get(CHANNEL_LINES + "?referenceType="
            + ReconciliationTestConstants.REFUND_REFERENCE_TYPE + "&externalTransactionId=EXT-1").assertSuccess(200)
            .assertPage(1, 10, 0)));
    }

    /** 分页：第二页与总数；每页 200 与非法参数为 400 INVALID_REQUEST。 */
    @Test
    void test_channelLinesPagingAndInvalidParameters() {
        ClearingAccount account = data.clearingAccount(ClearingAccountBuilder.template().build());
        ChannelStatement statement = statement(account, "P-1", ChannelStatementStatus.MATCHING);
        List<ChannelStatementLine> lines = new ArrayList<>();
        for (int lineNo = 1; lineNo <= 12; lineNo++) {
            lines.add(line(statement, lineNo, ChannelLineType.PAYMENT, "PG-" + lineNo, DAY_1));
        }
        data.channelStatement(statement, lines);

        List<JsonNode> second = get(CHANNEL_LINES + "?clearingAccountId=" + account.getId() + "&page=2&size=10")
            .assertSuccess(200).assertPage(2, 10, 12);
        assertEquals(List.of(lines.get(1).getId(), lines.get(0).getId()), ids(second));
        get(CHANNEL_LINES + "?size=100").assertSuccess(200).assertPage(1, 100, 12);
        get(CHANNEL_LINES + "?size=200").assertInvalidRequest();
        get(CHANNEL_LINES + "?statementStatus=UNKNOWN").assertInvalidRequest();
        get(CHANNEL_LINES + "?transactionDateFrom=2026-13-40").assertInvalidRequest();
        get(CHANNEL_LINES + "?referenceType=pay").assertInvalidRequest();
    }

    /** 对账单头：字段同列表项；不存在为 404 BANK_STATEMENT_NOT_FOUND。 */
    @Test
    void test_bankStatementHeader() {
        CashAccount cash = activeCashAccount();
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-H1")
            .line("H-1", SettlementDirection.CREDIT, "12.50"));

        get(BANK_STATEMENTS + "/" + statementId).assertSuccess(200)
            .assertText("bankStatementId", String.valueOf(statementId)).assertText("cashAccountId", cash.getId().toString())
            .assertText("statementRef", "BS-H1").assertText("status", BankStatementStatus.IMPORTED.getCode())
            .assertText("currency", ReconciliationTestConstants.CURRENCY);
        get(BANK_STATEMENTS + "/" + ReconciliationTestConstants.nextId())
            .assertProblem(404, ReconciliationErrorCode.BANK_STATEMENT_NOT_FOUND.getCode());
        get(BANK_STATEMENTS + "/abc").assertInvalidRequest();
    }

    /** 银行流水详情：REJECTED、PROPOSED、CONFIRMED 三种匹配各带预期银行流水，未结异常；不存在为 404 BANK_STATEMENT_LINE_NOT_FOUND。 */
    @Test
    void test_bankStatementLineDetail() {
        CashAccount cash = activeCashAccount();
        ClearingAccount account = clearingAccountFor(cash);
        ExpectedBankMovement first = reconciledMovement(account, cash, "30.00");
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-D1")
            .line("D-1", SettlementDirection.CREDIT, "30.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();
        BankMatch rejected = bankMatches.listByLine(line.getId(), null).getFirst();
        postAsMaker(BANK_MATCHES + "/" + rejected.getId() + "/rejection", null).assertSuccess(200);
        ExpectedBankMovement second = reconciledMovement(account, cash, "30.00");
        importedBank(BankStatementImportBuilder.of(cash, "BS-D2"));
        BankMatch proposed = bankMatches.listByLine(line.getId(), BankMatchStatus.PROPOSED).getFirst();
        ExpectedBankMovement partial = reconciledMovement(account, cash, "10.00");
        postAsMaker(BANK_MATCHES, manual(line, partial, "10.00")).assertSuccess(201);
        awaitIdle();
        long exceptionId = openException(ReconExceptionSourceType.BANK_STATEMENT_LINE, line.getId()).assertSuccess(201)
            .id("exceptionId");

        JsonNode detail = get(BANK_LINES + "/" + line.getId()).assertSuccess(200).json();
        assertAll(() -> assertEquals(line.getId().toString(), detail.path("line").path("bankStatementLineId").asString()),
            () -> assertEquals("D-1", detail.path("line").path("bankTransactionRef").asString()),
            () -> assertEquals(String.valueOf(statementId), detail.path("statement").path("bankStatementId").asString()),
            () -> assertEquals("BS-D1", detail.path("statement").path("statementRef").asString()));
        List<JsonNode> matches = new ArrayList<>();
        detail.path("matches").forEach(matches::add);
        assertEquals(3, matches.size());
        assertMatch(matches.get(0), rejected.getId(), BankMatchStatus.REJECTED, first, "30.00");
        assertMatch(matches.get(1), proposed.getId(), BankMatchStatus.PROPOSED, second, "30.00");
        assertMatch(matches.get(2), null, BankMatchStatus.CONFIRMED, partial, "10.00");
        assertEquals("10.00", matches.get(2).path("expectedMovement").path("confirmedAmount").asString());
        assertEquals(1, detail.path("openExceptions").size());
        assertAll(() -> assertEquals(String.valueOf(exceptionId),
                detail.path("openExceptions").get(0).path("exceptionId").asString()),
            () -> assertEquals(ReconExceptionStatus.OPEN.getCode(),
                detail.path("openExceptions").get(0).path("status").asString()));

        get(BANK_LINES + "/" + ReconciliationTestConstants.nextId())
            .assertProblem(404, ReconciliationErrorCode.BANK_STATEMENT_LINE_NOT_FOUND.getCode());
    }

    /** 没有匹配与异常的流水返回空列表。 */
    @Test
    void test_bankStatementLineDetailEmpty() {
        CashAccount cash = activeCashAccount();
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-E1")
            .line("E-1", SettlementDirection.DEBIT, "5.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();
        JsonNode detail = get(BANK_LINES + "/" + line.getId()).assertSuccess(200).json();
        assertAll(() -> assertEquals(0, detail.path("matches").size()),
            () -> assertEquals(0, detail.path("openExceptions").size()),
            () -> assertEquals(LineMatchStatus.UNMATCHED.getCode(), detail.path("line").path("matchStatus").asString()));
    }

    /** 一张给定状态的渠道账单头（未插入）。 */
    private static ChannelStatement statement(ClearingAccount account, String batchRef, ChannelStatementStatus status) {
        return ChannelStatementBuilder.matching(account, batchRef, "0", "0", "0", CashDirection.CREDIT, 0)
            .with(item -> item.setStatus(status)).build();
    }

    /** 一条未匹配账单行；交易号为空时不带交易号类型。 */
    private static ChannelStatementLine line(ChannelStatement statement, int lineNo, ChannelLineType type,
        String externalTransactionId, LocalDate transactionDate) {
        String referenceType = externalTransactionId == null ? null
            : type == ChannelLineType.REFUND ? ReconciliationTestConstants.REFUND_REFERENCE_TYPE
            : ReconciliationTestConstants.PAY_REFERENCE_TYPE;
        ChannelStatementLine line = ChannelStatementBuilder.line(statement, lineNo, type, SettlementDirection.CREDIT,
            "100.00", "0", referenceType, externalTransactionId);
        line.setTransactionDate(transactionDate);
        return line;
    }

    /** 分页 items 的行主键。 */
    private static List<Long> ids(List<JsonNode> items) {
        return items.stream().map(item -> item.path("lineId").asLong()).toList();
    }

    /** 一条人工匹配分配。 */
    private static CreateBankMatchesRequestBody manual(BankStatementLine line, ExpectedBankMovement movement,
        String amount) {
        CreateBankMatchesRequestBody.Allocation allocation = new CreateBankMatchesRequestBody.Allocation();
        allocation.setExpectedMovementId(movement.getId().toString());
        allocation.setAmount(amount);
        CreateBankMatchesRequestBody body = new CreateBankMatchesRequestBody();
        body.setBankStatementLineId(line.getId().toString());
        body.setAllocations(List.of(allocation));
        return body;
    }

    /** 断言一条匹配及其预期银行流水摘要。 */
    private static void assertMatch(JsonNode item, Long matchId, BankMatchStatus status, ExpectedBankMovement movement,
        String matchedAmount) {
        JsonNode match = item.path("match");
        JsonNode expected = item.path("expectedMovement");
        assertAll(() -> {
                if (matchId != null) {
                    assertEquals(matchId.toString(), match.path("bankMatchId").asString());
                }
            }, () -> assertEquals(status.getCode(), match.path("status").asString()),
            () -> assertEquals(matchedAmount, match.path("matchedAmount").asString()),
            () -> assertEquals(movement.getId().toString(), match.path("expectedMovementId").asString()),
            () -> assertEquals(movement.getId().toString(), expected.path("expectedMovementId").asString()),
            () -> assertEquals(ExpectedMovementType.CHANNEL_SETTLEMENT.getCode(), expected.path("movementType").asString()),
            () -> assertEquals(movement.getSourceId().toString(), expected.path("sourceId").asString()),
            () -> assertEquals(movement.getExpectedAmount().setScale(2).toPlainString(),
                expected.path("expectedAmount").asString()));
    }
}
