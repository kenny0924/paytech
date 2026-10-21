package com.paytech.reconciliation.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.domain.view.ClearingAccountView;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ClearingAccountService;
import com.paytech.reconciliation.service.processor.request.ClearingAccountMidRequest;

import lombok.AllArgsConstructor;

/**
 * 从清算账户摘除 MID（UC-R01，#3）
 * <li>锁 clearing-account:{id}；摘除即删除归属行，已生成的平台流水仍属原清算账户。MID 不在本账户为业务拒绝。</li>
 */
@Component
@AllArgsConstructor
public class RemoveClearingAccountMidProcessor
    extends AbstractLockProcessor<ClearingAccountMidRequest, ClearingAccountView> {
    private final ClearingAccountService clearingAccountService;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ClearingAccountMidRepository clearingAccountMidRepository;

    /** 清算账户资源锁。 */
    @Override
    protected LockCommand lockCommand(ClearingAccountMidRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.clearingAccount(request.getClearingAccountId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected ClearingAccountView processWithinLock(ClearingAccountMidRequest request) {
        // 业务验证
        ClearingAccount account = clearingAccountRepository.findById(request.getClearingAccountId());
        if (account == null) {
            throw new BizCheckFailException("清算账户不存在: " + request.getClearingAccountId());
        }
        ClearingAccountMid mid = clearingAccountMidRepository.findByMidAndCurrency(request.getMidId(),
            account.getCurrency());
        if (mid == null || !mid.getClearingAccountId().equals(account.getId())) {
            throw new BizCheckFailException("MID 不在本清算账户: MID " + request.getMidId() + ", 清算账户 "
                + account.getId());
        }

        // 本地保存
        clearingAccountService.removeMid(account, mid);
        return new ClearingAccountView(account, clearingAccountMidRepository.listByClearingAccount(account.getId()));
    }
}
