package com.paytech.reconciliation.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.api.enums.RematchTargetType;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.event.ChannelSettlementConfirmedPayload;
import com.paytech.reconciliation.api.event.ReconAdjustmentApprovedPayload;
import com.paytech.reconciliation.api.exception.request.OpenReconExceptionRequestBody;
import com.paytech.reconciliation.api.exception.request.ReviewReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.request.SubmitReconResolutionRequestBody;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.test.base.AbstractBankReconciliationTest;
import com.paytech.reconciliation.test.builder.BankStatementImportBuilder;
import com.paytech.reconciliation.test.builder.PlatformTxnBuilder;
import com.paytech.reconciliation.test.checker.ReconciliationResponseChecker;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

import tools.jackson.databind.JsonNode;

/**
 * 人工开异常、提交与复核处置（AC-11、AC-14，technique D-02、§2.3 处置应用规则，设计 F3）
 * <li>差异金额由来源对象算出；处置方式按异常类型、归类码按来源对象限定，归类金额必须等于差异；提交人不能复核，摘要必须一致。</li>
 * <li>批准在同一事务应用：平台流水 / 银行流水 / 预期银行流水的 CLASSIFY 各发一条 ReconAdjustmentApproved；银行层 RE_MATCH 建立带处置 ID 的
 * 已确认匹配。</li>
 *
 * @see com.paytech.reconciliation.api.exception.ReconExceptionApi#openReconException(String, String, OpenReconExceptionRequestBody)
 * @see com.paytech.reconciliation.api.exception.ReconExceptionApi#submitReconResolution(String, String, String, SubmitReconResolutionRequestBody)
 * @see com.paytech.reconciliation.api.exception.ReconExceptionApi#listReconExceptions(String, String, String, String, String, String, Integer, Integer)
 * @see com.paytech.reconciliation.api.exception.ReconExceptionApi#getReconException(String)
 * @see com.paytech.reconciliation.api.exception.ReconResolutionApi#reviewReconResolution(String, String, String, ReviewReconResolutionRequestBody)
 */
class ReconResolutionApiTest extends AbstractBankReconciliationTest {

