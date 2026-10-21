package com.paytech.reconciliation.service.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.domain.BankMatchContext;
import com.paytech.reconciliation.domainservice.service.BankMatchService;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.service.processor.request.CreateBankMatchesRequest;
import com.paytech.reconciliation.service.processor.response.BankMatchResponse;
import lombok.AllArgsConstructor;

/**
 * 人工多选匹配（UC-R13，#23，AC-10）：一条银行流水分配给多条预期银行流水，直接 CONFIRMED（MANUAL）
 * <li>锁银行流水与全部预期银行流水（tryLocks 统一排序）；同一预期流水只能出现一次。</li>
 * <li>锁内校验：同一银行账户、对账单已导入、预期流水可确认、每条不超过预期剩余、流水带符号合计（同向为正）落在 0 到流水金额之间
 * （{@link BankMatchContext#check()}）。</li>
 * <li>同一事务更新流水、预期流水与渠道账单进度，每条渠道匹配登记 ChannelSettlementConfirmed，自动结案因此结清的银行层异常。
 * 没有业务唯一身份，同一请求重发会被剩余金额校验拒绝。</li>
 */
@Component
@AllArgsConstructor
public class CreateBankMatchesProcessor extends AbstractLockProcessor<CreateBankMatchesRequest, BankMatchResponse> {
    private final BankMatchService bankMatchService;
    private final TransactionTemplate transactionTemplate;
    private final ReconExceptionService reconExceptionService;
    private final BankReconciliationQueryService bankReconciliationQueryService;

    /** 同一预期流水不得重复（锁前，只依赖输入）。 */
    @Override
    protected void validate(CreateBankMatchesRequest request) {
        Set<Long> seen = new HashSet<>();
        for (CreateBankMatchesRequest.Allocation allocation : request.getAllocations()) {
            if (!seen.add(allocation.getExpectedMovementId())) {
                throw new BizCheckFailException("同一预期银行流水在一次匹配中重复出现: " + allocation.getExpectedMovementId());
            }
        }
    }

    /** 银行流水与全部预期银行流水锁。 */
    @Override
    protected LockCommand lockCommand(CreateBankMatchesRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.bankMatch(request.getBankStatementLineId(),
            request.getAllocations().stream().map(CreateBankMatchesRequest.Allocation::getExpectedMovementId).toList()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected BankMatchResponse processWithinLock(CreateBankMatchesRequest request) {
        // 构建上下文并校验
        List<BankMatchContext.AllocationInput> inputs = request.getAllocations().stream()
            .map(allocation -> new BankMatchContext.AllocationInput(allocation.getExpectedMovementId(),
                allocation.getAmount(), null)).toList();
        BankMatchContext context = bankReconciliationQueryService.loadMatchContext(request.getBankStatementLineId(),
            inputs, MatchMethod.MANUAL, request.getActorId(), null);
        context.check();

        // 本地保存
        List<BankMatch> confirmed = transactionTemplate.execute(status -> {
            List<BankMatch> matches = bankMatchService.confirm(context);
            closeSettledExceptions(context);
            return matches;
        });
        return new BankMatchResponse(confirmed, context.getAllocations().getFirst().getMovement().getCurrency());
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
