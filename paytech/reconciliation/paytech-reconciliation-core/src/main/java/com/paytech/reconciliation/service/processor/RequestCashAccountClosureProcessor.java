package com.paytech.reconciliation.service.processor;

import java.util.List;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.domain.CashAccountReviews;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.service.CashAccountService;
import com.paytech.reconciliation.service.processor.request.CashAccountCommandRequest;
import lombok.AllArgsConstructor;

/**
 * 申请关闭银行账户（UC-R02，#8，technique D-03）：ACTIVE 账户记 pendingAction = CLOSE，等待复核
 * <li>锁 cash-account:{id}，与复核共用；已有关闭申请或账户不是 ACTIVE 时拒绝。提交人与内容摘要更新为本次申请。</li>
 */
@Component
@AllArgsConstructor
public class RequestCashAccountClosureProcessor extends AbstractLockProcessor<CashAccountCommandRequest, CashAccount> {
    private final CashAccountService cashAccountService;
    private final CashAccountRepository cashAccountRepository;

    /** 银行账户资源锁。 */
    @Override
    protected LockCommand lockCommand(CashAccountCommandRequest request) {
        return LockCommand.tryLocks(List.of(ReconciliationLocks.cashAccount(request.getCashAccountId())),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected CashAccount processWithinLock(CashAccountCommandRequest request) {
        // 业务验证
        CashAccount account = cashAccountRepository.findById(request.getCashAccountId());
        if (account == null) {
            throw new BizCheckFailException("银行账户不存在: " + request.getCashAccountId());
        }
        if (account.getStatus() != CashAccountStatus.ACTIVE || account.getPendingAction() != null) {
            throw new BizCheckFailException("银行账户不是 ACTIVE 或已有待复核的关闭申请: " + account.getId() + ", 状态 "
                + account.getStatus().getCode());
        }

        // 本地保存
        cashAccountService.requestClosure(account, request.getActorId(),
            CashAccountReviews.closeHash(account, request.getActorId()));
        return account;
    }
}
