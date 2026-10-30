package com.paytech.risk.control.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.domain.limittier.ClientTierChangeView;
import com.paytech.risk.control.domain.limittier.LimitTierValidationException;
import com.paytech.risk.control.domainservice.repository.ClientLimitTierChangeRepository;
import com.paytech.risk.control.service.ClientLimitTierService;
import com.paytech.risk.control.service.processor.request.DecideClientTierChangeRequest;

import lombok.AllArgsConstructor;

/**
 * 驳回档位变更申请（L8 普通、L9 高级）
 * <li>普通接口只能处理 STANDARD 级别；驳回人不能是申请人；驳回意见必填。档位与名单不变。</li>
 */
@Component
@AllArgsConstructor
public class RejectClientTierChangeProcessor
    extends AbstractLockProcessor<DecideClientTierChangeRequest, ClientTierChangeView> {
    private final Clock clock;
    private final ClientLimitTierService service;
    private final ClientLimitTierChangeRepository changes;

    /** 驳回须写意见；只依赖输入，锁前判断。 */
    @Override
    protected void validate(DecideClientTierChangeRequest request) {
        if (request.getComment() == null || request.getComment().isBlank()) {
            throw new LimitTierValidationException("comment", "驳回时须写审批意见");
        }
    }

    /** 按申请所属 Client 获取档位锁；申请不存在直接返回 CHANGE_NOT_FOUND。 */
    @Override
    protected LockCommand lockCommand(DecideClientTierChangeRequest request) {
        long clientId = LimitTierCommands.requireChange(changes, request.getChangeId()).getClientId();
        return LockCommand.tryLocks(List.of(LimitTierCommands.client(clientId)), LimitTierCommands.WAIT_TIME,
            LimitTierCommands.LEASE_TIME);
    }

    @Override
    protected ClientTierChangeView processWithinLock(DecideClientTierChangeRequest request) {
        // 业务验证
        ClientLimitTierChange change = LimitTierCommands.requireChange(changes, request.getChangeId());
        LimitTierCommands.requirePending(change, request.getExpectedVersion());
        LimitTierCommands.requireDecider(change, request);

        // 保存
        service.reject(change, request.getActorAccountId(), request.getComment(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        return ClientTierChangeView.of(change);
    }
}
