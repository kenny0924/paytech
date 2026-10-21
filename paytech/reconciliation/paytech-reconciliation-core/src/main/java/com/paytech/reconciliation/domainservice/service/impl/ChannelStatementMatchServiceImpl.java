package com.paytech.reconciliation.domainservice.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.ChannelLineMatchChange;
import com.paytech.reconciliation.domain.PlatformTxnProgressChange;
import com.paytech.reconciliation.domain.PlatformTxns;
import com.paytech.reconciliation.domain.StatementMatchContext;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementMatchService;
import com.paytech.reconciliation.service.handler.ActualChannelCostConfirmedHandler;
import com.paytech.reconciliation.service.handler.ChannelBatchReconciledHandler;

import lombok.AllArgsConstructor;

/** 匹配结果与对平保存；事务传播为 REQUIRED，与匹配处理器的异常开立、预期银行流水生成在同一事务提交。 */
@Service
@AllArgsConstructor
public class ChannelStatementMatchServiceImpl implements ChannelStatementMatchService {
    private final TransactionTemplate transactionTemplate;
    private final PlatformTxnRepository platformTxnRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    /** 批量写匹配结果，再批量写被覆盖平台流水的新进度（均按版本条件更新）。 */
    @Override
    public void saveMatches(StatementMatchContext context) {
        List<ChannelLineMatchChange> lineChanges = context.getLineMatches().stream()
            .map(match -> new ChannelLineMatchChange(match.getLine(), match.getPlatformTxnId(), match.getMatchMethod(),
                match.getStatus() == LineMatchStatus.MATCHED ? context.getNow() : null, match.getActualFee(),
                match.getLine().getResolutionId(), match.getStatus()))
            .toList();
        List<PlatformTxnProgressChange> progressChanges = context.getTxnProgress().values().stream()
            .filter(progress -> progress.getMatchedAmount().compareTo(progress.getTxn().getMatchedAmount()) != 0)
            .map(progress -> new PlatformTxnProgressChange(progress.getTxn(), progress.getMatchedAmount(),
                progress.getActualFee(), progress.getTxn().getSettledAmount(), progress.status()))
            .toList();
        transactionTemplate.executeWithoutResult(status -> {
            channelStatementLineRepository.updateMatches(lineChanges);
            platformTxnRepository.updateProgresses(progressChanges);
        });
    }

    /** 结清平台流水、推进账单状态并登记对平与成本事件。 */
    @Override
    public void reconcile(StatementMatchContext context, List<PlatformTxn> coveredTxns) {
        ChannelStatement statement = context.getStatement();
        Map<Long, BigDecimal> covered = coveredAmounts(context.getLines());
        List<PlatformTxnProgressChange> settlements = coveredTxns.stream()
            .map(txn -> settlement(txn, covered.getOrDefault(txn.getId(), BigDecimal.ZERO))).toList();
        transactionTemplate.executeWithoutResult(status -> {
            platformTxnRepository.updateProgresses(settlements);
            settlements.stream().filter(change -> change.status() == PlatformTxnStatus.SETTLED)
                .map(PlatformTxnProgressChange::record).filter(txn -> PlatformTxns.feeDifference(txn).signum() != 0)
                .forEach(txn -> compensationEventRegistrar.register(ActualChannelCostConfirmedHandler.buildEvent(txn.getId())));
            ChannelStatementStatus target = statement.getCashDirection() == CashDirection.NONE
                ? ChannelStatementStatus.CASH_CONFIRMED : ChannelStatementStatus.RECONCILED;
            channelStatementRepository.changeStatus(statement, target, null, context.getNow());
            compensationEventRegistrar.register(ChannelBatchReconciledHandler.buildEvent(statement.getId()));
        });
    }

    /** 累加已结清金额；满额转 SETTLED（满额且费用有差额的登记成本确认事件）。 */
    static PlatformTxnProgressChange settlement(PlatformTxn txn, BigDecimal amount) {
        BigDecimal settled = txn.getSettledAmount().add(amount);
        boolean fullySettled = settled.compareTo(txn.getExpectedAmount()) >= 0;
        PlatformTxnStatus status = fullySettled ? PlatformTxnStatus.SETTLED : txn.getStatus();
        return new PlatformTxnProgressChange(txn, txn.getMatchedAmount(), txn.getActualFee(), settled, status);
    }

    /** 本账单行按平台流水累计的覆盖金额。 */
    static Map<Long, BigDecimal> coveredAmounts(List<ChannelStatementLine> lines) {
        return lines.stream().filter(line -> line.getPlatformTxnId() != null).collect(Collectors.groupingBy(
            ChannelStatementLine::getPlatformTxnId, Collectors.reducing(BigDecimal.ZERO, ChannelStatementLine::getAmount,
                BigDecimal::add)));
    }
}
