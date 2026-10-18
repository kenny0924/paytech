package com.paytech.client.master.service.processor;

import java.time.Duration;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PeopleCommandReceiptResponseBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domain.onboarding.ClientPeopleOperation;
import com.paytech.client.master.domain.onboarding.ClientRelationValues;
import com.paytech.client.master.domainservice.service.ClientPeopleService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.CreateClientRelationRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

/**
 * C09 为已有人员新建关系。
 * <li>同时按申请与关系对象人员 cct:party:{partyId} 串行。</li>
 * <li>相同组织、人员、类型且内容相同返回原关系；内容不同或并发同一关系返回 RELATION_ALREADY_EXISTS。</li>
 */
public class CreateClientRelationProcessor
    extends AbstractLockProcessor<CreateClientRelationRequest, PeopleCommandReceiptResponseBody> {
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientPeopleService service;
    private final ClientDraftAccountClient accounts;
    private final TransactionTemplate writes;

    /** 锁等待与租约沿用 CCT 既有注解锁配置，保持同一互斥域。 */
    public CreateClientRelationProcessor(Duration lockWait, Duration lockLease, ClientPeopleService service,
        ClientDraftAccountClient accounts, TransactionTemplate writes) {
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.service = service;
        this.accounts = accounts;
        this.writes = writes;
    }

    /** 与原注解锁 client-onboarding:application 与 cct:party 生成同一组逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(CreateClientRelationRequest request) {
        return LockCommand.tryLocks(ClientOnboardingLocks.applicationAndParty(request.getApplicationId(),
            request.getPartyId()), lockWait, lockLease);
    }

    @Override
    protected PeopleCommandReceiptResponseBody processWithinLock(CreateClientRelationRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        var relation = ClientRelationValues.create(request.getRelation());
        var command = ClientDraftCommand.of(request.getAccountId(), ClientPeopleOperation.CREATE_RELATION.getCode());
        return ClientOnboardingCommands.relationWrite(writes,
            () -> service.createRelation(command, request.getApplicationId(), request.getPartyId(), relation));
    }
}