    /** 三类来源的差异金额与主体；同类型未结异常与无未完成金额被拒；列表默认只查未结，详情按来源列出可用处置与归类码。 */
    @Test
    void test_openAndQuery() {
        CashAccount cash = activeCashAccount();
        ClearingAccount account = clearingAccountFor(cash);
        PlatformTxn refund = data.platformTxn(PlatformTxnBuilder.refund(account, "RF-1", "30.00").build());
        ExpectedBankMovement movement = reconciledMovement(account, cash, "67.55");
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-O1")
            .line("O-1", SettlementDirection.DEBIT, "5.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();

        ReconciliationResponseChecker txnException = openException(ReconExceptionSourceType.PLATFORM_TXN, refund.getId())
            .assertSuccess(201).assertText("exceptionType", ReconExceptionType.PLATFORM_ITEM_UNSETTLED.getCode())
            .assertText("differenceAmount", "-30.00").assertText("clearingAccountId", account.getId().toString())
            .assertText("openedBy", String.valueOf(ReconciliationTestConstants.MAKER_ID)).assertText("ageDays", "0");
        openException(ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT, movement.getId()).assertSuccess(201)
            .assertText("exceptionType", ReconExceptionType.BANK_AMOUNT_MISMATCH.getCode())
            .assertText("differenceAmount", "67.55").assertText("cashAccountId", cash.getId().toString())
            .assertText("reconLayer", ReconLayer.BANK.getCode());
        openException(ReconExceptionSourceType.BANK_STATEMENT_LINE, line.getId()).assertSuccess(201)
            .assertText("differenceAmount", "-5.00")
            .assertText("entityType", ReconEntityType.OPERATING_ENTITY.getCode());
        openException(ReconExceptionSourceType.PLATFORM_TXN, refund.getId()).assertBusinessRejected("已有未结的同类型异常");

        assertEquals(3, get(EXCEPTIONS).assertSuccess(200).assertPage(1, 10, 3).size());
        assertEquals(2, get(EXCEPTIONS + "?reconLayer=" + ReconLayer.BANK.getCode()).assertSuccess(200)
            .assertPage(1, 10, 2).size());
        assertEquals(0, get(EXCEPTIONS + "?status=" + ReconExceptionStatus.RESOLVED.getCode()).assertSuccess(200)
            .assertPage(1, 10, 0).size());
        JsonNode summary = get("/reconciliation/v1/summary?currency=" + ReconciliationTestConstants.CURRENCY)
            .assertSuccess(200).json().path("openExceptionsByAge");
        assertAll(() -> assertEquals("D0_3", summary.get(0).path("bucket").asString()),
            () -> assertEquals(3, summary.get(0).path("count").asInt()),
            () -> assertEquals(0, summary.get(0).path("blockingCount").asInt()),
            () -> assertEquals(0, summary.get(3).path("count").asInt()));
        JsonNode detail = get(EXCEPTIONS + "/" + txnException.id("exceptionId")).assertSuccess(200).json();
        assertAll(() -> assertEquals("RF-1", detail.path("source").path("reference").asString()),
            () -> assertEquals(List.of(ResolutionType.ACCEPT_TIMING_DIFFERENCE.getCode(), ResolutionType.CLASSIFY.getCode()),
                texts(detail.path("availableResolutionTypes"))),
            () -> assertEquals(List.of(ReconClassification.RECON_DIFFERENCE.getCode()),
                texts(detail.path("availableClassificationCodes"))));
    }

    /** 平台流水归类：结清 SETTLED，发一条 ReconAdjustmentApproved（带符号金额、清算账户、Maker / Checker 与批准摘要）。 */
    @Test
    void test_classifyPlatformTxn() {
        ClearingAccount account = clearingAccountFor(activeCashAccount());
        PlatformTxn refund = data.platformTxn(PlatformTxnBuilder.refund(account, "RF-2", "30.00").build());
        long exceptionId = openException(ReconExceptionSourceType.PLATFORM_TXN, refund.getId()).id("exceptionId");

        ReconciliationResponseChecker submitted = submit(exceptionId, classify(ReconClassification.RECON_DIFFERENCE,
            "-30.00")).assertSuccess(201).assertText("status", ResolutionStatus.PENDING_CHECK.getCode())
            .assertText("resolutionNo", "1");
        assertExceptionStatus(exceptionId, ReconExceptionStatus.UNDER_REVIEW);
        review(submitted.id("resolutionId"), CheckerDecision.APPROVED, submitted.text("contentHash")).assertSuccess(200)
            .assertText("status", ResolutionStatus.APPLIED.getCode());
        awaitIdle();

        assertAll(() -> assertEquals(PlatformTxnStatus.SETTLED, platformTxns.findById(refund.getId()).getStatus()),
            () -> assertExceptionStatus(exceptionId, ReconExceptionStatus.RESOLVED));
        ReconAdjustmentApprovedPayload payload = events.payload(events.await(1).getFirst(),
            ReconAdjustmentApprovedPayload.class);
        assertAll(() -> assertEquals(submitted.id("resolutionId"), payload.getResolutionId()),
            () -> assertEquals(ReconExceptionSourceType.PLATFORM_TXN.getCode(), payload.getSourceType()),
            () -> assertEquals(ReconClassification.RECON_DIFFERENCE.getCode(), payload.getClassificationCode()),
            () -> assertEquals(0, new BigDecimal("-30.00").compareTo(payload.getAmount())),
            () -> assertEquals(account.getId(), payload.getClearingAccountId()), () -> assertNull(payload.getCashAccountId()),
            () -> assertEquals(ReconciliationTestConstants.MAKER_ID, payload.getMakerId()),
            () -> assertEquals(ReconciliationTestConstants.CHECKER_ID, payload.getCheckerId()),
            () -> assertEquals(submitted.text("contentHash"), payload.getApprovedContentHash()),
            () -> assertEquals(ReconciliationTestConstants.ENTITY_NO, payload.getEntityId()));
    }

    /** 银行流水归类：流水 MATCHED 并发一条 ReconAdjustmentApproved（银行账户主体）。 */
    @Test
    void test_classifyBankLine() {
        CashAccount cash = activeCashAccount();
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-K1")
            .line("K-1", SettlementDirection.DEBIT, "5.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();
        long exceptionId = openException(ReconExceptionSourceType.BANK_STATEMENT_LINE, line.getId()).id("exceptionId");

        resolve(exceptionId, classify(ReconClassification.BANK_FEE, "-5.00"));

        assertEquals(LineMatchStatus.MATCHED, bankStatementLines.findById(line.getId()).getMatchStatus());
        ReconAdjustmentApprovedPayload payload = events.payload(events.await(1).getFirst(),
            ReconAdjustmentApprovedPayload.class);
        assertAll(() -> assertEquals(cash.getId(), payload.getCashAccountId()), () -> assertNull(payload.getClearingAccountId()),
            () -> assertEquals(ReconEntityType.OPERATING_ENTITY.getCode(), payload.getEntityType()),
            () -> assertEquals(ReconClassification.BANK_FEE.getCode(), payload.getClassificationCode()));
    }

    /** 银行层 RE_MATCH：建立带处置 ID 的 CONFIRMED 匹配，发到账事件；异常 RESOLVED。 */
    @Test
    void test_bankRematch() {
        CashAccount cash = activeCashAccount();
        ExpectedBankMovement movement = reconciledMovement(clearingAccountFor(cash), cash, "50.00");
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-RM1")
            .line("RM-1", SettlementDirection.CREDIT, "40.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();
        long exceptionId = openException(ReconExceptionSourceType.BANK_STATEMENT_LINE, line.getId()).id("exceptionId");

        SubmitReconResolutionRequestBody overAllocated = rematch(movement, "45.00");
        submit(exceptionId, overAllocated).assertBusinessRejected("超出银行流水金额");
        long resolutionId = resolve(exceptionId, rematch(movement, "40.00"));

        BankMatch match = bankMatches.listByLine(line.getId(), BankMatchStatus.CONFIRMED).getFirst();
        assertAll(() -> assertEquals(resolutionId, match.getResolutionId()),
            () -> assertEquals(LineMatchStatus.MATCHED, bankStatementLines.findById(line.getId()).getMatchStatus()),
            () -> assertEquals(ExpectedMovementStatus.PARTIALLY_CONFIRMED,
                expectedMovements.findById(movement.getId()).getStatus()),
            () -> assertExceptionStatus(exceptionId, ReconExceptionStatus.RESOLVED));
        assertEquals(ChannelSettlementConfirmedPayload.EVENT_TYPE, events.await(1).getFirst().eventType());
    }

    /** ACCEPT_TIMING_DIFFERENCE 只结案：连续性异常 RESOLVED，不发事件。 */
    @Test
    void test_acceptGap() {
        CashAccount cash = activeCashAccount();
        LocalDate today = BankStatementImportBuilder.today();
        importedBank(BankStatementImportBuilder.of(cash, "BS-A1").period(today.minusDays(5), today.minusDays(4)));
        long gapId = importBank(BankStatementImportBuilder.of(cash, "BS-A2").period(today, today).opening("1.00"))
            .id("gapExceptionId");

        resolve(gapId, accept());

        assertExceptionStatus(gapId, ReconExceptionStatus.RESOLVED);
        events.assertNone();
    }

    /** 拒绝：类型 / 归类码 / 金额 / 字段组合不合法；同人复核、摘要不符；拒绝后回 OPEN 可重新提交（序号 2）；同一复核人重复同一决定返回当前结果。 */
    @Test
    void test_validationAndMakerChecker() {
        ClearingAccount account = clearingAccountFor(activeCashAccount());
        PlatformTxn payment = data.platformTxn(PlatformTxnBuilder.payment(account, "PY-1", "100.00", "0").build());
        long exceptionId = openException(ReconExceptionSourceType.PLATFORM_TXN, payment.getId()).id("exceptionId");

        submit(exceptionId, classify(ReconClassification.BANK_FEE, "100.00")).assertBusinessRejected("不能使用归类码");
        submit(exceptionId, classify(ReconClassification.RECON_DIFFERENCE, "99.00"))
            .assertBusinessRejected("归类金额必须等于异常差异金额");
        SubmitReconResolutionRequestBody rematch = rematch(null, null);
        rematch.setRematchTargetType(RematchTargetType.PLATFORM_TXN.getCode());
        rematch.setRematchTargetId(payment.getId().toString());
        submit(exceptionId, rematch).assertBusinessRejected("不能使用处置方式");
        SubmitReconResolutionRequestBody missing = classify(ReconClassification.RECON_DIFFERENCE, "100.00");
        missing.setAmount(null);
        submit(exceptionId, missing).assertBusinessRejected("CLASSIFY 时填写");
        assertExceptionStatus(exceptionId, ReconExceptionStatus.OPEN);

        ReconciliationResponseChecker first = submit(exceptionId, accept()).assertSuccess(201);
        long firstId = first.id("resolutionId");
        submit(exceptionId, accept()).assertBusinessRejected("不是 OPEN");
        ReviewReconResolutionRequestBody selfReview = new ReviewReconResolutionRequestBody();
        selfReview.setDecision(CheckerDecision.APPROVED.getCode());
        selfReview.setApprovedContentHash(first.text("contentHash"));
        post(RESOLUTIONS + "/" + firstId + "/approval", operator(ReconciliationTestConstants.MAKER_ID), selfReview)
            .assertBusinessRejected("提交人不能复核");
        review(firstId, CheckerDecision.APPROVED, "e".repeat(64)).assertBusinessRejected("内容摘要与提交不一致");

        review(firstId, CheckerDecision.REJECTED, first.text("contentHash")).assertSuccess(200)
            .assertText("status", ResolutionStatus.REJECTED.getCode());
        review(firstId, CheckerDecision.REJECTED, first.text("contentHash")).assertSuccess(200)
            .assertText("status", ResolutionStatus.REJECTED.getCode());
        review(firstId, CheckerDecision.APPROVED, first.text("contentHash")).assertBusinessRejected("不是待复核状态");
        assertExceptionStatus(exceptionId, ReconExceptionStatus.OPEN);
        submit(exceptionId, accept()).assertSuccess(201).assertText("resolutionNo", "2");
        assertEquals(2, get(EXCEPTIONS + "/" + exceptionId).assertSuccess(200).json().path("resolutions").size());
        events.assertNone();
    }

    /** 批准时应用条件已变化（预期流水剩余变了）：整笔回滚，处置仍 PENDING_CHECK、异常仍 UNDER_REVIEW。 */
    @Test
    void test_staleClassificationRejected() {
        CashAccount cash = activeCashAccount();
        ExpectedBankMovement movement = reconciledMovement(clearingAccountFor(cash), cash, "30.00");
        long exceptionId = openException(ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT, movement.getId())
            .id("exceptionId");
        ReconciliationResponseChecker submitted = submit(exceptionId, classify(ReconClassification.BANK_FEE, "30.00"))
            .assertSuccess(201);
        long statementId = importedBank(BankStatementImportBuilder.of(cash, "BS-S1")
            .line("S-1", SettlementDirection.CREDIT, "10.00"));
        BankStatementLine line = bankStatementLines.listByStatement(statementId).getFirst();
        postAsMaker(BANK_MATCHES, BankMatchApiTest.matches(line, BankMatchApiTest.alloc(movement, "10.00")))
            .assertSuccess(201);
        awaitIdle();
        events.await(1);

        review(submitted.id("resolutionId"), CheckerDecision.APPROVED, submitted.text("contentHash"))
            .assertBusinessRejected("剩余金额已变化");
        assertAll(() -> assertExceptionStatus(exceptionId, ReconExceptionStatus.UNDER_REVIEW),
            () -> assertEquals(ResolutionStatus.PENDING_CHECK, reconResolutions.findById(submitted.id("resolutionId"))
                .getStatus()));
        events.assertNone();
    }

    /** 银行层 RE_MATCH 请求。 */
    static SubmitReconResolutionRequestBody rematch(ExpectedBankMovement movement, String amount) {
        SubmitReconResolutionRequestBody body = new SubmitReconResolutionRequestBody();
        body.setResolutionType(ResolutionType.RE_MATCH.getCode());
        body.setRematchTargetType(RematchTargetType.EXPECTED_BANK_MOVEMENT.getCode());
        body.setRematchTargetId(movement == null ? null : movement.getId().toString());
        body.setRematchAmount(amount);
        body.setNote("rematch");
        return body;
    }

    /** JSON 字符串数组。 */
    static List<String> texts(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
