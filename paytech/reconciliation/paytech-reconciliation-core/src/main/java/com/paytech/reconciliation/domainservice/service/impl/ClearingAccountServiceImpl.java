package com.paytech.reconciliation.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.domain.ClearingAccountSettings;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ClearingAccountService;

import lombok.AllArgsConstructor;

/** 清算账户与 MID 归属的本地保存。 */
@Service
@AllArgsConstructor
public class ClearingAccountServiceImpl implements ClearingAccountService {
    private final TransactionTemplate transactionTemplate;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ClearingAccountMidRepository clearingAccountMidRepository;

    /** 插入清算账户。 */
    @Override
    public void create(ClearingAccount account) {
        transactionTemplate.executeWithoutResult(status -> clearingAccountRepository.insert(account));
    }

    /** 按版本替换配置。 */
    @Override
    public void updateSettings(ClearingAccount account, ClearingAccountSettings settings) {
        transactionTemplate.executeWithoutResult(status -> clearingAccountRepository.updateSettings(account,
            settings.getUnmatchedWaitDays(), settings.getCostToleranceAmount(), settings.getCashGraceDays(),
            settings.getSettlementCashAccountId()));
    }

    /** 插入归属行并推进版本。 */
    @Override
    public void addMid(ClearingAccount account, ClearingAccountMid mid) {
        transactionTemplate.executeWithoutResult(status -> {
            clearingAccountRepository.increaseVersion(account);
            clearingAccountMidRepository.insert(mid);
        });
    }

    /** 删除归属行并推进版本。 */
    @Override
    public void removeMid(ClearingAccount account, ClearingAccountMid mid) {
        transactionTemplate.executeWithoutResult(status -> {
            clearingAccountRepository.increaseVersion(account);
            clearingAccountMidRepository.delete(mid);
        });
    }

    /** 按版本与原状态启停。 */
    @Override
    public void changeStatus(ClearingAccount account, ClearingAccountStatus status) {
        transactionTemplate.executeWithoutResult(tx -> clearingAccountRepository.changeStatus(account, status));
    }
}
