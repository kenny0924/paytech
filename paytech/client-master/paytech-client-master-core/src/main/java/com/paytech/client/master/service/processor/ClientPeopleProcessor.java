package com.paytech.client.master.service.processor;
import java.util.function.Supplier;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.*;
import com.paytech.client.master.domain.onboarding.*;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.repository.ClientPeopleRepository;
import com.paytech.client.master.domainservice.service.ClientPeopleService;
import com.paytech.client.master.integration.ClientAgreementPartyClient;
import com.paytech.client.master.core.dal.dataobject.ClientPersonIdentity;
import java.util.List;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;
import static com.paytech.client.master.domain.onboarding.ClientPeopleOperation.*;
/** Redis 多资源锁在事务外按稳定顺序取得；会话核对后才进入 CCT 本地事务。 */
public class ClientPeopleProcessor {
    private final ClientPeopleService service;
    private final ClientDraftAccountClient accounts;
    private final Validator validator;
    private final TransactionTemplate writes;
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final ClientAgreementPartyClient agreementParties;
    public ClientPeopleProcessor(ClientPeopleService service,ClientDraftAccountClient accounts,Validator validator,TransactionTemplate writes,
        ClientDraftRepository drafts,ClientPeopleRepository people,ClientAgreementPartyClient agreementParties) {
        this.service=service;this.accounts=accounts;this.validator=validator;this.writes=writes;
        this.drafts=drafts;this.people=people;this.agreementParties=agreementParties;
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public PeopleCommandReceiptResponseBody createPerson(long accountId,long sessionId,long applicationId,CreatePersonRequestBody request) {
        accounts.authorize(accountId,sessionId);if (request==null) throw new FailException(INVALID_DRAFT);
        var person=person(request.person());var relation=ClientRelationValues.create(request.relation());
        var command=command(accountId,CREATE_PERSON);
        return transaction(() -> service.createPerson(command,applicationId,person,relation));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    @ResourceLock(prefix="cct:party",key="#partyId")
    public PeopleCommandReceiptResponseBody savePerson(long accountId,long sessionId,long applicationId,long partyId,DraftPatchRequestBody request) {
        accounts.authorize(accountId,sessionId);var person=person(request);
        var command=command(accountId,SAVE_PERSON);
        var usage=agreementUsage(accountId,applicationId,partyId);
        return transaction(() -> service.savePerson(command,applicationId,partyId,person,usage));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    @ResourceLock(prefix="cct:party",key="#partyId")
    public PeopleCommandReceiptResponseBody createRelation(long accountId,long sessionId,long applicationId,long partyId,RelationFields request) {
        accounts.authorize(accountId,sessionId);var relation=ClientRelationValues.create(request);
        var command=command(accountId,CREATE_RELATION);
        return transaction(() -> service.createRelation(command,applicationId,partyId,relation));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public PeopleCommandReceiptResponseBody saveRelation(long accountId,long sessionId,long applicationId,long relationId,RelationPatchRequestBody request) {
        accounts.authorize(accountId,sessionId);var patch=ClientRelationValues.patch(request);
        var command=command(accountId,SAVE_RELATION);
        return transaction(() -> service.saveRelation(command,applicationId,relationId,patch));
    }
    @ResourceLock(prefix="client-onboarding:application",key="#applicationId")
    public void removeRelation(long accountId,long sessionId,long applicationId,long relationId) {
        accounts.authorize(accountId,sessionId);
        var command=command(accountId,REMOVE_RELATION);
        transaction(() -> { service.removeRelation(command,applicationId,relationId);return null; });
    }
    /**
     * 事务前查询目标人员是否已被合同引用
     * <li>只读本库人员身份后调用 commercial；申请或人员不存在时不调用，事务内照常报原错误。</li>
     */
    ClientAgreementPartyUsage agreementUsage(long accountId,long applicationId,long partyId) {
        var application=drafts.identity(applicationId,accountId,false);
        ClientPersonIdentity person=application==null?null:people.person(application,partyId,false);
        return agreementParties.usage(person==null?List.of():List.of(person));
    }
    private ClientDraftPatch person(DraftPatchRequestBody request) {
        var person=ClientDraftPatch.normalize(request,validator);person.requirePerson();return person;
    }
    private ClientDraftCommand command(long account,ClientPeopleOperation operation) {
        return ClientDraftCommand.of(account,operation.getCode());
    }
    /** 申请锁失效时的并发同一关系由唯一键回滚并明确冲突。 */
    private <T> T transaction(Supplier<T> action) {
        try { return writes.execute(tx -> action.get()); }
        catch (DuplicateKeyException collision) {
            String reason=collision.getMostSpecificCause().getMessage();
            if (reason!=null && reason.contains("uk_party_relation_identity")) throw new FailException(RELATION_ALREADY_EXISTS);
            throw collision;
        }
    }
}
