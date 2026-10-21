package com.paytech.reconciliation.service.processor;

import java.util.List;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;
import com.paytech.reconciliation.domainservice.service.BankMatchService;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import com.paytech.reconciliation.service.processor.request.BankMatchCommandRequest;
import com.paytech.reconciliation.service.processor.response.BankMatchResponse;
import lombok.AllArgsConstructor;

/**
 * 拒绝系统建议（UC-R13，#22）：PROPOSED → REJECTED，不改流水与预期流水
 * <li>与确认共用银行流水与预期银行流水锁；已拒绝返回当前结果，已确认的不能拒绝。被拒绝的配对之后不再建议。</li>
 */
@Component
@AllArgsConstructor
public class RejectBankMatchProcessor extends AbstractLockProcessor<BankMatchCommandRequest, BankMatchResponse> {
    private final BankMatchService bankMatchService;
    private final BankMatchRepository bankMatchRepository;
    private final BankReconciliationQueryService bankReconciliationQueryService;

    /** 银行流水与预期银行流水锁；匹配不存在时直接拒绝。 */
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
            throw new BizCheckFailException("建议已确认，不能拒绝: " + proposal.getId());
        }

        // 本地保存
        if (proposal.getStatus() == BankMatchStatus.PROPOSED) {
            bankMatchService.reject(proposal, request.getActorId());
        }
        return new BankMatchResponse(List.of(proposal), bankReconciliationQueryService.currencyOf(proposal));
    }

    /** 读取匹配，不存在为业务拒绝。 */
    BankMatch load(Long bankMatchId) {
        BankMatch match = bankMatchRepository.findById(bankMatchId);
        if (match == null) {
            throw new BizCheckFailException("银行匹配不存在: " + bankMatchId);
        }
        return match;
    }
}
