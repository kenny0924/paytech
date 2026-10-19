package com.paytech.client.master.service.processor;

import java.time.Duration;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PeopleCommandReceiptResponseBody;
import com.paytech.client.master.core.dal.dataobject.ClientPersonIdentity;
import com.paytech.client.master.domain.onboarding.ClientAgreementPartyUsage;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domain.onboarding.ClientPeopleOperation;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.repository.ClientPeopleRepository;
import com.paytech.client.master.domainservice.service.ClientPeopleService;
import com.paytech.client.master.integration.ClientAgreementPartyClient;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.SaveClientPersonRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import jakarta.validation.Validator;

/**
 * C08 保存关联人员资料。
 * <li>同时按申请与人员 cct:party:{partyId} 串行，两把锁去重排序后在同一等待预算内获取。</li>
 * <li>事务前查询目标人员是否已被 commercial 合同引用：只读本库人员身份后调用 commercial；</li>
 * <li>申请或人员不存在时不查询引用，事务内照常报原错误。</li>
 */
public class SaveClientPersonProcessor
    extends AbstractLockProcessor<SaveClientPersonRequest, PeopleCommandReceiptResponseBody> {
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientPeopleService service;
    private final ClientDraftAccountClient accounts;
    private final TransactionTemplate writes;
    private final Validator validator;
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final ClientAgreementPartyClient agreementParties;

    /** 锁等待与租约沿用 CCT 既有注解锁配置，保持同一互斥域。 */
    public SaveClientPersonProcessor(Duration lockWait, Duration lockLease, ClientPeopleService service,
        ClientDraftAccountClient accounts, TransactionTemplate writes, Validator validator,
        ClientDraftRepository drafts, ClientPeopleRepository people, ClientAgreementPartyClient agreementParties) {
        this.lockWait = lockWait;
        this.lockLease = lockLease;
        this.service = service;
        this.accounts = accounts;
        this.writes = writes;
        this.validator = validator;
        this.drafts = drafts;
        this.people = people;
        this.agreementParties = agreementParties;
    }

    /** 与原注解锁 client-onboarding:application 与 cct:party 生成同一组逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(SaveClientPersonRequest request) {
        return LockCommand.tryLocks(ClientOnboardingLocks.applicationAndParty(request.getApplicationId(),
            request.getPartyId()), lockWait, lockLease);
    }

    @Override
    protected PeopleCommandReceiptResponseBody processWithinLock(SaveClientPersonRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        var person = ClientOnboardingCommands.personPatch(request.getPatch(), validator);
        var command = ClientDraftCommand.of(request.getAccountId(), ClientPeopleOperation.SAVE_PERSON.getCode());
        var usage = agreementUsage(request.getAccountId(), request.getApplicationId(), request.getPartyId());
        return ClientOnboardingCommands.relationWrite(writes,
            () -> service.savePerson(command, request.getApplicationId(), request.getPartyId(), person, usage));
    }

    /** 事务前读取本库人员身份并查询 commercial 合同引用；申请或人员不存在时传入空集合。 */
    ClientAgreementPartyUsage agreementUsage(long accountId, long applicationId, long partyId) {
        var application = drafts.identity(applicationId, accountId, false);
        ClientPersonIdentity person = application == null ? null : people.person(application, partyId, false);
        return agreementParties.usage(person == null ? List.of() : List.of(person));
    }
}
