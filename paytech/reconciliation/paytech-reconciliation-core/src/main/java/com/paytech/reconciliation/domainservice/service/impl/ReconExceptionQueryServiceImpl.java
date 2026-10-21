package com.paytech.reconciliation.domainservice.service.impl;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.ReconResolutionRules;
import com.paytech.reconciliation.domain.view.ReconExceptionDetailView;
import com.paytech.reconciliation.domain.view.ReconSummaryView;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.repository.ReconResolutionRepository;
import com.paytech.reconciliation.domainservice.service.ReconExceptionQueryService;
import lombok.AllArgsConstructor;

/** 对账异常查询：来源对象摘要按类型读取所属表；在途汇总由三张表的聚合 SQL 组成。 */
@Service
@AllArgsConstructor
public class ReconExceptionQueryServiceImpl implements ReconExceptionQueryService {
    private final Clock clock;
    private final PlatformTxnRepository platformTxnRepository;
    private final BankStatementRepository bankStatementRepository;
    private final ReconExceptionRepository reconExceptionRepository;
    private final ReconResolutionRepository reconResolutionRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final BankStatementLineRepository bankStatementLineRepository;
    private final ChannelStatementLineRepository channelStatementLineRepository;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;

    /** 分页查询；未给状态时只查未结。 */
    @Override
    public PagingResponseBody<ReconException> listExceptions(ReconExceptionStatus status,
        ReconExceptionType exceptionType, ReconLayer reconLayer, Long clearingAccountId, Long cashAccountId,
        Long statementId, PagingRequestBody paging) {
        List<ReconExceptionStatus> statuses = status != null ? List.of(status)
            : List.of(ReconExceptionStatus.OPEN, ReconExceptionStatus.UNDER_REVIEW);
        List<ReconException> items = reconExceptionRepository.findPage(statuses, exceptionType, reconLayer,
            clearingAccountId, cashAccountId, statementId, paging.offset(), paging.size());
        long total = reconExceptionRepository.countPage(statuses, exceptionType, reconLayer, clearingAccountId,
            cashAccountId, statementId);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 详情：来源摘要、可用处置（仅 OPEN）、处置历史。 */
    @Override
    public ReconExceptionDetailView getException(long exceptionId) {
        ReconException exception = reconExceptionRepository.findById(exceptionId);
        if (exception == null) {
            throw new BizCheckFailException("对账异常不存在: " + exceptionId);
        }
        boolean open = exception.getStatus() == ReconExceptionStatus.OPEN;
        List<ResolutionType> types = open ? ReconResolutionRules.allowedTypes(exception.getExceptionType()) : List.of();
        List<ReconClassification> codes = types.contains(ResolutionType.CLASSIFY)
            ? ReconResolutionRules.allowedCodes(exception.getSourceType()) : List.of();
        return new ReconExceptionDetailView(exception, source(exception), types, codes,
            reconResolutionRepository.listByException(exceptionId));
    }

    /** 按来源对象类型读取摘要。 */
    ReconExceptionDetailView.SourceSummary source(ReconException exception) {
        long id = exception.getSourceId();
        return switch (exception.getSourceType()) {
            case CHANNEL_STATEMENT -> statementSummary(channelStatementRepository.findById(id));
            case CHANNEL_STATEMENT_LINE -> lineSummary(channelStatementLineRepository.findById(id));
            case PLATFORM_TXN -> txnSummary(platformTxnRepository.findById(id));
            case BANK_STATEMENT -> bankStatementSummary(bankStatementRepository.findById(id));
            case BANK_STATEMENT_LINE -> bankLineSummary(bankStatementLineRepository.findById(id));
            case EXPECTED_BANK_MOVEMENT -> movementSummary(expectedBankMovementRepository.findById(id));
        };
    }

    /** 渠道账单摘要。 */
    static ReconExceptionDetailView.SourceSummary statementSummary(ChannelStatement statement) {
        return new ReconExceptionDetailView.SourceSummary(statement.getChannelBatchRef() + " #" + statement.getRevision(),
            null, null, statement.getStatus().getCode(), statement.getSettlementDate());
    }

    /** 渠道账单行摘要：批次号与行号。 */
    ReconExceptionDetailView.SourceSummary lineSummary(ChannelStatementLine line) {
        ChannelStatement statement = channelStatementRepository.findById(line.getStatementId());
        return new ReconExceptionDetailView.SourceSummary(statement.getChannelBatchRef() + " 第 " + line.getLineNo() + " 行",
            line.getDirection().getCode(), line.getAmount(), line.getMatchStatus().getCode(), line.getTransactionDate());
    }

    /** 平台流水摘要：外部交易号。 */
    static ReconExceptionDetailView.SourceSummary txnSummary(PlatformTxn txn) {
        return new ReconExceptionDetailView.SourceSummary(txn.getExternalTransactionId(), txn.getDirection().getCode(),
            txn.getExpectedAmount(), txn.getStatus().getCode(), null);
    }

    /** 银行对账单摘要。 */
    static ReconExceptionDetailView.SourceSummary bankStatementSummary(BankStatement statement) {
        return new ReconExceptionDetailView.SourceSummary(statement.getStatementRef(), null, null,
            statement.getStatus().getCode(), statement.getPeriodEnd());
    }

    /** 银行流水摘要：银行交易号。 */
    static ReconExceptionDetailView.SourceSummary bankLineSummary(BankStatementLine line) {
        return new ReconExceptionDetailView.SourceSummary(line.getBankTransactionRef(), line.getDirection().getCode(),
            line.getAmount(), line.getMatchStatus().getCode(), line.getValueDate());
    }

    /** 预期银行流水摘要：来源类型与来源 ID。 */
    static ReconExceptionDetailView.SourceSummary movementSummary(ExpectedBankMovement movement) {
        return new ReconExceptionDetailView.SourceSummary(movement.getMovementType().getCode() + " " + movement.getSourceId(),
            movement.getDirection().getCode(), movement.getExpectedAmount(), movement.getStatus().getCode(),
            movement.getExpectedDate());
    }

    /** 三块聚合。 */
    @Override
    public ReconSummaryView summarize(String entityNo, String currency) {
        return new ReconSummaryView(channelStatementRepository.sumInTransit(entityNo, currency),
            reconExceptionRepository.sumOpenByAge(entityNo, currency, clock.instant()),
            bankStatementLineRepository.sumUnmatched(entityNo, currency));
    }
}
