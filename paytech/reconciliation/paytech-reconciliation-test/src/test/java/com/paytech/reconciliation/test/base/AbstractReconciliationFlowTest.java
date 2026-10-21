package com.paytech.reconciliation.test.base;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.api.statement.request.AppendChannelStatementLinesRequestBody;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.repository.ReconResolutionRepository;
import com.paytech.reconciliation.domainservice.service.ChannelReconciliationResolutionService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.test.builder.CashAccountBuilder;
import com.paytech.reconciliation.test.builder.ClearingAccountBuilder;
import com.paytech.reconciliation.test.builder.StatementImportBuilder;
import com.paytech.reconciliation.test.checker.ReconciliationResponseChecker;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 交易层（清算账户、平台流水、渠道账单、匹配与对平）入口测试的公共调用与读取（不含 @Test）
 * <li>分段导入走真实 HTTP 入口；匹配任务由完成导入提交后的真实补偿调度执行，{@link #awaitIdle()} 等待本地任务全部结束。</li>
 * <li>{@link #classifyLine} 扮演 C 线处置批准：在一个本地事务内插入已应用的 CLASSIFY 处置、异常结案，并调用 B 线提供的
 * {@link ChannelReconciliationResolutionService} 钩子（C 线的处置接口尚未实现）。</li>
 * <li>数据库断言通过生产 Repository 读取。</li>
 */
public abstract class AbstractReconciliationFlowTest extends MockTestBase {
    /** 渠道账单 API 前缀。 */
    protected static final String STATEMENTS = "/reconciliation/v1/channel-statements";

    /** 清算账户 API 前缀。 */
    protected static final String CLEARING_ACCOUNTS = "/reconciliation/v1/clearing-accounts";

    /** 每段追加行数上限。 */
    static final int SEGMENT = 100;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected PlatformTxnRepository platformTxns;

    @Autowired
    protected ReconExceptionService reconExceptionService;

    @Autowired
    protected ReconExceptionRepository reconExceptions;

    @Autowired
    protected ReconResolutionRepository reconResolutions;

    @Autowired
    protected ChannelStatementRepository channelStatements;

    @Autowired
    protected ChannelStatementLineRepository channelStatementLines;

    @Autowired
    protected ExpectedBankMovementRepository expectedMovements;

    @Autowired
    protected ChannelReconciliationResolutionService resolutionService;

    /** 插入 ACTIVE 到账银行账户与指向它的清算账户（容差给定）。 */
    protected ClearingAccount clearingAccountWithCash(String tolerance) {
        CashAccount cash = data.cashAccount(CashAccountBuilder.template().build());
        return data.clearingAccount(ClearingAccountBuilder.template().settlementCashAccount(cash.getId())
            .with(account -> account.setCostToleranceAmount(new BigDecimal(tolerance))).build());
    }

    /** 扮演 billing 发布事实并等待消费提交，返回登记的平台流水。 */
    protected PlatformTxn recorded(BillingFactPayload fact) {
        consumed(fact);
        return platformTxns.findByBillingFactId(fact.getFactId());
    }

    /** 开始导入（以提交人身份）。 */
    protected ReconciliationResponseChecker start(StatementImportBuilder statement) {
        return postAsMaker(STATEMENTS, statement.header());
    }

    /** 追加一段明细（以提交人身份）。 */
    protected ReconciliationResponseChecker append(long statementId, List<AppendChannelStatementLinesRequestBody.Line> lines) {
        return postAsMaker(STATEMENTS + "/" + statementId + "/lines", StatementImportBuilder.segment(lines));
    }

    /** 完成导入（以提交人身份，无 Body）。 */
    protected ReconciliationResponseChecker complete(long statementId) {
        return postAsMaker(STATEMENTS + "/" + statementId + "/completion", null);
    }

    /** 作废（以提交人身份，无 Body）。 */
    protected ReconciliationResponseChecker voidStatement(long statementId) {
        return postAsMaker(STATEMENTS + "/" + statementId + "/void", null);
    }

    /** 带操作人头的任意方法请求（PUT 配置、DELETE MID）。 */
    protected ReconciliationResponseChecker exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = operator(ReconciliationTestConstants.MAKER_ID);
        return new ReconciliationResponseChecker(http.exchange(method, path, headers, body), objectMapper, REQUEST_ID);
    }

    /** 开始、分段追加、完成导入并等待匹配任务结束；返回账单主键，完成结果须为 MATCHING。 */
    protected long importStatement(StatementImportBuilder statement) {
        long statementId = start(statement).assertSuccess(201).id("statementId");
        List<AppendChannelStatementLinesRequestBody.Line> lines = statement.lines();
        for (int from = 0; from < lines.size(); from += SEGMENT) {
            append(statementId, lines.subList(from, Math.min(lines.size(), from + SEGMENT))).assertSuccess(200);
        }
        complete(statementId).assertSuccess(200).assertText("status", ChannelStatementStatus.MATCHING.getCode());
        awaitIdle();
        return statementId;
    }

    /** 等待本地补偿任务（匹配、事件发布）全部结束。 */
    protected void awaitIdle() {
        await().atMost(ASYNC_WAIT).until(() -> testMapper.countCompensationEvents() == 0);
    }

    /** 账单的某一行。 */
    protected ChannelStatementLine line(long statementId, int lineNo) {
        return channelStatementLines.listByStatementAndLineNos(statementId, List.of(lineNo)).getFirst();
    }

    /** 某对象某类型的未结异常。 */
    protected ReconException activeException(ReconExceptionSourceType sourceType, long sourceId,
        ReconExceptionType type) {
        return reconExceptions.findActive(sourceType, sourceId, type);
    }

    /** 断言账单状态。 */
    protected void assertStatementStatus(long statementId, ChannelStatementStatus expected) {
        assertEquals(expected, channelStatements.findById(statementId).getStatus(), "账单状态");
    }

    /**
     * 扮演 C 线批准账单行的 CLASSIFY 处置：同一事务内插入已应用处置（金额 = 异常差异）、异常 OPEN → UNDER_REVIEW → RESOLVED、
     * 应用到账单行并登记门禁重算，提交后等待匹配任务结束
     *
     * @param lineId 账单行
     * @param exceptionType 行上未结异常类型
     * @param code 归类码
     * @return 已应用的处置
     */
    protected ReconResolution classifyLine(long lineId, ReconExceptionType exceptionType, ReconClassification code) {
        ReconException exception = activeException(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE, lineId,
            exceptionType);
        ReconResolution resolution = resolution(exception, ResolutionType.CLASSIFY, code);
        transactionTemplate.executeWithoutResult(status -> {
            reconResolutions.insert(resolution);
            reconExceptionService.markUnderReview(exception);
            reconExceptionService.resolve(exception);
            resolutionService.applyLineClassification(lineId, resolution.getId());
            resolutionService.scheduleStatementEvaluation(exception.getStatementId(), resolution.getId());
        });
        awaitIdle();
        return resolution;
    }

    /** 已应用（批准）的处置记录。 */
    protected static ReconResolution resolution(ReconException exception, ResolutionType type, ReconClassification code) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ReconResolution resolution = new ReconResolution();
        resolution.setId(ReconciliationTestConstants.nextId());
        resolution.setExceptionId(exception.getId());
        resolution.setResolutionNo(1);
        resolution.setResolutionType(type);
        resolution.setClassificationCode(code);
        resolution.setAmount(code == null ? null : exception.getDifferenceAmount());
        resolution.setNote("test resolution");
        resolution.setContentHash("c".repeat(64));
        resolution.setMakerId(ReconciliationTestConstants.MAKER_ID);
        resolution.setCheckerId(ReconciliationTestConstants.CHECKER_ID);
        resolution.setCheckerDecision(CheckerDecision.APPROVED);
        resolution.setApprovedContentHash("c".repeat(64));
        resolution.setDecisionTime(now);
        resolution.setStatus(ResolutionStatus.APPLIED);
        resolution.setVersion(1L);
        resolution.setCreateTime(now);
        resolution.setUpdateTime(now);
        return resolution;
    }

    /** 断言某异常已结案为给定状态。 */
    protected void assertExceptionStatus(long exceptionId, ReconExceptionStatus expected) {
        assertEquals(expected, reconExceptions.findById(exceptionId).getStatus(), "异常状态");
    }
}
