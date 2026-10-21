package com.paytech.reconciliation.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.domain.ClearingAccountRules;
import com.paytech.reconciliation.domain.ClearingAccountSettings;
import com.paytech.reconciliation.domain.view.ClearingAccountView;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ClearingAccountService;
import com.paytech.reconciliation.service.processor.request.UpdateClearingAccountSettingsRequest;

import lombok.AllArgsConstructor;

/**
 * 修改清算账户三项配置与到账银行账户（UC-R01，#2），整体替换
 * <li>锁 clearing-account:{id}；按版本写入。到账银行账户校验同新建；已生成的预期银行流水不随之改变。</li>
 */
@Component
@AllArgsConstructor
public class UpdateClearingAccountSettingsProcessor
    extends AbstractLockProcessor<UpdateClearingAccountSettingsRequest, ClearingAccountView> {
    private final CashAccountRepository cashAccountRepository;
    private final ClearingAccountService clearingAccountService;
    private final ClearingAccountRepository clearingAccountRepository;
    private final ClearingAccountMidRepository clearingAccountMidRepository;

    /** 清算账户资源锁。 */
    @Override
    protected LockCommand lockCommand(UpdateClearingAccountSettingsRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.clearingAccount(request.getClearingAccountId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected ClearingAccountView processWithinLock(UpdateClearingAccountSettingsRequest request) {
        // 业务验证
        ClearingAccount account = clearingAccountRepository.findById(request.getClearingAccountId());
        if (account == null) {
            throw new BizCheckFailException("清算账户不存在: " + request.getClearingAccountId());
        }
        Money.of(account.getCurrency(), request.getCostToleranceAmount());
        if (request.getSettlementCashAccountId() != null) {
            ClearingAccountRules.checkSettlementCashAccount(request.getSettlementCashAccountId(),
                cashAccountRepository.findById(request.getSettlementCashAccountId()), account.getEntityNo(),
                account.getCurrency());
        }

        // 本地保存
        clearingAccountService.updateSettings(account, new ClearingAccountSettings(request.getSettlementCashAccountId(),
            request.getUnmatchedWaitDays(), request.getCostToleranceAmount(), request.getCashGraceDays()));
        return new ClearingAccountView(account, clearingAccountMidRepository.listByClearingAccount(account.getId()));
    }
}
