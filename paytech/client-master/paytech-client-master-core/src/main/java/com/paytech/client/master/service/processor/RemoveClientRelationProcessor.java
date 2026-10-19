package com.paytech.client.master.service.processor;

import java.time.Duration;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domain.onboarding.ClientPeopleOperation;
import com.paytech.client.master.domainservice.service.ClientPeopleService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.RemoveClientRelationRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * C11 删除关系；本申请下已不存在时同样完成。
 * <li>按申请 client-onboarding:application:{applicationId} 串行；Platform 会话在事务外核验。</li>
 */
public class RemoveClientRelationProcessor extends AbstractLockProcessor<RemoveClientRelationRequest, Void> {
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientPeopleService service;
    private final ClientDraftAccountClient accounts;
    private final TransactionTemplate writes;

    /** 锁等待与租约沿用 CCT 既有注解锁配置，保持同一互斥域。 */
    public RemoveClientRelationProcessor(Duration lockWait, Duration lockLease, ClientPeopleService service,
        ClientDraftAccountClient accounts, TransactionTemplate writes) {
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.service = service;
        this.accounts = accounts;
        this.writes = writes;
    }

    /** 与原注解锁 client-onboarding:application 生成同一逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(RemoveClientRelationRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.application(request.getApplicationId())), lockWait,
            lockLease);
    }

    @Override
    protected Void processWithinLock(RemoveClientRelationRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        var command = ClientDraftCommand.of(request.getAccountId(), ClientPeopleOperation.REMOVE_RELATION.getCode());
        return ClientOnboardingCommands.relationWrite(writes, () -> {
            service.removeRelation(command, request.getApplicationId(), request.getRelationId());
            return null;
        });
    }
}
