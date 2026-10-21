package com.paytech.reconciliation.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.domain.VoidChannelStatementContext;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementService;
import com.paytech.reconciliation.service.handler.ChannelStatementMatchHandler;

import lombok.AllArgsConstructor;

/** 渠道账单分段导入与作废；事务传播为 REQUIRED。 */
@Service
@AllArgsConstructor
public class ChannelStatementServiceImpl implements ChannelStatementService {
    private final TransactionTemplate transactionTemplate;
    private final PlatformTxnRepository platformTxnRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    /** 插入账单头。 */
    @Override
    public void start(ChannelStatement statement) {
        transactionTemplate.executeWithoutResult(status -> channelStatementRepository.insert(statement));
    }

    /** 批量插入新行并更新已接收行数。 */
    @Override
    public void appendLines(ChannelStatement statement, List<ChannelStatementLine> lines) {
        if (lines.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            channelStatementLineRepository.insertBatch(lines);
            channelStatementRepository.updateImportedLineCount(statement,
                statement.getImportedLineCount() + lines.size());
        });
    }

    /** RECEIVING → MATCHING 并登记匹配任务。 */
    @Override
    public void accept(ChannelStatement statement) {
        transactionTemplate.executeWithoutResult(status -> {
            channelStatementRepository.changeStatus(statement, ChannelStatementStatus.MATCHING, null, null);
            compensationEventRegistrar.register(ChannelStatementMatchHandler.buildEvent(statement.getId()));
        });
    }

    /** RECEIVING → REJECTED。 */
    @Override
    public void reject(ChannelStatement statement, String rejectReason) {
        transactionTemplate.executeWithoutResult(status -> channelStatementRepository.changeStatus(statement,
            ChannelStatementStatus.REJECTED, rejectReason, null));
    }

    /** 行 UNMATCHED → EXCEPTION。 */
    @Override
    public void markLineException(ChannelStatementLine line) {
        transactionTemplate.executeWithoutResult(status -> channelStatementLineRepository.updateMatch(line, null, null,
            null, null, null, LineMatchStatus.EXCEPTION));
    }

    /** 释放行、回退平台流水、账单转 VOIDED。 */
    @Override
    public void voidStatement(VoidChannelStatementContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            for (ChannelStatementLine line : context.getReleasedLines()) {
                channelStatementLineRepository.updateMatch(line, null, null, null, null, null,
                    LineMatchStatus.UNMATCHED);
            }
            for (VoidChannelStatementContext.TxnRollback rollback : context.getTxnRollbacks()) {
                platformTxnRepository.updateProgress(rollback.getTxn(), rollback.getMatchedAmount(),
                    rollback.getActualFee(), rollback.getTxn().getSettledAmount(), rollback.getStatus());
            }
            channelStatementRepository.changeStatus(context.getStatement(), ChannelStatementStatus.VOIDED, null, null);
        });
    }
}
