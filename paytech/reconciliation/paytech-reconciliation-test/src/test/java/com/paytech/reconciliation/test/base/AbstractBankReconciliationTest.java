package com.paytech.reconciliation.test.base;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import com.paytech.reconciliation.api.bank.request.AppendBankStatementLinesRequestBody;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.exception.request.OpenReconExceptionRequestBody;
import com.paytech.reconciliation.api.exception.request.ReviewReconResolutionRequestBody;
import com.paytech.reconciliation.api.exception.request.SubmitReconResolutionRequestBody;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.service.ExpectedBankMovementService;
import com.paytech.reconciliation.test.builder.BankStatementImportBuilder;
import com.paytech.reconciliation.test.builder.CashAccountBuilder;
import com.paytech.reconciliation.test.builder.ChannelStatementBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.builder.ExpectedBankMovementBuilder;
import com.paytech.reconciliation.test.checker.ReconciliationResponseChecker;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 银行层（银行账户、银行对账单、匹配）与异常处置入口测试的公共调用与读取（不含 @Test）
 * <li>分段导入、匹配、开异常、提交与复核处置都走真实 HTTP 入口；建议任务与事件发布由提交后的真实补偿调度执行，
 * {@link #awaitIdle()} 等待其结束。</li>
 * <li>数据库断言通过生产 Repository 读取。</li>
 */
public abstract class AbstractBankReconciliationTest extends AbstractReconciliationFlowTest {
    /** 银行账户 API 前缀。 */
    protected static final String CASH_ACCOUNTS = "/reconciliation/v1/cash-accounts";

    /** 银行对账单 API 前缀。 */
    protected static final String BANK_STATEMENTS = "/reconciliation/v1/bank-statements";

    /** 银行匹配 API 前缀。 */
    protected static final String BANK_MATCHES = "/reconciliation/v1/bank-matches";

    /** 对账异常 API 前缀。 */
    protected static final String EXCEPTIONS = "/reconciliation/v1/recon-exceptions";

    /** 异常处置 API 前缀。 */
    protected static final String RESOLUTIONS = "/reconciliation/v1/recon-resolutions";

    @Autowired
    protected CashAccountRepository cashAccounts;

    @Autowired
    protected BankMatchRepository bankMatches;

    @Autowired
    protected BankStatementRepository bankStatements;

    @Autowired
    protected BankStatementLineRepository bankStatementLines;

    @Autowired
    protected ExpectedBankMovementService expectedMovementService;

    /** 开始导入银行对账单（以提交人身份）。 */
    protected ReconciliationResponseChecker startBank(BankStatementImportBuilder statement) {
        return postAsMaker(BANK_STATEMENTS, statement.header());
    }

    /** 追加一段银行流水（以提交人身份）。 */
    protected ReconciliationResponseChecker appendBank(long statementId,
        List<AppendBankStatementLinesRequestBody.Line> lines) {
        return postAsMaker(BANK_STATEMENTS + "/" + statementId + "/lines", BankStatementImportBuilder.segment(lines));
    }

    /** 完成导入（以提交人身份）。 */
    protected ReconciliationResponseChecker completeBank(long statementId) {
        return postAsMaker(BANK_STATEMENTS + "/" + statementId + "/completion", null);
    }

    /** 开始、一段追加、完成导入并等待建议任务结束；返回对账单主键与完成结果。 */
    protected ReconciliationResponseChecker importBank(BankStatementImportBuilder statement) {
        long statementId = startBank(statement).assertSuccess(201).id("bankStatementId");
        if (!statement.lines().isEmpty()) {
            appendBank(statementId, statement.lines()).assertSuccess(200);
        }
        ReconciliationResponseChecker completed = completeBank(statementId).assertSuccess(200);
        awaitIdle();
        return completed;
    }

    /** 导入成功（IMPORTED）的对账单主键。 */
    protected long importedBank(BankStatementImportBuilder statement) {
        return importBank(statement).assertText("status", BankStatementStatus.IMPORTED.getCode()).id("bankStatementId");
    }

    /** 插入 ACTIVE 银行账户与以它为到账账户的清算账户。 */
    protected ClearingAccount clearingAccountFor(CashAccount cash) {
        return data.clearingAccount(ClearingAccountBuilder.template().settlementCashAccount(cash.getId()).build());
    }

    /** 插入 ACTIVE 银行账户。 */
    protected CashAccount activeCashAccount() {
        return data.cashAccount(CashAccountBuilder.template().build());
    }

    /** 插入一张已对平（RECONCILED、净额 CREDIT）的渠道账单与其 OPEN 预期银行流水。 */
    protected ExpectedBankMovement reconciledMovement(ClearingAccount account, CashAccount cash, String amount) {
        return reconciledMovement(account, cash, SettlementDirection.CREDIT, amount);
    }

    /** 插入一张已对平（RECONCILED）的渠道账单（净额方向同预期流水）与其 OPEN 预期银行流水。 */
    protected ExpectedBankMovement reconciledMovement(ClearingAccount account, CashAccount cash,
        SettlementDirection direction, String amount) {
        ChannelStatement statement = reconciledStatement(account, direction, amount);
        return data.expectedMovement(ExpectedBankMovementBuilder.channelSettlement(statement, cash, direction, amount)
            .build());
    }

    /** 插入一张已对平（RECONCILED）、没有行的渠道账单。 */
    protected ChannelStatement reconciledStatement(ClearingAccount account, SettlementDirection direction,
        String amount) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ChannelStatement statement = ChannelStatementBuilder.matching(account, "BATCH-" + ReconciliationTestConstants
            .nextId(), amount, "0", amount, direction == SettlementDirection.CREDIT ? CashDirection.CREDIT
            : CashDirection.DEBIT, 0).with(item -> {
                item.setStatus(ChannelStatementStatus.RECONCILED);
                item.setReconciledTime(now);
            }).build();
        return data.channelStatement(statement, List.of());
    }

    /** 人工开异常（以提交人身份）。 */
    protected ReconciliationResponseChecker openException(ReconExceptionSourceType sourceType, long sourceId) {
        OpenReconExceptionRequestBody body = new OpenReconExceptionRequestBody();
        body.setSourceType(sourceType.getCode());
        body.setSourceId(String.valueOf(sourceId));
        return postAsMaker(EXCEPTIONS, body);
    }

    /** CLASSIFY 处置请求。 */
    protected static SubmitReconResolutionRequestBody classify(ReconClassification code, String amount) {
        SubmitReconResolutionRequestBody body = new SubmitReconResolutionRequestBody();
        body.setResolutionType(ResolutionType.CLASSIFY.getCode());
        body.setClassificationCode(code.getCode());
        body.setAmount(amount);
        body.setNote("classify " + code.getCode());
        return body;
    }

    /** ACCEPT_TIMING_DIFFERENCE 处置请求。 */
    protected static SubmitReconResolutionRequestBody accept() {
        SubmitReconResolutionRequestBody body = new SubmitReconResolutionRequestBody();
        body.setResolutionType(ResolutionType.ACCEPT_TIMING_DIFFERENCE.getCode());
        body.setNote("accept timing difference");
        return body;
    }

    /** 以提交人身份提交处置。 */
    protected ReconciliationResponseChecker submit(long exceptionId, SubmitReconResolutionRequestBody body) {
        return postAsMaker(EXCEPTIONS + "/" + exceptionId + "/resolutions", body);
    }

    /** 以复核人身份复核处置，确认给定摘要。 */
    protected ReconciliationResponseChecker review(long resolutionId, CheckerDecision decision, String contentHash) {
        ReviewReconResolutionRequestBody body = new ReviewReconResolutionRequestBody();
        body.setDecision(decision.getCode());
        body.setApprovedContentHash(contentHash);
        return postAsChecker(RESOLUTIONS + "/" + resolutionId + "/approval", body);
    }

    /** 提交并由复核人批准，等待提交后的任务结束；返回处置主键。 */
    protected long resolve(long exceptionId, SubmitReconResolutionRequestBody body) {
        ReconciliationResponseChecker submitted = submit(exceptionId, body).assertSuccess(201);
        long resolutionId = submitted.id("resolutionId");
        review(resolutionId, CheckerDecision.APPROVED, submitted.text("contentHash")).assertSuccess(200);
        awaitIdle();
        return resolutionId;
    }
}
