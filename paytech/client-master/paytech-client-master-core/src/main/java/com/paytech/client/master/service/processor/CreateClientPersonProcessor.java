package com.paytech.client.master.service.processor;

import java.time.Duration;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PeopleCommandReceiptResponseBody;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domain.onboarding.ClientPeopleOperation;
import com.paytech.client.master.domain.onboarding.ClientRelationValues;
import com.paytech.client.master.domainservice.service.ClientPeopleService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.CreateClientPersonRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import jakarta.validation.Validator;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.INVALID_DRAFT;

/**
 * C08 为组织申请新增关联人员及其草稿关系。
 * <li>按申请 client-onboarding:application:{applicationId} 串行；Platform 会话在事务外核验后才进入 CCT 本地事务。</li>
 * <li>申请锁失效时的并发同一关系由唯一键回滚并返回 RELATION_ALREADY_EXISTS。</li>
 */
public class CreateClientPersonProcessor
    extends AbstractLockProcessor<CreateClientPersonRequest, PeopleCommandReceiptResponseBody> {
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientPeopleService service;
    private final ClientDraftAccountClient accounts;
    private final TransactionTemplate writes;
    private final Validator validator;

    /** 锁等待与租约沿用 CCT 既有注解锁配置，保持同一互斥域。 */
    public CreateClientPersonProcessor(Duration lockWait, Duration lockLease, ClientPeopleService service,
        ClientDraftAccountClient accounts, TransactionTemplate writes, Validator validator) {
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.service = service;
        this.accounts = accounts;
        this.writes = writes;
        this.validator = validator;
    }

    /** 与原注解锁 client-onboarding:application 生成同一逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(CreateClientPersonRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.application(request.getApplicationId())), lockWait,
            lockLease);
    }

    @Override
    protected PeopleCommandReceiptResponseBody processWithinLock(CreateClientPersonRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        if (request.getBody() == null) {
            throw new FailException(INVALID_DRAFT);
        }
        var person = ClientOnboardingCommands.personPatch(request.getBody().person(), validator);
        var relation = ClientRelationValues.create(request.getBody().relation());
        var command = ClientDraftCommand.of(request.getAccountId(), ClientPeopleOperation.CREATE_PERSON.getCode());
        return ClientOnboardingCommands.relationWrite(writes,
            () -> service.createPerson(command, request.getApplicationId(), person, relation));
    }
}
