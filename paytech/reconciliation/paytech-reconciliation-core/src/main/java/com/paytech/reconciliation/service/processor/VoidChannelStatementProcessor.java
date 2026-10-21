package com.paytech.reconciliation.service.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.PlatformTxns;
import com.paytech.reconciliation.domain.VoidChannelStatementContext;
import com.paytech.reconciliation.domain.view.ChannelStatementView;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.service.ChannelStatementService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.service.processor.request.ChannelStatementCommandRequest;

import lombok.AllArgsConstructor;

/**
 * 作废未对平的渠道账单（UC-R08，#15）
 * <li>锁 channel-statement:{id}；已作废返回当前结果；只限 RECEIVING / MATCHING，已对平（含 CASH_CONFIRMED）拒绝；有待复核处置
 * （异常 UNDER_REVIEW）拒绝。</li>
 * <li>同一本地事务：释放本账单已处理的行（匹配、实际费用、归类清空回 UNMATCHED）、回退被覆盖平台流水的已匹配金额与实际费用及状态、本账单
 * 未结异常 AUTO_CLOSED、账单转 VOIDED。被回退的平台流水若已结清（与已匹配金额矛盾）拒绝作废。</li>
 */
@Component
@AllArgsConstructor
public class VoidChannelStatementProcessor
    extends AbstractLockProcessor<ChannelStatementCommandRequest, ChannelStatementView> {
    private final TransactionTemplate transactionTemplate;
    private final PlatformTxnRepository platformTxnRepository;
    private final ReconExceptionService reconExceptionService;
    private final ChannelStatementService channelStatementService;
    private final ReconExceptionRepository reconExceptionRepository;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final ChannelStatementLineRepository channelStatementLineRepository;

    /** 渠道账单资源锁。 */
    @Override
    protected LockCommand lockCommand(ChannelStatementCommandRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.channelStatement(request.getStatementId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected ChannelStatementView processWithinLock(ChannelStatementCommandRequest request) {
        // 查询原账单
        ChannelStatement statement = channelStatementRepository.findById(request.getStatementId());
        if (statement == null) {
            throw new BizCheckFailException("渠道账单不存在: " + request.getStatementId());
        }
        if (statement.getStatus() == ChannelStatementStatus.VOIDED) {
            return view(statement);
        }

        // 业务验证与上下文
        List<ReconException> activeExceptions = checkVoidable(statement);
        VoidChannelStatementContext context = buildContext(statement);

        // 本地保存：释放、回退、自动结案、作废
        transactionTemplate.executeWithoutResult(status -> {
            channelStatementService.voidStatement(context);
            activeExceptions.forEach(exception -> reconExceptionService.autoClose(exception.getSourceType(),
                exception.getSourceId(), exception.getExceptionType()));
        });
        return view(statement);
    }

    /** 状态只限 RECEIVING / MATCHING，且没有待复核处置；返回本账单未结异常。 */
    List<ReconException> checkVoidable(ChannelStatement statement) {
        if (statement.getStatus() != ChannelStatementStatus.RECEIVING
            && statement.getStatus() != ChannelStatementStatus.MATCHING) {
            throw new BizCheckFailException("只有接收中或匹配中的渠道账单可以作废: 账单 " + statement.getId() + ", 状态 "
                + statement.getStatus().getCode());
        }
        List<ReconException> active = reconExceptionRepository.listActiveByStatement(statement.getId());
        if (active.stream().anyMatch(exception -> exception.getStatus() == ReconExceptionStatus.UNDER_REVIEW)) {
            throw new BizCheckFailException("渠道账单有待复核的处置，不能作废: 账单 " + statement.getId());
        }
        return active;
    }

    /** 需要释放的行与需要回退的平台流水。 */
    VoidChannelStatementContext buildContext(ChannelStatement statement) {
        List<ChannelStatementLine> lines = channelStatementLineRepository.listByStatement(statement.getId());
        List<ChannelStatementLine> released = lines.stream()
            .filter(line -> line.getMatchStatus() != LineMatchStatus.UNMATCHED).toList();
        List<Long> txnIds = released.stream().map(ChannelStatementLine::getPlatformTxnId).filter(Objects::nonNull)
            .distinct().toList();
        List<VoidChannelStatementContext.TxnRollback> rollbacks = new ArrayList<>();
        for (PlatformTxn txn : platformTxnRepository.findByIds(txnIds)) {
            rollbacks.add(rollback(txn, released));
        }
        return new VoidChannelStatementContext(statement, released, rollbacks);
    }

    /** 扣回本账单行覆盖的金额与实际费用。 */
    static VoidChannelStatementContext.TxnRollback rollback(PlatformTxn txn, List<ChannelStatementLine> lines) {
        if (txn.getStatus() == PlatformTxnStatus.SETTLED) {
            throw new BizCheckFailException("渠道账单覆盖的平台流水已结清，不能作废: 平台流水 " + txn.getId());
        }
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        for (ChannelStatementLine line : lines) {
            if (txn.getId().equals(line.getPlatformTxnId())) {
                amount = amount.add(line.getAmount());
                fee = line.getActualFee() == null ? fee : fee.add(line.getActualFee());
            }
        }
        BigDecimal matched = txn.getMatchedAmount().subtract(amount);
        return new VoidChannelStatementContext.TxnRollback(txn, matched, txn.getActualFee().subtract(fee),
            PlatformTxns.progressStatus(txn, matched));
    }

    /** 账单与清算账户。 */
    ChannelStatementView view(ChannelStatement statement) {
        return new ChannelStatementView(statement, clearingAccountRepository.findById(statement.getClearingAccountId()));
    }
}
