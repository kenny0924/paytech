package com.paytech.reconciliation.service.processor;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.domain.BankMatchContext;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;
import com.paytech.reconciliation.domainservice.service.BankMatchService;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.service.processor.request.BankMatchCommandRequest;
import com.paytech.reconciliation.service.processor.response.BankMatchResponse;
import lombok.AllArgsConstructor;

/**
 * 确认系统建议（UC-R13，#22，AC-10）
 * <li>锁银行流水与预期银行流水（tryLocks 统一排序，流水 Key 在前）；已确认返回当前结果，已拒绝的建议不能确认。</li>
 * <li>确认在锁内重新校验不变量（同一银行账户、流水带符号合计不超过流水金额、不超过预期剩余），同一事务更新流水、预期流水与渠道账单进度，
 * 登记 ChannelSettlementConfirmed，并自动结案因此结清的 BANK_LINE_UNMATCHED / BANK_AMOUNT_MISMATCH。</li>
 */
@Component
@AllArgsConstructor
public class ConfirmBankMatchProcessor extends AbstractLockProcessor<BankMatchCommandRequest, BankMatchResponse> {
    private final BankMatchService bankMatchService;
    private final TransactionTemplate transactionTemplate;
    private final BankMatchRepository bankMatchRepository;
    private final ReconExceptionService reconExceptionService;
    private final BankReconciliationQueryService bankReconciliationQueryService;

    /** 银行流水与预期银行流水锁（匹配的两端不可变，锁前读取）；匹配不存在时直接拒绝。 */
    @Override
    protected LockCommand lockCommand(BankMatchCommandRequest request) {
        BankMatch match = load(request.getBankMatchId());
        return LockCommand.tryLocks(ReconciliationLocks.bankMatch(match.getBankStatementLineId(),
            List.of(match.getExpectedMovementId())), ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected BankMatchResponse processWithinLock(BankMatchCommandRequest request) {
        // 查询原建议
        BankMatch proposal = load(request.getBankMatchId());
        if (proposal.getStatus() == BankMatchStatus.CONFIRMED) {
            return new BankMatchResponse(List.of(proposal), bankReconciliationQueryService.currencyOf(proposal));
        }
        if (proposal.getStatus() == BankMatchStatus.REJECTED) {
            throw new BizCheckFailException("建议已拒绝，不能确认: " + proposal.getId());
        }

        // 构建上下文并校验
        BankMatchContext context = bankReconciliationQueryService.loadMatchContext(proposal.getBankStatementLineId(),
            List.of(new BankMatchContext.AllocationInput(proposal.getExpectedMovementId(), proposal.getMatchedAmount(),
                proposal)), MatchMethod.AUTO, request.getActorId(), null);
        context.check();

        // 本地保存
        List<BankMatch> confirmed = transactionTemplate.execute(status -> {
            List<BankMatch> matches = bankMatchService.confirm(context);
            closeSettledExceptions(context);
            return matches;
        });
        return new BankMatchResponse(confirmed, context.getAllocations().getFirst().getMovement().getCurrency());
    }

    /** 读取匹配，不存在为业务拒绝。 */
    BankMatch load(Long bankMatchId) {
        BankMatch match = bankMatchRepository.findById(bankMatchId);
        if (match == null) {
            throw new BizCheckFailException("银行匹配不存在: " + bankMatchId);
        }
        return match;
    }

    /** 确认后已结清的流水与预期流水上的开放银行层异常自动结案（设计 F2）。 */
    void closeSettledExceptions(BankMatchContext context) {
        if (context.getLine().getMatchStatus() == LineMatchStatus.MATCHED) {
            reconExceptionService.autoClose(ReconExceptionSourceType.BANK_STATEMENT_LINE, context.getLine().getId(),
                ReconExceptionType.BANK_LINE_UNMATCHED);
        }
        for (BankMatchContext.Allocation allocation : context.getAllocations()) {
            if (allocation.getMovement().getStatus() == ExpectedMovementStatus.CONFIRMED) {
                reconExceptionService.autoClose(ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT,
                    allocation.getMovement().getId(), ReconExceptionType.BANK_AMOUNT_MISMATCH);
            }
        }
    }
}
