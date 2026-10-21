package com.paytech.reconciliation.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.domain.view.ClearingAccountView;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ClearingAccountService;
import com.paytech.reconciliation.service.processor.request.ChangeClearingAccountStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 启用或停用清算账户（UC-R01，#4）
 * <li>锁 clearing-account:{id}；已是目标状态直接返回。停用后不再接收新的平台流水与渠道账单，也不参与解析。</li>
 */
@Component
@AllArgsConstructor
public class ChangeClearingAccountStatusProcessor
    extends AbstractLockProcessor<ChangeClearingAccountStatusRequest, ClearingAccountView> {
    private final ClearingAccountService clearingAccountService;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ClearingAccountMidRepository clearingAccountMidRepository;

    /** 清算账户资源锁。 */
    @Override
    protected LockCommand lockCommand(ChangeClearingAccountStatusRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.clearingAccount(request.getClearingAccountId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected ClearingAccountView processWithinLock(ChangeClearingAccountStatusRequest request) {
        ClearingAccount account = clearingAccountRepository.findById(request.getClearingAccountId());
        if (account == null) {
            throw new BizCheckFailException("清算账户不存在: " + request.getClearingAccountId());
        }
        if (account.getStatus() != request.getStatus()) {
            clearingAccountService.changeStatus(account, request.getStatus());
        }
        return new ClearingAccountView(account, clearingAccountMidRepository.listByClearingAccount(account.getId()));
    }
}
