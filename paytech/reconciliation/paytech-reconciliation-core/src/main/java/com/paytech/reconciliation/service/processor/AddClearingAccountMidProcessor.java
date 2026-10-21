package com.paytech.reconciliation.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import com.paytech.reconciliation.domain.ClearingAccountRules;
import com.paytech.reconciliation.domain.view.ClearingAccountView;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountMidRepository;
import com.paytech.reconciliation.domainservice.repository.ClearingAccountRepository;
import com.paytech.reconciliation.domainservice.service.ClearingAccountService;
import com.paytech.reconciliation.integration.ChannelMidClient;
import com.paytech.reconciliation.service.processor.request.ClearingAccountMidRequest;

import lombok.AllArgsConstructor;

/**
 * 向清算账户挂入 MID（UC-R01，#3）
 * <li>锁 clearing-account:{id}；币种取清算账户币种。同一 MID + 币种已在本账户直接返回当前结果；已归属其他清算账户为业务拒绝（先按业务键查询，
 * uk(mid_id, currency) 冲突只作并发兜底 409）。</li>
 * <li>本地事务前向 channel-connector 核对：MID 存在、ACTIVE、渠道与经营主体和清算账户一致、能力组含清算币种；对方不可用为 503。</li>
 */
@Component
@AllArgsConstructor
public class AddClearingAccountMidProcessor extends AbstractLockProcessor<ClearingAccountMidRequest, ClearingAccountView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelMidClient channelMidClient;
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
        // 查询原归属
        ClearingAccount account = loadActiveAccount(request.getClearingAccountId());
        ClearingAccountMid existing = clearingAccountMidRepository.findByMidAndCurrency(request.getMidId(),
            account.getCurrency());
        if (existing != null && existing.getClearingAccountId().equals(account.getId())) {
            return view(account);
        }
        if (existing != null) {
            throw new BizCheckFailException("MID + 币种已归属其他清算账户: MID " + request.getMidId() + ", 币种 "
                + account.getCurrency() + ", 已归属清算账户 " + existing.getClearingAccountId());
        }

        // 事务前核对 channel-connector
        ClearingAccountRules.checkMid(request.getMidId(), channelMidClient.findMid(request.getMidId()),
            account.getChannelCode(), account.getEntityNo(), account.getCurrency());

        // 本地保存
        clearingAccountService.addMid(account, buildMid(account, request.getMidId()));
        return view(account);
    }

    /** 读取并要求清算账户存在且 ACTIVE。 */
    ClearingAccount loadActiveAccount(Long clearingAccountId) {
        ClearingAccount account = clearingAccountRepository.findById(clearingAccountId);
        if (account == null || account.getStatus() != ClearingAccountStatus.ACTIVE) {
            throw new BizCheckFailException("清算账户不存在或已停用，不能挂入 MID: " + clearingAccountId);
        }
        return account;
    }

    /** 新归属行。 */
    ClearingAccountMid buildMid(ClearingAccount account, Long midId) {
        ClearingAccountMid mid = new ClearingAccountMid();
        mid.setId(idGenerator.nextId());
        mid.setClearingAccountId(account.getId());
        mid.setMidId(midId);
        mid.setCurrency(account.getCurrency());
        mid.setCreateTime(clock.instant().truncatedTo(ChronoUnit.MICROS));
        return mid;
    }

    /** 清算账户与全部 MID。 */
    ClearingAccountView view(ClearingAccount account) {
        return new ClearingAccountView(account, clearingAccountMidRepository.listByClearingAccount(account.getId()));
    }
}
