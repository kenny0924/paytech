package com.paytech.reconciliation.domainservice.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.ChannelLineSearch;
import com.paytech.reconciliation.domain.ReconcileGate;
import com.paytech.reconciliation.domain.view.ChannelStatementDetailView;
import com.paytech.reconciliation.domain.view.ChannelStatementLineView;
import com.paytech.reconciliation.domain.view.ChannelStatementView;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementQueryService;

import lombok.AllArgsConstructor;

/** 渠道账单与平台流水查询；详情的门禁与匹配任务使用同一纯计算 {@link ReconcileGate}。 */
@Service
@AllArgsConstructor
public class ChannelStatementQueryServiceImpl implements ChannelStatementQueryService {
    /** 跨账单查询每页条数上限。 */
    static final int MAX_SEARCH_PAGE_SIZE = 100;

    private final PlatformTxnRepository platformTxnRepository;
    private final ReconExceptionRepository reconExceptionRepository;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final ChannelStatementLineRepository channelStatementLineRepository;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;

    /** 分页查询账单并补清算账户。 */
    @Override
    public PagingResponseBody<ChannelStatementView> listStatements(Long clearingAccountId, String channelBatchRef,
        ChannelStatementStatus status, LocalDate settlementDateFrom, LocalDate settlementDateTo,
        PagingRequestBody paging) {
        List<ChannelStatement> statements = channelStatementRepository.findPage(clearingAccountId, channelBatchRef,
            status, settlementDateFrom, settlementDateTo, paging.offset(), paging.size());
        Map<Long, ClearingAccount> accounts = clearingAccountRepository.findByIds(statements.stream()
            .map(ChannelStatement::getClearingAccountId).distinct().toList()).stream()
            .collect(Collectors.toMap(ClearingAccount::getId, Function.identity()));
        List<ChannelStatementView> items = statements.stream()
            .map(statement -> new ChannelStatementView(statement, accounts.get(statement.getClearingAccountId()))).toList();
        long total = channelStatementRepository.countPage(clearingAccountId, channelBatchRef, status, settlementDateFrom,
            settlementDateTo);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 详情：行计数、未结异常数、预期银行流水与门禁。 */
    @Override
    public ChannelStatementDetailView getStatement(long statementId) {
        ChannelStatement statement = load(statementId);
        ClearingAccount account = clearingAccountRepository.findById(statement.getClearingAccountId());
        List<ReconException> active = reconExceptionRepository.listActiveByStatement(statementId);
        ExpectedBankMovement movement = expectedBankMovementRepository.findBySource(
            ExpectedMovementType.CHANNEL_SETTLEMENT, statementId);
        ReconcileGate gate = statement.getStatus() != ChannelStatementStatus.MATCHING ? null
            : ReconcileGate.evaluate(statement, account, channelStatementLineRepository.listByStatement(statementId),
                active);
        return new ChannelStatementDetailView(statement, account, countLines(statementId, LineMatchStatus.UNMATCHED),
            countLines(statementId, LineMatchStatus.MATCHED), countLines(statementId, LineMatchStatus.EXCEPTION),
            active.size(), movement == null ? null : movement.getId(), gate);
    }

    /** 读取账单头。 */
    @Override
    public ChannelStatement getStatementHeader(long statementId) {
        return load(statementId);
    }

    /** 分页查询账单行。 */
    @Override
    public PagingResponseBody<ChannelStatementLine> listLines(long statementId, LineMatchStatus matchStatus,
        ChannelLineType lineType, PagingRequestBody paging) {
        load(statementId);
        List<ChannelStatementLine> items = channelStatementLineRepository.findPage(statementId, matchStatus, lineType,
            paging.offset(), paging.size());
        long total = channelStatementLineRepository.countPage(statementId, matchStatus, lineType);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 跨账单查询账单行：一次分页查询 + 一次总数，所属账单与清算账户按本页主键批量补齐。 */
    @Override
    public PagingResponseBody<ChannelStatementLineView> searchLines(ChannelLineSearch criteria,
        PagingRequestBody paging) {
        if (paging.size() > MAX_SEARCH_PAGE_SIZE) {
            throw new InvalidParameterException("每页条数必须为 10、20、50 或 100: " + paging.size());
        }
        List<ChannelStatementLine> lines = channelStatementLineRepository.searchPage(criteria, paging.offset(),
            paging.size());
        Map<Long, ChannelStatement> statements = channelStatementRepository.findByIds(lines.stream()
            .map(ChannelStatementLine::getStatementId).distinct().toList()).stream()
            .collect(Collectors.toMap(ChannelStatement::getId, Function.identity()));
        Map<Long, ClearingAccount> accounts = clearingAccountRepository.findByIds(lines.stream()
            .map(ChannelStatementLine::getClearingAccountId).distinct().toList()).stream()
            .collect(Collectors.toMap(ClearingAccount::getId, Function.identity()));
        List<ChannelStatementLineView> items = lines.stream().map(line -> new ChannelStatementLineView(line,
            statements.get(line.getStatementId()), accounts.get(line.getClearingAccountId()))).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(),
            channelStatementLineRepository.countSearch(criteria));
    }

    /** 分页查询平台流水。 */
    @Override
    public PagingResponseBody<PlatformTxn> listPlatformTxns(Long clearingAccountId, Long statementId,
        PlatformTxnStatus status, ClearingDirection direction, String externalTransactionId, Instant businessTimeFrom,
        Instant businessTimeTo, PagingRequestBody paging) {
        List<PlatformTxn> items = platformTxnRepository.findPage(clearingAccountId, statementId, status, direction,
            externalTransactionId, businessTimeFrom, businessTimeTo, paging.offset(), paging.size());
        long total = platformTxnRepository.countPage(clearingAccountId, statementId, status, direction,
            externalTransactionId, businessTimeFrom, businessTimeTo);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 读取账单，不存在为业务拒绝。 */
    ChannelStatement load(long statementId) {
        ChannelStatement statement = channelStatementRepository.findById(statementId);
        if (statement == null) {
            throw new BizCheckFailException("渠道账单不存在: " + statementId);
        }
        return statement;
    }

    /** 某匹配状态的行数。 */
    long countLines(long statementId, LineMatchStatus matchStatus) {
        return channelStatementLineRepository.countPage(statementId, matchStatus, null);
    }
}
