package com.paytech.reconciliation.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.service.CashAccountService;
import lombok.AllArgsConstructor;

/** 银行账户复核流转；状态与版本由 Repository 条件更新保护。 */
@Service
@AllArgsConstructor
public class CashAccountServiceImpl implements CashAccountService {
    private final TransactionTemplate transactionTemplate;
    private final CashAccountRepository cashAccountRepository;

    /** 插入新账户。 */
    @Override
    public void create(CashAccount account) {
        transactionTemplate.executeWithoutResult(status -> cashAccountRepository.insert(account));
    }

    /** 记关闭申请。 */
    @Override
    public void requestClosure(CashAccount account, Long makerId, String contentHash) {
        transactionTemplate.executeWithoutResult(status -> cashAccountRepository.updateReview(account,
            CashAccountStatus.ACTIVE, CashAccountPendingAction.CLOSE, contentHash, makerId, account.getCheckerId()));
    }

    /** 复核通过。 */
    @Override
    public void approve(CashAccount account, Long checkerId) {
        CashAccountStatus target = account.getPendingAction() == CashAccountPendingAction.CLOSE ? CashAccountStatus.CLOSED
            : CashAccountStatus.ACTIVE;
        transactionTemplate.executeWithoutResult(status -> cashAccountRepository.updateReview(account, target, null,
            account.getContentHash(), account.getMakerId(), checkerId));
    }

    /** 复核拒绝。 */
    @Override
    public void reject(CashAccount account, Long checkerId) {
        CashAccountStatus target = account.getPendingAction() == CashAccountPendingAction.CLOSE ? CashAccountStatus.ACTIVE
            : CashAccountStatus.CLOSED;
        transactionTemplate.executeWithoutResult(status -> cashAccountRepository.updateReview(account, target, null,
            account.getContentHash(), account.getMakerId(), checkerId));
    }
}
