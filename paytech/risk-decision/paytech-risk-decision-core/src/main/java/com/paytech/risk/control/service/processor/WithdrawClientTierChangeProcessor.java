package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.domain.limittier.ClientTierChangeView;
import com.paytech.risk.control.domain.limittier.LimitTierException;
import com.paytech.risk.control.domain.limittier.LimitTierException.Reason;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierChangeRepository;
import com.paytech.risk.control.service.ClientLimitTierService;
import com.paytech.risk.control.service.processor.request.WithdrawClientTierChangeRequest;

import lombok.AllArgsConstructor;

/** 申请人撤回待审批的档位变更申请（L5）；只有申请人可以撤回。 */
@Component
@AllArgsConstructor
public class WithdrawClientTierChangeProcessor
    extends AbstractLockProcessor<WithdrawClientTierChangeRequest, ClientTierChangeView> {
    private final Clock clock;
    private final ClientLimitTierService service;
    private final ClientLimitTierChangeRepository changes;

    /** 按申请所属 Client 获取档位锁；申请不存在直接返回 CHANGE_NOT_FOUND。 */
    @Override
    protected LockCommand lockCommand(WithdrawClientTierChangeRequest request) {
        long clientId = LimitTierCommands.requireChange(changes, request.getChangeId()).getClientId();
        return LockCommand.tryLocks(List.of(LimitTierCommands.client(clientId)), LimitTierCommands.WAIT_TIME,
            LimitTierCommands.LEASE_TIME);
    }

    @Override
    protected ClientTierChangeView processWithinLock(WithdrawClientTierChangeRequest request) {
        // 业务验证
        ClientLimitTierChange change = LimitTierCommands.requireChange(changes, request.getChangeId());
        LimitTierCommands.requirePending(change, request.getExpectedVersion());
        if (!Objects.equals(change.getRequestedBy(), request.getActorAccountId())) {
            throw new LimitTierException(Reason.NOT_REQUESTER, "只有申请人可以撤回档位变更申请: " + change.getChangeId());
        }

        // 保存
        service.withdraw(change, request.getActorAccountId(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        return ClientTierChangeView.of(change);
    }
}
